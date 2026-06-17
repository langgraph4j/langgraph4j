#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

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

/**
 * A {@link ToolCallback} backed by a React-style sub-agent whose tool metadata and system
 * prompt are loaded from a skill source.
 * <p>
 * The skill front matter defines the exposed tool name, description, and optional allowed
 * tool list, while the Markdown body becomes the sub-agent system prompt.
 */
public interface SkilledReactSubAgent extends ToolCallback, AgentEx.ToolBehaviour<Message, AgentExecutorEx.State> {

    /**
     * Input passed to the generated tool callback.
     *
     * @param context all conversation context needed by the skill to perform the task
     */
    record Input(
        @ToolParam(description = """
    all information extracted by conversation needed to perform the required task
    """) String context) {
    }

    /**
     * Builder for creating a {@link SkilledReactSubAgent} from a skill definition and the
     * underlying React agent configuration.
     */
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
                        state.toolExecutionRequests${symbol_dollar}getFirst()
                                .map(toolCall -> {
                                    final var newState = Map.<String, Object>of("messages", new UserMessage(toolCall.arguments()));

                                    return action.apply(graph.getStateFactory().apply(newState), config);
                                })
                                .orElseGet(() -> failedFuture(new IllegalArgumentException("no tool execution request found!")))

                );

                graph.addAfterCallNodeHook(name(), (nodeId, state, config, lastResult) -> {

                    @SuppressWarnings("unchecked") final var messages = (List<Message>) lastResult.get("messages");
                    final var lastMessage = lastOf(messages).orElseThrow(() -> new IllegalArgumentException("no last messages found!"));

                    return state.toolExecutionRequests${symbol_dollar}getFirst().map(toolCall -> {

                                final var toolResponse = new ToolResponseMessage.ToolResponse(toolCall.id(), name(), lastMessage.getText());

                                final var toolResponseMessage = ToolResponseMessage.builder()
                                        .responses(List.of(toolResponse))
                                        .build();

                                final var newResult = Map.of(
                                        AgentExecutorEx.State.TOOL_EXECUTION_REQUESTS,
                                        state.toolExecutionRequests${symbol_dollar}removeFirst(),
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

        /**
         * Builds a skill-backed React sub-agent from the provided skill source.
         * <p>
         * The skill front matter must define at least the {@code name} and
         * {@code description} properties. When {@code allowed-tools} is present, the current
         * tool set is filtered before compiling the sub-agent.
         *
         * @param compileConfig the compile configuration to use, or {@code null} to use defaults
         * @param skillSource the source used to load the skill markdown definition
         * @return a configured {@link SkilledReactSubAgent}
         * @throws Exception if the skill cannot be loaded or the agent cannot be built
         */
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

    /**
     * Creates a new builder for configuring a skill-backed React sub-agent.
     *
     * @return a new {@link Builder} instance
     */
    static Builder builder() {
        return new Builder();
    }
}
