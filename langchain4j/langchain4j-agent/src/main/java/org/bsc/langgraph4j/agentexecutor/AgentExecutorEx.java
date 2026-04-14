package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;
import java.util.function.BiFunction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_RESET;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 * This implementation make in evidence the tools execution using and action dispatcher node
 * <pre>
 *              ┌─────┐
 *              │start│
 *              └─────┘
 *                 |
 *              ┌─────┐
 *              │model│
 *              └─────┘
 *                |
 *          ┌─────────────────┐
 *          │action_dispatcher│
 *          └─────────────────┘_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _
 *          |                 \              \                    \
 *       ┌────┐         ┌─────────────┐ ┌─────────────┐      ┌─────────────┐
 *       │stop│         │ tool_name 1 │ │ tool_name 2 │......│ tool_name N │
 *       └────┘         └─────────────┘ └─────────────┘      └─────────────┘
 * </pre>
 */
public interface AgentExecutorEx extends LG4JLoggable {

    /**
     * Represents the state of an agent.
     */
    class State extends MessagesState<ChatMessage> {

        public static final String TOOL_EXECUTION_REQUESTS = "tool_execution_requests";
        public static final String NEXT_ACTION = "next_action";
        public static final String FINAL_RESPONSE = "agent_response";

        static final Map<String, Channel<?>> SCHEMA = mergeMap(
                MessagesState.SCHEMA,
                Map.of(
                        TOOL_EXECUTION_REQUESTS, Channels.base( LinkedList::new ),
                        AgentEx.APPROVAL_RESULT, Channels.base( ( prevValue, newValue ) -> {
                            if( newValue instanceof AgentEx.ApprovalState approval ) {
                                return approval.name();
                            }
                            return newValue;
                        }))
                );



        public State(Map<String, Object> initData) {
            super(initData);
        }

        public List<ToolExecutionRequest> toolExecutionRequests() {
            return this.<List<ToolExecutionRequest>>value(TOOL_EXECUTION_REQUESTS)
                    .orElseThrow();
        }

        public List<ToolExecutionRequest> toolExecutionRequests$removeFirst() {
            return toolExecutionRequests().stream().skip(1).toList();
        }

        private Optional<List<ToolExecutionRequest>> loadToolExecutionRequestsFromLastMessage() {

            return lastMessage()
                    .filter(m -> ChatMessageType.AI == m.type())
                    .map(AiMessage.class::cast)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .map(AiMessage::toolExecutionRequests);
        }

        public Optional<String> nextAction() {
            return value(NEXT_ACTION);
        }

        /**
         * Retrieves the agent final response.
         *
         * @return an Optional containing the agent final response if present
         */
        public Optional<String> finalResponse() {
            return value(FINAL_RESPONSE);
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

    private static ToolExecutionResultMessage createRejectToolResponseMessage(ToolExecutionRequest toolRequest  )
    {
        final var text = "tool '%s' execution has been DENIED!".formatted(toolRequest.name());

        return ToolExecutionResultMessage.builder()
                .id( toolRequest.id() )
                .toolName(toolRequest.name() )
                .text( text )
                .build();

    }

    static AsyncNodeActionWithConfig<State> executeTool( LC4jToolService toolService, String actionName ) {

        return ( state, config ) -> {
            log.trace( "ExecuteTool" );

            final var currentToolExecutionRequests = state.toolExecutionRequests();

            if( currentToolExecutionRequests.isEmpty()) {
                return failedFuture( new IllegalArgumentException("no tool execution request found!") );
            }

            final var currentToolExecutionRequest = currentToolExecutionRequests.get(0);

            final var context = InvocationContext.builder()
                    .invocationParameters( InvocationParameters.from(state.data()))
                    .build();

            return toolService.execute( List.of(currentToolExecutionRequest), context, "messages")
                    .thenApply( command ->
                            mergeMap( command.update(),
                                    Map.of(State.TOOL_EXECUTION_REQUESTS,
                                            state.toolExecutionRequests$removeFirst() ),
                                    (v1,v2) -> v2 ));

        };
    }

    private static AsyncNodeActionWithConfig<State> dispatchTools(Set<String> approvals ) {
        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            log.trace("DispatchTools");

            final var previousToolExecutionRequests = state.toolExecutionRequests();
            if (!previousToolExecutionRequests.isEmpty()) {

                final var currentToolExecutionRequest = previousToolExecutionRequests.get(0);

                final var nextAction = approvals.contains(currentToolExecutionRequest.name()) ?
                        "approval_%s".formatted(currentToolExecutionRequest.name()) :
                        currentToolExecutionRequest.name();

                return Map.of(State.NEXT_ACTION, nextAction,
                        State.TOOL_EXECUTION_REQUESTS, previousToolExecutionRequests);
            }

            return state.loadToolExecutionRequestsFromLastMessage().map( newToolExecutionRequests -> {

                final var currentToolExecutionRequest = newToolExecutionRequests.get(0);

                final var nextAction = approvals.contains(currentToolExecutionRequest.name()) ?
                        "approval_%s".formatted(currentToolExecutionRequest.name()) :
                        currentToolExecutionRequest.name();

                return Map.of(State.NEXT_ACTION, nextAction,
                        State.TOOL_EXECUTION_REQUESTS, newToolExecutionRequests);

            }).orElseGet( () -> Map.of(State.FINAL_RESPONSE, "no tool execution request found!",
                    State.NEXT_ACTION, MARK_FOR_REMOVAL,
                    State.TOOL_EXECUTION_REQUESTS, MARK_FOR_RESET));

        });
    }

