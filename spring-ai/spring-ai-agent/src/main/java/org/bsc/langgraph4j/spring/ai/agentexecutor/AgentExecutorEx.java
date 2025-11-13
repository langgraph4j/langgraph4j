package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.AgentEx;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agent.CallModelAction;
import org.bsc.langgraph4j.spring.ai.agent.ReactAgentBuilder;
import org.bsc.langgraph4j.spring.ai.serializer.std.SpringAIStateSerializer;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
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
public interface AgentExecutorEx {

    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentExecutorEx.class);

    /**
     * Represents the state of an agent in a system.
     * This class extends {@link AgentState} and defines constants for keys related to input, agent outcome,
     * and intermediate steps. It includes a static map schema that specifies how these keys should be handled.
     */
    class State extends MessagesState<Message> {

        static final Map<String, Channel<?>> SCHEMA = mergeMap(
                MessagesState.SCHEMA,
                Map.of( "tool_execution_results", Channels.appender(ArrayList::new) ) );

        /**
         * Constructs a new State with the given initialization data.
         *
         * @param initData the initialization data
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        public List<ToolResponseMessage> toolExecutionResults() {
            return this.<List<ToolResponseMessage>>value("tool_execution_results")
                    .orElseThrow(() -> new RuntimeException("messages not found"));
        }

        public Optional<String> nextAction() {
            return value("next_action");
        }

        public Optional<ToolResponseMessage.ToolResponse> findToolResponseByToolCall(AssistantMessage.ToolCall toolCall ) {
            return  toolExecutionResults().stream()
                        .flatMap( r -> r.getResponses().stream() )
                        .filter( r -> toolCall.id().equals(r.id()) )
                        .findAny();

        }
        private Stream<AssistantMessage.ToolCall> toolCallsAsStream() {
            return lastMessage()
                    .filter(m -> MessageType.ASSISTANT == m.getMessageType())
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .stream()
                    .flatMap(Collection::stream)
                    //.map(requests -> requests.stream().filter(req -> Objects.equals(req.name(), actionName))
                    //.toList())
                    //.orElseGet(List::of)
                    ;
        }
    }

    /**
     * Class responsible for building a state graph.
     */
    class Builder extends ReactAgentBuilder<Builder, State> {

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

            if (stateSerializer == null) {
                stateSerializer = new SpringAIStateSerializer<>(AgentExecutorEx.State::new);
            }

            final var chatService = requireNonNull(chatServiceFactory, "chatServiceFactory cannot be null!").apply(this);

            // verify approval
            final var toolService = new SpringAIToolService(tools());

            final var callModelAction = new CallModelAction<State>( chatService, streaming );

            return AgentEx.<Message, State, ToolCallback>builder()
                    .stateSerializer( stateSerializer )
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

    private static AsyncCommandAction<State> dispatchAction() {
        return AsyncCommandAction.command_async( (state, config ) ->
                    state.nextAction()
                            .map( Command::new )
                            .orElseGet( () -> new Command("model" ) ));

    }

    private static AsyncCommandAction<State> approvalAction() {
        return (state, config) -> {
            var result = new CompletableFuture<Command>();

            if( state.value( AgentEx.APPROVAL_RESULT_PROPERTY ).isEmpty() ) {
                result.completeExceptionally( new IllegalStateException(format("resume property '%s' not found!", AgentEx.APPROVAL_RESULT_PROPERTY) ));
                return result;
            }

            var resumeState = state.<String>value( AgentEx.APPROVAL_RESULT_PROPERTY )
                    .orElseThrow( () -> new IllegalStateException(format("resume property '%s' not found!", AgentEx.APPROVAL_RESULT_PROPERTY) ));

            if( Objects.equals( resumeState, AgentEx.ApprovalState.APPROVED.name() )) {
                // APPROVED
                result.complete( new Command( resumeState,
                        Map.of(AgentEx.APPROVAL_RESULT_PROPERTY, MARK_FOR_REMOVAL)));

            }
            else {
                // DENIED
                var actionName = state.nextAction()
                        .map( v -> v.replace("approval_", "") )
                        .orElseThrow( () -> new IllegalStateException("no next action found!"));

                if(state.toolCallsAsStream().findAny().isEmpty())  {
                    throw new IllegalStateException("no tool execution request found!");
                }

                var toolResponses = state.toolCallsAsStream()
                                .map( toolCall -> {

                                    var prevToolResponse = state.findToolResponseByToolCall( toolCall );

                                    if( prevToolResponse.isPresent() ) {
                                        return prevToolResponse.get();
                                    }
                                    if( toolCall.name().equals(actionName) ) {
                                        return new ToolResponseMessage.ToolResponse(toolCall.id(),
                                                actionName,
                                                "execution has been DENIED!");
                                    }
                                    return new ToolResponseMessage.ToolResponse(toolCall.id(),
                                            toolCall.name(),
                                            "execution has been SUSPENDED, please re-executed it!");
                                })
                                .toList();

                var toolResponseMessages = ToolResponseMessage.builder().responses(toolResponses).build();

                result.complete( new Command( resumeState,
                        Map.of( "messages",toolResponseMessages,
                                "tool_execution_results", toolResponseMessages,
                                AgentEx.APPROVAL_RESULT_PROPERTY, MARK_FOR_REMOVAL)));

            }
            return result;
        };
    }

    private static AsyncNodeActionWithConfig<State> dispatchTools(Set<String> approvals ) {

        return AsyncNodeActionWithConfig.node_async(( state, config ) -> {
            log.trace( "DispatchTools" );

            var toolExecutionRequests = state.lastMessage()
                    .filter( m -> MessageType.ASSISTANT==m.getMessageType() )
                    .map( AssistantMessage.class::cast )
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls);

            if( toolExecutionRequests.isEmpty() ) {
                return Map.of("agent_response", "no tool execution request found!" );
            }

            var requests = toolExecutionRequests.get();

            return requests.stream()
                    .filter( request -> state.toolExecutionResults().stream()
                            .flatMap( r -> r.getResponses().stream() )
                            .noneMatch( r -> Objects.equals(r.name(), request.name())))
                    .findFirst()
                    .map( result -> ( approvals.contains(result.name()) ?
                            format( "approval_%s", result.name() ) :
                            result.name()))
                    .map( actionId -> Map.<String,Object>of( "next_action", actionId ))
                    .orElseGet( () ->  Map.of("messages",  state.toolExecutionResults(),
                            "tool_execution_results", MARK_FOR_RESET, /* reset results */
                            "next_action", MARK_FOR_REMOVAL  /* remove element */ ));

        });

    }

    static AsyncNodeActionWithConfig<State> executeTool(SpringAIToolService toolService, String actionName  ) {
        return ( state, config ) -> {
            log.trace( "ExecuteTool" );

            var toolCalls = state.toolCallsAsStream()
                                .filter( t -> t.name().equals(actionName))
                                .toList();

            if( toolCalls.isEmpty() ) {
                return CompletableFuture.failedFuture( new IllegalArgumentException("no tool execution request found!") );
            }

            return toolService.executeFunctions( toolCalls, state.data(), "tool_execution_results" )
                    .thenApply( Command::update );

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

