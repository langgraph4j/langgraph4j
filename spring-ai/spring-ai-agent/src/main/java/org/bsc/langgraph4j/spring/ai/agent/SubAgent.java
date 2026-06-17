package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agent.AgentEx;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;

public interface SubAgent extends ToolCallback, AgentEx.ToolBehaviour<Message, AgentExecutorEx.State> {

    /**
     * Input passed to the generated tool callback.
     *
     * @param context all conversation context needed by the skill to perform the task
     */
    record Input(
            @ToolParam(description = """
            all information extracted by conversation needed to perform the required task
            """) String context) {}


    record SubAgentImpl(ToolCallback delegate,
                        CompiledGraph<AgentExecutorEx.State> subGraph) implements SubAgent {

        public SubAgentImpl {
            requireNonNull(delegate, "delegate cannot be null!");
            requireNonNull(subGraph, "subGraph cannot be null!");
        }

        @Override
        public String name() {
            return getToolDefinition().name();
        }

        /**
         * Registers the sub-agent as a node in the parent graph and adapts tool execution
         * requests to the sub-agent message format.
         * <p>
         * Before invoking the sub-graph, the pending tool call arguments are wrapped as a
         * {@link UserMessage}. After completion, the last sub-agent message is converted back
         * into a {@link ToolResponseMessage} associated with the original tool call id.
         *
         * @param graph the parent graph receiving this sub-agent node
         * @throws GraphStateException if graph node registration fails
         */
        @Override
        public void addToGraph(StateGraph<AgentExecutorEx.State> graph) throws GraphStateException {
            graph.addWrapCallNodeHook(name(), (nodeId, state, config, action) ->
                    state.toolExecutionRequests$getFirst()
                            .map(toolCall -> {
                                final var newState = Map.<String, Object>of("messages", new UserMessage(toolCall.arguments()));

                                return action.apply(graph.getStateFactory().apply(newState), config);
                            })
                            .orElseGet(() -> failedFuture(new IllegalArgumentException("no tool execution request found!")))

            );

            graph.addAfterCallNodeHook(name(), (nodeId, state, config, lastResult) -> {

                @SuppressWarnings("unchecked") final var messages = (List<Message>) lastResult.get("messages");
                final var lastMessage = lastOf(messages).orElseThrow(() -> new IllegalArgumentException("no last messages found!"));

                return state.toolExecutionRequests$getFirst().map(toolCall -> {

                            final var toolResponse = new ToolResponseMessage.ToolResponse(toolCall.id(), name(), lastMessage.getText());

                            final var toolResponseMessage = ToolResponseMessage.builder()
                                    .responses(List.of(toolResponse))
                                    .build();

                            final var newResult = Map.of(
                                    AgentExecutorEx.State.TOOL_EXECUTION_REQUESTS,
                                    state.toolExecutionRequests$removeFirst(),
                                    "messages", toolResponseMessage);

                            return completedFuture(newResult);
                        })
                        .orElseGet(() -> failedFuture(new IllegalArgumentException("no tool execution request found!")));
            });

            graph.addNode(name(), subGraph);
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return delegate.call(toolInput, toolContext);
        }
    }

}