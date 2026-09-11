package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-turn conversation on the same checkpoint thread: the tool loop of a
 * later turn must run to completion instead of being cut short by the final
 * response of a previous turn (which was stored in the checkpoint under
 * {@code agent_response}).
 * <p>
 * Requires {@link CompileConfig.Builder#releaseThread(boolean)} to be
 * {@code false} so that checkpoints are actually restored across invocations.
 */
public class AgentExecutorMultiTurnCheckpointTest {


    private static ChatResponse toolCallResponse(String id, String message) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id(id)
                                .name("execTest")
                                .arguments("{\"message\": \"%s\"}".formatted(message))
                                .build()))
                        .build())
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
    }

    private static ChatResponse textResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .finishReason(FinishReason.STOP)
                .build();
    }


    /**
     * Every tool request left in the message history must have been answered by
     * a tool result: a dangling request would pollute the next LLM call.
     */
    private static void assertNoDanglingToolRequests(List<ChatMessage> messages) {
        final Set<String> requested = new HashSet<>();
        final Set<String> executed = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage aiMessage) {
                aiMessage.toolExecutionRequests().forEach(request -> requested.add(request.id()));
            } else if (message instanceof ToolExecutionResultMessage resultMessage) {
                executed.add(resultMessage.id());
            }
        }
        requested.removeAll(executed);
        assertTrue(requested.isEmpty(),
                "dangling tool requests without a tool result: " + requested);
    }

    @Test
    void secondTurnOnSameThreadRunsToolLoopToCompletion() throws Exception {

        final var model = ScriptedChatModel.builder()
                .addResponse(toolCallResponse("call-1", "turn-one"))
                .addResponse(textResponse("first answer"))
                .addResponse(toolCallResponse("call-2", "turn-two"))
                .addResponse(textResponse("second answer"))
                .build();

        final var saver = new MemorySaver();

        final StateGraph<AgentExecutor.State> workflow = AgentExecutor.builder()
                .chatModel(model)
                .toolsFromObject(new TestTools())
                .build();

        final CompiledGraph<AgentExecutor.State> graph = workflow.compile(
                CompileConfig.builder().checkpointSaver(saver).releaseThread(false).build());

        final var config = RunnableConfig.builder().threadId("user-1").build();

        // FIRST TURN: tool call -> tool result -> final answer
        final Optional<AgentExecutor.State> firstTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("first question"))), config);

        assertTrue(firstTurn.isPresent());
        assertEquals("first answer", firstTurn.get().finalResponse().orElse(null));
        assertEquals(2, model.calls.get());

        // SECOND TURN on the same thread: the model asks for a tool again,
        // the loop must go back to the model and produce the final answer
        final Optional<AgentExecutor.State> secondTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("second question"))), config);

        assertTrue(secondTurn.isPresent());
        assertEquals(4, model.calls.get(),
                "the model must be called again after the second turn's tool execution");
        assertEquals("second answer", secondTurn.get().finalResponse().orElse(null));

        final var messages = secondTurn.get().messages();
        final var lastMessage = messages.get(messages.size() - 1);
        assertTrue(lastMessage instanceof AiMessage aiMessage && "second answer".equals(aiMessage.text()),
                "the last message must be the second turn's final answer, got: " + lastMessage);
        assertNoDanglingToolRequests(messages);
    }

    @Test
    void exSecondTurnOnSameThreadRunsToolLoopToCompletion() throws Exception {

        final var model = ScriptedChatModel.builder()
                .addResponse(toolCallResponse("call-1", "turn-one"))
                .addResponse(textResponse("first answer"))
                .addResponse(toolCallResponse("call-2", "turn-two"))
                .addResponse(textResponse("second answer"))
                .build();

        final var saver = new MemorySaver();

        final StateGraph<AgentExecutorEx.State> workflow = AgentExecutorEx.builder()
                .chatModel(model)
                .toolsFromObject(new TestTools())
                .build();

        final CompiledGraph<AgentExecutorEx.State> graph = workflow.compile(
                CompileConfig.builder().checkpointSaver(saver).releaseThread(false).build());

        final var config = RunnableConfig.builder().threadId("user-2").build();

        // FIRST TURN: tool call -> tool result -> final answer
        final Optional<AgentExecutorEx.State> firstTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("first question"))), config);

        assertTrue(firstTurn.isPresent());
        assertEquals("first answer", firstTurn.get().finalResponse().orElse(null));
        assertEquals(2, model.calls.get());

        // SECOND TURN on the same thread: the tool must actually run before the
        // model produces the final answer
        final Optional<AgentExecutorEx.State> secondTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("second question"))), config);

        assertTrue(secondTurn.isPresent());
        assertEquals(4, model.calls.get(),
                "the model must be called again after the second turn's tool execution");
        assertEquals("second answer", secondTurn.get().finalResponse().orElse(null));

        final var messages = secondTurn.get().messages();
        assertNoDanglingToolRequests(messages);

        final var lastMessage = messages.get(messages.size() - 1);
        assertTrue(lastMessage instanceof AiMessage aiMessage && "second answer".equals(aiMessage.text()),
                "the last message must be the second turn's final answer, got: " + lastMessage);
    }

    @Test
    void directAnswerTurnOverwritesPreviousFinalResponseAndNextToolTurnStillWorks() throws Exception {

        final var model = ScriptedChatModel.builder()
                .addResponse(toolCallResponse("call-1", "turn-one"))
                .addResponse(textResponse("first answer"))
                .addResponse(textResponse("second direct answer"))
                .addResponse(toolCallResponse("call-2", "turn-three"))
                .addResponse(textResponse("third answer"))
                .build();

        final var saver = new MemorySaver();

        final StateGraph<AgentExecutor.State> workflow = AgentExecutor.builder()
                .chatModel(model)
                .toolsFromObject(new TestTools())
                .build();

        final CompiledGraph<AgentExecutor.State> graph = workflow.compile(
                CompileConfig.builder().checkpointSaver(saver).releaseThread(false).build());

        final var config = RunnableConfig.builder().threadId("user-3").build();

        final Optional<AgentExecutor.State> firstTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("first question"))), config);
        assertTrue(firstTurn.isPresent());
        assertEquals("first answer", firstTurn.get().finalResponse().orElse(null));
        assertEquals(2, model.calls.get());

        // SECOND TURN: answered directly (no tool), must overwrite the stale response
        final Optional<AgentExecutor.State> secondTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("second question"))), config);
        assertTrue(secondTurn.isPresent());
        assertEquals("second direct answer", secondTurn.get().finalResponse().orElse(null));
        assertEquals(3, model.calls.get());

        // THIRD TURN: asks for a tool again, the loop must run to completion
        final Optional<AgentExecutor.State> thirdTurn =
                graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("third question"))), config);
        assertTrue(thirdTurn.isPresent());
        assertEquals(5, model.calls.get(),
                "the model must be called again after the third turn's tool execution");
        assertEquals("third answer", thirdTurn.get().finalResponse().orElse(null));
        assertNoDanglingToolRequests(thirdTurn.get().messages());
    }
}