    private static AsyncCommandAction<State> approvalAction() {
        return (state, config) -> {

            final var approvalResultOptional = state.<String>value( AgentEx.APPROVAL_RESULT );

            if( approvalResultOptional.isEmpty() ) {
                return failedFuture( new IllegalStateException( "resume property '%s' not found!".formatted(AgentEx.APPROVAL_RESULT) ));
            }

            final var resumeState = approvalResultOptional.get();

            if( Objects.equals( resumeState, AgentEx.ApprovalState.APPROVED.name() )) {
                // APPROVED
                return completedFuture( new Command( resumeState,
                        Map.of(AgentEx.APPROVAL_RESULT, MARK_FOR_REMOVAL)));

            }
            else {
                // DENIED

                final var currentToolExecutionRequests = state.toolExecutionRequests();

                if(currentToolExecutionRequests.isEmpty())  {
                    return failedFuture( new IllegalStateException("no tool execution request found!") );
                }

                final var toolResponseMessage = createRejectToolResponseMessage( currentToolExecutionRequests.get(0));

                final var gotoNode = ( currentToolExecutionRequests.size() > 1 ) ?
                        AgentEx.ACTION_DISPATCHER_NODE :
                        AgentEx.CALL_MODEL_NODE ;

                return completedFuture( new Command( gotoNode,
                        Map.of( "messages",toolResponseMessage,
                                State.TOOL_EXECUTION_REQUESTS, state.toolExecutionRequests$removeFirst(),
                                AgentEx.APPROVAL_RESULT, MARK_FOR_REMOVAL)));

            }

        };
    }

    private static AsyncCommandAction<AgentExecutorEx.State> shouldContinue() {
        return AsyncCommandAction.command_async( (state, config ) ->
                state.finalResponse()
                        .map(res -> new Command(AgentEx.END_LABEL))
                        .orElse(new Command(AgentEx.CONTINUE_LABEL)) );
    }

    private static AsyncCommandAction<State> dispatchAction() {
        return AsyncCommandAction.command_async( (state, config ) ->
                state.nextAction()
                        .map( Command::new )
                        .orElseGet( () -> new Command(AgentEx.CALL_MODEL_NODE ) ));

    }

    /**
     * Builder class for constructing a graph of agent execution.
     */
    class Builder extends AgentExecutorBuilder<State,Builder>  {

        private final Map<String,AgentEx.ApprovalNodeAction<ChatMessage,State>> approvals = new LinkedHashMap<>();

        public Builder approvalOn( String actionId, BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider  ) {
            var action = AgentEx.ApprovalNodeAction.<ChatMessage,AgentExecutorEx.State>builder()
                    .interruptionMetadataProvider( interruptionMetadataProvider )
                    .build();

            approvals.put( actionId, action  );
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

            if (stateSerializer == null) {
                stateSerializer = Serializers.STD.object();
            }

            var tools = toolMap();

            final LC4jToolService toolService = new LC4jToolService(tools);

            return AgentEx.<ChatMessage, State, ToolSpecification>builder()
                    .stateSerializer( stateSerializer )
                    .schema( State.SCHEMA )
                    .toolName(ToolSpecification::name)
                    .callModelAction( new CallModel<>(this) )
                    .dispatchToolsAction( dispatchTools( approvals.keySet() ) )
                    .executeToolFactory( ( toolName ) -> executeTool( toolService, toolName ) )
                    .shouldContinueEdge( shouldContinue() )
                    .approvalActionEdge( approvalAction() )
                    .dispatchActionEdge( dispatchAction() )
                    .build( tools.keySet(), approvals )
                    ;
        }
    }

    /**
     *
     * @return a new Builder
     */
    static Builder builder() {
        return new Builder();
    }

}
