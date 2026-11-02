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
package org.bsc.langgraph4j.spring.ai.agentexecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the solution described for issue
 * <a href="https://github.com/langgraph4j/langgraph4j/issues/412">#412</a>: interrupt
 * the graph <em>before</em> a long-running (MCP) tool call, execute the tool outside the
 * graph, then resume and inject the external result while <b>skipping</b> the in-graph
 * tool execution.
 *
 * <p>
 * The mechanism is {@link AgentEx.Builder#addEdgeHook(org.bsc.langgraph4j.hook.EdgeHook.WrapCall)}
 * registered on the approval edge: on resume it replaces the default APPROVED/REJECTED
 * handling with a {@link Command} that feeds the externally produced result back into
 * the conversation and routes to the action dispatcher, exactly like the built-in
 * rejection path does — but carrying the real tool output.
 *
 * <p>
 * A scripted {@link ChatModel} keeps the test deterministic and offline.
 */
class Issue412Test {

	static final String TOOL_NAME = "mcp_long_job";

	static final String EXTERNAL_RESULT = "external MCP job result: 42 widgets shipped";

	/**
	 * Stand-in for a tool that takes a long time (eg. a remote MCP job). The graph must
	 * never execute it in these tests.
	 */
	public static class LongRunningMcpTool {

		final AtomicInteger invocations = new AtomicInteger();

		@Tool(name = TOOL_NAME, description = "A stand-in for a long running MCP tool call")
		public String mcpLongJob(@ToolParam(description = "the query to run") String query) {
			invocations.incrementAndGet();
			return "in-graph execution (must be skipped)";
		}

	}

	/**
	 * Asks for the tool on the first round; once the conversation carries a tool
	 * response it answers with it.
	 */
	static class ScriptedChatModel implements ChatModel {

		@Override
		public ChatResponse call(Prompt prompt) {

			final var hasToolResponse = prompt.getInstructions()
				.stream()
				.anyMatch(message -> MessageType.TOOL == message.getMessageType());

			if (hasToolResponse) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("final answer"))));
			}

			final var toolCall = new AssistantMessage.ToolCall("call-1", "function", TOOL_NAME,
					"{\"query\":\"quarterly report\"}");

			return new ChatResponse(List
				.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build())));
		}

	}

	@Test
	void externalResultIsInjectedAndToolIsSkipped() throws Exception {

		final var saver = new MemorySaver();
		final var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

		final var tool = new LongRunningMcpTool();
		final var externalResult = new AtomicReference<String>();

		final var agent = AgentExecutorEx.builder()
			.chatModel(new ScriptedChatModel())
			.toolsFromObject(tool)
			.approvalOn(TOOL_NAME, (nodeId, state) -> InterruptionMetadata.builder(nodeId, state)
				.addMetadata("tool_name", TOOL_NAME)
				.addMetadata("job_id", "job-42")
				.build())
			// On resume: when the external job has completed, skip the in-graph tool call,
			// inject its result and let the agent continue from it.
			.addApprovalActionHook((sourceId, state, config, action) -> {

				final var result = externalResult.get();
				if (result == null) {
					return action.apply(state, config);
				}

				final var request = state.toolExecutionRequests$getFirst()
					.orElseThrow(() -> new IllegalStateException("no tool execution request found!"));

				final var response = new ToolResponseMessage.ToolResponse(request.id(), request.name(), result);

				return CompletableFuture.completedFuture(new Command(AgentEx.ACTION_DISPATCHER_NODE,
						Map.of(AgentExecutorEx.State.TOOL_EXECUTION_REQUESTS, state.toolExecutionRequests$removeFirst(),
								AgentExecutorEx.State.TOOL_EXECUTION_RESPONSES, response, AgentEx.APPROVAL_RESULT,
								AgentState.MARK_FOR_REMOVAL)));
			})
			.build(compileConfig);

		final var runnableConfig = RunnableConfig.builder().threadId("issue-412").build();

		// 1. the graph stops BEFORE running the tool
		final var interrupted = agent
			.stream(GraphInput.args(Map.of("messages", new UserMessage("run the long MCP job"))), runnableConfig)
			.toCompletableFuture()
			.thenApply(GraphResult::from)
			.join();

		assertTrue(interrupted.isInterruptionMetadata(), "the graph should be interrupted before the tool call");
		assertTrue(interrupted.asInterruptionMetadata().metadata("job_id").isPresent());
		assertEquals(0, tool.invocations.get(), "the tool must not run while interrupted");

		// 2. the tool is executed outside the graph
		externalResult.set(EXTERNAL_RESULT);

		// 3. the graph resumes, skipping the tool and consuming the external result
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
			.filter(ToolResponseMessage.class::isInstance)
			.map(ToolResponseMessage.class::cast)
			.flatMap(message -> message.getResponses().stream())
			.anyMatch(response -> response.responseData().contains(EXTERNAL_RESULT)),
				"the external result must be part of the conversation");

		assertTrue(state.lastMessage()
			.filter(AssistantMessage.class::isInstance)
			.map(AssistantMessage.class::cast)
			.map(AssistantMessage::getText)
			.map(text -> text.contains("final answer"))
			.orElse(false), "the agent should answer after consuming the external result");
	}

	@Test
	void withoutTheHookApprovedResumeExecutesTheTool() throws Exception {

		final var saver = new MemorySaver();
		final var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

		final var tool = new LongRunningMcpTool();

		final var agent = AgentExecutorEx.builder()
			.chatModel(new ScriptedChatModel())
			.toolsFromObject(tool)
			.approvalOn(TOOL_NAME, (nodeId, state) -> InterruptionMetadata.builder(nodeId, state).build())
			.build(compileConfig);

		final var runnableConfig = RunnableConfig.builder().threadId("issue-412-baseline").build();

		final var interrupted = agent
			.stream(GraphInput.args(Map.of("messages", new UserMessage("run the long MCP job"))), runnableConfig)
			.toCompletableFuture()
			.thenApply(GraphResult::from)
			.join();

		assertTrue(interrupted.isInterruptionMetadata());
		assertEquals(0, tool.invocations.get());

		// without a hook the built-in APPROVED path runs the tool inside the graph
		agent.stream(GraphInput.resume(Map.of(AgentEx.APPROVAL_RESULT, AgentEx.ApprovalState.APPROVED)),
				runnableConfig)
			.toCompletableFuture()
			.join();

		assertEquals(1, tool.invocations.get(), "the built-in APPROVED path executes the tool");
	}

}
