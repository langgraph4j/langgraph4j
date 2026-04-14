package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agent.CallModelAction;
import org.bsc.langgraph4j.spring.ai.agent.ReactAgentBuilder;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
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
     * Represents the state of an agent in a system.
     * This class extends {@link AgentState} and defines constants for keys related to input, agent outcome,
     * and intermediate steps. It includes a static map schema that specifies how these keys should be handled.
     */
    class State extends MessagesState<Message> {

        public static final String TOOL_EXECUTION_REQUESTS = "tool_execution_requests";
        public static final String NEXT_ACTION = "next_action";

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

        /**
         * Constructs a new State with the given initialization data.
         *
         * @param initData the initialization data
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        public List<AssistantMessage.ToolCall> toolExecutionRequests() {
            return this.<List<AssistantMessage.ToolCall>>value(TOOL_EXECUTION_REQUESTS)
                    .orElseThrow();
        }

        private List<AssistantMessage.ToolCall> toolExecutionRequests$removeFirst() {
            return toolExecutionRequests().stream().skip(1).toList();
        }

        private Optional<List<AssistantMessage.ToolCall>> loadToolExecutionRequestsFromLastMessage() {

            return lastMessage()
                    .filter(m -> MessageType.ASSISTANT == m.getMessageType())
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls);
        }

        public Optional<String> nextAction() {
            return value(NEXT_ACTION);
        }
    }

    /**
     * Class responsible for building a state graph.
     */
    class Builder extends ReactAgentBuilder<Builder, State> {
        protected AgentEx.Builder<Message,State, ToolCallback> agentBuilder = AgentEx.builder();

        public Builder addCallModelHook(NodeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addCallModelHook(wrapCall);
            return this;
        }

        public Builder addDispatchToolsHook(NodeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addDispatchToolsHook(wrapCall);
            return this;
        }

        public Builder addApprovalActionHook( EdgeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addApprovalActionHook( wrapCall );
            return this;
        }

        public Builder addDispatchActionHook( EdgeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addDispatchActionHook( wrapCall );
            return this;
        }

        public Builder addShouldContinueHook( EdgeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addShouldContinueHook( wrapCall );
            return this;
        }

        private final Map<String,AgentEx.ApprovalNodeAction<Message,State>> approvals = new LinkedHashMap<>();

        public Builder approvalOn( String actionId, BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider  ) {
            var action = AgentEx.ApprovalNodeAction.<Message,AgentExecutorEx.State>builder()
                    .interruptionMetadataProvider( interruptionMetadataProvider )
                    .build();

            approvals.put( actionId, action  );
            return this;
        }

        /**
         * Builds and returns a StateGraph with the specified configuration.
         * Initializes the stateSerializer if it's null. Then, constructs a new StateGraph object using the provided schema
         * and serializer, adds an initial edge from the START node to "agent", and then proceeds to add nodes for "agent" and
         * "action". It also sets up conditional edges from the "agent" node based on whether or not to continue.
         *
         * @return A configured StateGraph object.
         * @throws GraphStateException If there is an issue with building the graph state.
         */
        public StateGraph<State> build(Function<ReactAgentBuilder<?, ?>, org.bsc.langgraph4j.spring.ai.agent.ReactAgent.ChatService> chatServiceFactory ) throws GraphStateException {

            // verify approval
            final var toolService = new SpringAIToolService(tools());

            final var callModelAction = new CallModelAction<State>( chatServiceFactory, this );

            return agentBuilder
                    .stateSerializer( ofNullable(stateSerializer)
                            .orElseGet( () -> new SpringAIJacksonStateSerializer<>(AgentExecutorEx.State::new) ) )
                    .schema( State.SCHEMA )
                    .toolName( tool -> tool.getToolDefinition().name() )
                    .callModelAction( callModelAction )
                    .dispatchToolsAction( dispatchTools( approvals.keySet() ) )
                    .executeToolFactory( ( toolName ) -> executeTool( toolService, toolName ) )
                    .shouldContinueEdge( shouldContinue() )
                    .approvalActionEdge( approvalAction() )
                    .dispatchActionEdge( dispatchAction() )
                    .build( tools, approvals )
                    ;
        }

    }

    /**
     * Returns a new instance of {@link Builder}.
     *
     * @return a new {@link Builder} object
     */
    static Builder builder() {
        return new Builder();
    }

    private static ToolResponseMessage createRejectToolResponseMessage(AssistantMessage.ToolCall toolExecutionRequest )
    {

        final var responseData = "tool '%s'  execution has been DENIED!".formatted(toolExecutionRequest.name());

        final var toolResponse = new ToolResponseMessage.ToolResponse(toolExecutionRequest.id(),
                toolExecutionRequest.name(),
                responseData);

        return ToolResponseMessage.builder()
                .responses( List.of(toolResponse) )
                .build();

    }

    private static AsyncCommandAction<State> dispatchAction() {
        return AsyncCommandAction.command_async( (state, config ) ->
                    state.nextAction()
                            .map( Command::new )
                            .orElseGet( () -> new Command(AgentEx.CALL_MODEL_NODE ) ));

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

                final var toolResponseMessage = createRejectToolResponseMessage( currentToolExecutionRequests.get(0) );

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

    private static AsyncNodeActionWithConfig<State> dispatchTools( Set<String> approvals ) {

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

            }).orElseGet( () -> Map.of("agent_response", "no tool execution request found!",
                    State.NEXT_ACTION, MARK_FOR_REMOVAL,
                    State.TOOL_EXECUTION_REQUESTS, MARK_FOR_RESET));

        });
    }

    static AsyncNodeActionWithConfig<State> executeTool(SpringAIToolService toolService, String actionName  ) {
        return ( state, config ) -> {
            log.trace( "ExecuteTool" );

            final var currentToolExecutionRequests = state.toolExecutionRequests();

            if( currentToolExecutionRequests.isEmpty()) {
                return failedFuture( new IllegalArgumentException("no tool execution request found!") );

            }

            final var currentToolExecutionRequest = currentToolExecutionRequests.get(0);

            return toolService.executeFunctions( List.of(currentToolExecutionRequest), state.data(), "messages" )
                    .thenApply( command ->
                            mergeMap( command.update(),
                                    Map.of(State.TOOL_EXECUTION_REQUESTS,
                                            state.toolExecutionRequests$removeFirst() ),
                                            (v1,v2) -> v2 ));

        };

    }

    static AsyncCommandAction<State> shouldContinue() {

        return AsyncCommandAction.command_async( (state, config ) -> {
            var message = state.lastMessage().orElseThrow();

            var finishReason = message.getMetadata().getOrDefault("finishReason", "");

            if (Objects.equals(finishReason, "STOP")) {
                return new Command(AgentEx.END_LABEL );
            }

            if (message instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.hasToolCalls()) {
                    return new Command(AgentEx.CONTINUE_LABEL );
                }
            }
            return new Command( AgentEx.END_LABEL );

        });
    }
}

