/*
* Copyright 2026 - 2026 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link AgentExecutorEx.Builder} forwards node and edge hooks to the
 * underlying agent graph, bringing the langchain4j executor to parity with the Spring AI
 * {@code ReactAgentBuilderEx}.
 *
 * <p>
 * The scenario is the one from <a href="https://github.com/langgraph4j/langgraph4j/issues/412">#412</a>:
 * the graph interrupts before a long running tool, the application executes that tool
 * outside the graph and the approval edge hook resumes the turn by injecting the external
 * result and skipping the in-graph call.
 */
class AgentExecutorExHooksTest {

	static final String TOOL_NAME = "mcp_long_job";

	static final String EXTERNAL_RESULT = "external MCP job result: 42 widgets shipped";

	/**
	 * Stand-in for a long running tool: the graph must never execute it here.
	 */
	public static class LongRunningMcpTool {

		final AtomicInteger invocations = new AtomicInteger();

		@Tool(name = TOOL_NAME, value = "A stand-in for a long running MCP tool call")
		public String mcpLongJob(@P("query") String query) {
			invocations.incrementAndGet();
			return "in-graph execution (must be skipped)";
		}

	}

	/**
	 * Asks for the tool on the first round; once a tool result is in the conversation it
	 * answers with it.
	 */
	static class ScriptedChatModel implements ChatModel {

		@Override
		public ChatResponse doChat(ChatRequest request) {

			final var hasToolResult = request.messages()
				.stream()
				.anyMatch(message -> ChatMessageType.TOOL_EXECUTION_RESULT == message.type());

			if (hasToolResult) {
				return ChatResponse.builder().aiMessage(new AiMessage("final answer")).build();
			}

			final var toolRequest = ToolExecutionRequest.builder()
				.id("call-1")
				.name(TOOL_NAME)
				.arguments("{\"query\":\"quarterly report\"}")
				.build();

			return ChatResponse.builder().aiMessage(new AiMessage(List.of(toolRequest))).build();
		}

	}

	@Test
	void approvalHookCanInjectAnExternalResultAndSkipTheTool() throws Exception {

		final var saver = new MemorySaver();
		final var tool = new LongRunningMcpTool();
		final var externalResult = new AtomicReference<String>();

		final var agent = AgentExecutorEx.builder()
			.chatModel(new ScriptedChatModel())
			.toolsFromObject(tool)
			.approvalOn(TOOL_NAME, (nodeId, state) -> InterruptionMetadata.builder(nodeId, state)
				.addMetadata("job_id", "job-42")
				.build())
			// the hook registered here only reaches the graph if the builder forwards it
			.addApprovalActionHook((sourceId, state, config, action) -> {

				final var result = externalResult.get();
				if (result == null) {
					return action.apply(state, config);
				}

				final var requests = state.toolExecutionRequests();
				final var request = requests.get(0);

				final var message = new ToolExecutionResultMessage(request.id(), request.name(), result);

				final var gotoNode = requests.size() > 1 ? AgentEx.ACTION_DISPATCHER_NODE
						: AgentEx.CALL_MODEL_NODE;

				return completedFuture(new Command(gotoNode, Map.of("messages", message,
						AgentExecutorEx.State.TOOL_EXECUTION_REQUESTS, state.toolExecutionRequests$removeFirst(),
						AgentEx.APPROVAL_RESULT, AgentState.MARK_FOR_REMOVAL)));
			})
			.build()
			.compile(CompileConfig.builder().checkpointSaver(saver).build());

		final var runnableConfig = RunnableConfig.builder().threadId("lc4j-412").build();

		final var interrupted = agent
			.stream(GraphInput.args(Map.of("messages", UserMessage.from("run the long MCP job"))), runnableConfig)
			.toCompletableFuture()
			.thenApply(GraphResult::from)
			.join();

		assertTrue(interrupted.isInterruptionMetadata(), "the graph should be interrupted before the tool call");
		assertTrue(interrupted.asInterruptionMetadata().metadata("job_id").isPresent());
		assertEquals(0, tool.invocations.get(), "the tool must not run while interrupted");

		// the application executes the tool outside the graph, then resumes
		externalResult.set(EXTERNAL_RESULT);

		final var resumed = agent
			.stream(GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.REJECTED)),
					runnableConfig)
			.toCompletableFuture()
			.thenApply(GraphResult::from)
			.join();

		assertEquals(0, tool.invocations.get(), "the in-graph tool call must be skipped");

		final AgentExecutorEx.State state = resumed.asState();

		assertTrue(state.messages()
			.stream()
			.filter(ToolExecutionResultMessage.class::isInstance)
			.map(ToolExecutionResultMessage.class::cast)
			.anyMatch(message -> message.text().contains(EXTERNAL_RESULT)),
				"the external result must be part of the conversation");

		assertTrue(state.lastMessage()
			.filter(AiMessage.class::isInstance)
			.map(AiMessage.class::cast)
			.map(AiMessage::text)
			.map(text -> text.contains("final answer"))
			.orElse(false), "the agent should answer after consuming the external result");
	}

}
