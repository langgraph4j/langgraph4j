package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.agent.skill.SkillParser;
import org.bsc.langgraph4j.agent.skill.SkillSource;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;

public interface SkilledReactSubAgent extends ToolCallback, AgentEx.ToolBehaviour<Message, AgentExecutorEx.State> {

    record Input(
        @ToolParam(description = """
    all information extracted by conversation needed to perform the required task
    """) String context) {
    }

    class Builder extends ReactAgentBuilderEx<Builder, AgentExecutorEx.State> {

        private record AgentImpl(ToolCallback delegate,
                                 CompiledGraph<AgentExecutorEx.State> subGraph) implements SkilledReactSubAgent {

            private AgentImpl {
                requireNonNull(delegate, "delegate cannot be null!");
                requireNonNull(subGraph, "subGraph cannot be null!");
            }

            @Override
            public String name() {
                return getToolDefinition().name();
            }

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

        public SkilledReactSubAgent build(CompileConfig compileConfig, SkillSource skillSource ) throws Exception {

            final var parser = SkillParser.of(skillSource.content());

            final var frontMatter = parser.getFrontMatter();

            final var name =  frontMatter.getString("name")
                    .orElseThrow( () -> new IllegalStateException("'name' property not found!"));
            final var description =  frontMatter.getString("description")
                    .orElseThrow( () -> new IllegalStateException("'description' property not found!"));

            // select only allowed tools if defined
            frontMatter.getStringList("allowed-tools").ifPresent( allowedTools -> {
                     tools.removeIf( tool -> allowedTools.stream().
                             noneMatch(allowedTool ->
                                     allowedTool.equalsIgnoreCase(tool.getToolDefinition().name())));
            });

            final var agent = new AgentExecutorEx.Builder( this )
                    .defaultSystem( parser.getContent() )
                    .build()
                    .compile( ofNullable(compileConfig)
                            .orElseGet( () -> CompileConfig.builder().build() ));

            final var function =  FunctionToolCallback.<Input, String>builder(name, ( input, context ) -> {

                final var graphInput = GraphInput.args( Map.of( "messages", UserMessage.builder().text(input.context()).build() ));

                return agent.invoke(graphInput, RunnableConfig.builder().build())
                        .flatMap(MessagesState::lastMessage)
                        .map( Message::getText )
                        .orElseThrow(() -> new IllegalStateException("no output message found!"));
            })
            .description(description)
            .inputType(Input.class)
            .build();

            return new AgentImpl(function, agent);

        }

    }

    static Builder builder() {
        return new Builder();
    }
}
