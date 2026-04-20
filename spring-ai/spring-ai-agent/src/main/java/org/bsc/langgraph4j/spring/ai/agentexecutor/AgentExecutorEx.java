package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.spring.ai.agent.CallModelAction;
import org.bsc.langgraph4j.spring.ai.agent.ReactAgentBuilderEx;
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

import static java.lang.String.format;
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

    String TOOL_EXECUTION_REQUESTS = "tool_execution_requests";
    String NEXT_ACTION = "next_action";

    /**
     * Represents the state of an agent in a system.
     * This class extends {@link AgentState} and defines constants for keys related to input, agent outcome,
     * and intermediate steps. It includes a static map schema that specifies how these keys should be handled.
     */
    class State extends MessagesState<Message> {

        private static final class Holder {
            private static final StateSerializer<State> INSTANCE = new SpringAIJacksonStateSerializer<>(State::new);
        }

        public static StateSerializer<State> defaultSerializer() {
            return Holder.INSTANCE;
        }

        static final Map<String, Channel<?>> SCHEMA = mergeMap(
                MessagesState.SCHEMA,
                Map.ofEntries(
                        Map.entry(TOOL_EXECUTION_REQUESTS, Channels.base( LinkedList::new )),
                        AgentEx.ApprovalResultChannelEntry
                ));

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

        public Optional<AssistantMessage.ToolCall> toolExecutionRequests$getFirst() {
            return toolExecutionRequests().stream().findFirst();
        }

        public List<AssistantMessage.ToolCall> toolExecutionRequests$removeFirst() {
            return toolExecutionRequests().stream().skip(1).toList();
        }

        public Optional<String> nextAction() {
            return value(NEXT_ACTION);
        }

        public Optional<AssistantMessage.ToolCall> getToolCallByNameFromLastMessage( String toolName ) {
            return this.lastMessage()
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .flatMap( toolCalls -> toolCalls.stream()
                            .filter( call -> toolName.equalsIgnoreCase(call.name()) )
                            .findFirst() );
        }
    }

    record FunctionToolBehaviour( SpringAIToolService toolService, ToolCallback toolCallback ) implements AgentEx.ToolBehaviour<Message, State> {
        public FunctionToolBehaviour {
            Objects.requireNonNull(toolService, "toolService cannot be null!");
            Objects.requireNonNull(toolCallback, "toolCallback cannot be null!");
        }
        @Override
        public String name() {
            return toolCallback.getToolDefinition().name();
        }

        @Override
        public void addToGraph(StateGraph<State> graph) throws GraphStateException {
            graph.addNode( name(), executeTool( this ));
        }
    }
    /**
     * Class responsible for building a state graph.
     */
    class Builder extends ReactAgentBuilderEx<Builder, State> {

        public Builder() {
        }

        public Builder(ReactAgentBuilderEx<?, State> builder) {
            super(builder);
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
        public StateGraph<State> build() throws GraphStateException {

            final var toolService = new SpringAIToolService(tools());

            final var callModelAction = new CallModelAction<State>( this );

            @SuppressWarnings("unchecked")
            final var toolsInfo = tools.stream().map( tool -> {

                if( tool instanceof AgentEx.ToolBehaviour<?, ?> agentToolBehaviour ) {
                    return (AgentEx.ToolBehaviour<Message, State>) agentToolBehaviour;
                }
                return new FunctionToolBehaviour( toolService, tool );
            }).toList();

            return agentBuilder
                    .stateSerializer( ofNullable(stateSerializer)
                            .orElseGet(State::defaultSerializer) )
                    .schema( State.SCHEMA )
                    .callModelAction( callModelAction )
                    .dispatchToolsAction( dispatchTools( approvals.keySet() ) )
                    .shouldContinueEdge( shouldContinue() )
                    .approvalActionEdge( approvalAction() )
                    .dispatchActionEdge( dispatchAction() )
                    .build( toolsInfo, approvals )
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

    private static AsyncCommandAction<State> dispatchAction() {
        return AsyncCommandAction.command_async( (state, config ) ->
                    state.nextAction()
                            .map( Command::new )
                            .orElseGet( () -> new Command("model" ) ));

    }

    private static AsyncCommandAction<State> approvalAction() {
        return (state, config) -> {

            final var approvalResultOptional = state.<String>value( AgentEx.APPROVAL_RESULT);

            if( approvalResultOptional.isEmpty() ) {
                return failedFuture( new IllegalStateException(format("resume property '%s' not found!", AgentEx.APPROVAL_RESULT) ));
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

                final var currentToolExecutionRequest =  currentToolExecutionRequests.get(0);

                final var toolResponse = new ToolResponseMessage.ToolResponse(currentToolExecutionRequest.id(),
                        currentToolExecutionRequest.name(),
                        "tool result is undefined because its execution has been DENIED!");

                var toolResponseMessage = ToolResponseMessage.builder()
                        .responses( List.of(toolResponse) )
                        .build();

                final var gotoNode =( currentToolExecutionRequests.size() > 1 ) ?
                        AgentEx.ACTION_DISPATCHER_NODE :
                        AgentEx.CALL_MODEL_NODE ;

                return completedFuture( new Command( gotoNode,
                        Map.of( "messages",toolResponseMessage,
                                TOOL_EXECUTION_REQUESTS, state.toolExecutionRequests$removeFirst(),
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

                return Map.of(NEXT_ACTION, nextAction,
                        TOOL_EXECUTION_REQUESTS, previousToolExecutionRequests);
            }

            final var toolExecutionRequests = state.lastMessage()
                    .filter(m -> MessageType.ASSISTANT == m.getMessageType())
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls);

            if (toolExecutionRequests.isEmpty()) {
                return Map.of("agent_response", "no tool execution request found!",
                        NEXT_ACTION, MARK_FOR_REMOVAL,
                        TOOL_EXECUTION_REQUESTS, MARK_FOR_RESET);
            } else {

                final var newToolExecutionRequests = toolExecutionRequests.get();

                final var currentToolExecutionRequest = newToolExecutionRequests.get(0);

                final var nextAction = approvals.contains(currentToolExecutionRequest.name()) ?
                        "approval_%s".formatted(currentToolExecutionRequest.name()) :
                        currentToolExecutionRequest.name();

                return Map.of(NEXT_ACTION, nextAction,
                        TOOL_EXECUTION_REQUESTS, newToolExecutionRequests);
            }
        });
    }

    static AsyncNodeActionWithConfig<State> executeTool( FunctionToolBehaviour toolBehaviour) {
        return ( state, config ) -> {
            log.trace( "ExecuteTool" );

            return state.toolExecutionRequests$getFirst().map( toolCall ->
                toolBehaviour.toolService().executeFunctions( List.of(toolCall), state.data(), "messages" )
                        .thenApply( command ->
                                mergeMap( command.update(),
                                        Map.of(TOOL_EXECUTION_REQUESTS,
                                                state.toolExecutionRequests$removeFirst() ),
                                        (v1,v2) -> v2 ))

            )
            .orElseGet( () -> failedFuture( new IllegalArgumentException("no tool execution request found!") ) );

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

