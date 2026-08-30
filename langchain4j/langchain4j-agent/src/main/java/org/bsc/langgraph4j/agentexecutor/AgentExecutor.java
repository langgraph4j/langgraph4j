package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutor;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 */
public interface AgentExecutor {
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentExecutor.class);

    /**
     * Represents the state of an agent.
     */
    class State extends MessagesState<ChatMessage> {

        public static final String FINAL_RESPONSE = "agent_response";
        public static final String ACTIVE_SKILLS = "active_skills";

        /**
         * Extends {@link MessagesState#SCHEMA} with a replacing {@code active_skills} channel
         * (skill activation ids only — bodies stay out of state).
         */
        public static final Map<String, Channel<?>> SCHEMA;
        static {
            var schema = new java.util.HashMap<String, Channel<?>>(MessagesState.SCHEMA);
            schema.put(ACTIVE_SKILLS, Channels.base(ArrayList::new));
            SCHEMA = Map.copyOf(schema);
        }

        /**
         * Constructs a new State with the given initialization data.
         *
         * @param initData the initialization data
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        /**
         * Retrieves the agent final response.
         *
         * @return an Optional containing the agent final response if present
         */
        public Optional<String> finalResponse() {
            return value(FINAL_RESPONSE);
        }

        /**
         * Currently activated skill ids (empty if none).
         */
        @SuppressWarnings("unchecked")
        public List<String> activeSkills() {
            return this.<List<String>>value(ACTIVE_SKILLS).orElseGet(List::of);
        }

    }

    /**
     * Enum representing different serializers for the agent state.
     */
    enum Serializers {

        STD(new LC4jStateSerializer<>(State::new) ),
        JSON(new LC4jJacksonStateSerializer<>(State::new));

        private final StateSerializer<State> serializer;

        /**
         * Constructs a new Serializers enum with the specified serializer.
         *
         * @param serializer the state serializer
         */
        Serializers(StateSerializer<State> serializer) {
            this.serializer = serializer;
        }

        /**
         * Retrieves the state serializer.
         *
         * @return the state serializer
         */
        public StateSerializer<State> object() {
            return serializer;
        }
    }


    static AsyncCommandAction<AgentExecutor.State> executeTool( LC4jToolService toolService ) {

        return (state, config) -> {
            log.trace("executeTools");

            final var toolExecutionRequests = state.lastMessage()
                        .filter(m -> ChatMessageType.AI == m.type())
                        .map(m -> (AiMessage) m)
                        .filter(AiMessage::hasToolExecutionRequests)
                        .map(AiMessage::toolExecutionRequests);

            if (toolExecutionRequests.isEmpty() ) {
                final Command command = state.finalResponse()
                        .map( response -> new Command(Agent.END_LABEL) )
                        .orElseGet( () -> new Command(Agent.END_LABEL,
                                Map.of("agent_response", "no tool execution request found!")) );

                return completedFuture(command);
            }

            final var context = InvocationContext.builder()
                                .invocationParameters( InvocationParameters.from(state.data()))
                                .build();

            return toolService.execute( toolExecutionRequests.get(), context, "messages")
                    .thenApply( command ->
                        state.finalResponse()
                                .map(res -> new Command(Agent.END_LABEL, command.update()) )
                                .orElseGet( () -> new Command(Agent.AGENT_LABEL, command.update()) ));
        };
    }

    /**
     * Builder class for constructing a graph of agent execution.
     */
    class Builder extends AgentExecutorBuilder<State,Builder> {

        private final Agent.Builder<ChatMessage, State> agentBuilder = Agent.builder();

        /**
         * Sets the tool specification for the graph builder.
         *
         * @param objectsWithTools the tool specification
         * @return the updated GraphBuilder instance
         */
        @Deprecated
        public Builder toolSpecification(Object objectsWithTools) {
            super.toolsFromObject( objectsWithTools );
            return this;
        }

        @Deprecated
        public Builder toolSpecification(ToolSpecification spec, ToolExecutor executor) {
            super.tool(spec, executor);
            return this;
        }

        /**
         * Sets the tool specification for the graph builder.
         *
         * @param toolSpecification the tool specifications
         * @return the updated GraphBuilder instance
         */
        @Deprecated
        public Builder toolSpecification(LC4jToolService.Specification toolSpecification) {
            super.tool(toolSpecification.value(), toolSpecification.executor());
            return this;
        }


        /**
         * Sets the state serializer for the graph builder.
         *
         * @param stateSerializer the state serializer
         * @return the updated GraphBuilder instance
         */
        public Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Registers a wrap hook around the {@code agent} (call-model) node.
         * Aligns with {@link Agent.Builder#addCallModelHook} / Spring {@code ReactAgent}.
         */
        public Builder addCallModelHook(NodeHook.WrapCall<State> wrapCall) {
            agentBuilder.addCallModelHook(wrapCall);
            return this;
        }

        /**
         * Registers a wrap hook around the {@code action} (execute-tools) conditional edge.
         * Aligns with {@link Agent.Builder#addExecuteToolsHook} / Spring {@code ReactAgent}.
         */
        public Builder addExecuteToolsHook(EdgeHook.WrapCall<State> wrapCall) {
            agentBuilder.addExecuteToolsHook(wrapCall);
            return this;
        }

        /**
         * Wires {@link SkillInjector}: execute-tools activation hook +
         * (optionally) callModel unload hook + conversation policy
         * (composed with any previously set policy).
         */
        public Builder skillInjector(SkillInjector injector) {
            addExecuteToolsHook(injector.executeToolsHook());
            if (injector.hasUnloadAfterCallModel()) {
                addCallModelHook(injector.callModelUnloadHook());
            }
            conversationContextPolicy(injector.asConversationContextPolicy(conversationContextPolicy));
            return this;
        }

        /**
         * Builds the state graph.
         *
         * @return the constructed StateGraph
         * @throws GraphStateException if there is an error in the graph state
         */
        public StateGraph<State> build() throws GraphStateException {

            if (streamingChatModel != null && chatModel != null) {
                throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
            }
            if (streamingChatModel == null && chatModel == null) {
                throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
            }

            final LC4jToolService toolService = new LC4jToolService(toolMap());

            return agentBuilder
                    .stateSerializer( ofNullable(stateSerializer).orElseGet(Serializers.JSON::object) )
                    .schema( State.SCHEMA )
                    .callModelAction( new CallModel<>( this ) )
                    .executeToolsAction( executeTool( toolService ) )
                    .build();

        }
    }

    /**
     * Creates a new Builder instance.
     *
     * @return a new Builder
     */
    static Builder builder() {
        return new Builder();
    }

}
