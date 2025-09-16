#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import ${groupId}.GraphStateException;
import ${groupId}.StateGraph;
import ${groupId}.action.*;
import ${groupId}.agent.AgentEx;
import ${groupId}.prebuilt.MessagesState;
import ${groupId}.spring.ai.serializer.std.SpringAIStateSerializer;
import ${groupId}.spring.ai.tool.SpringAIToolService;
import ${groupId}.state.AgentState;
import ${groupId}.state.Channel;
import ${groupId}.state.Channels;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static ${groupId}.state.AgentState.MARK_FOR_REMOVAL;
import static ${groupId}.state.AgentState.MARK_FOR_RESET;
import static ${groupId}.utils.CollectionsUtils.mapOf;
import static ${groupId}.utils.CollectionsUtils.mergeMap;

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
 *          |                 ${symbol_escape}              ${symbol_escape}                    ${symbol_escape}
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

        private  List<AssistantMessage.ToolCall> toolCallsByName( String actionName ) {
            return lastMessage()
                    .filter(m -> MessageType.ASSISTANT == m.getMessageType())
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .map(requests -> requests.stream()
                            .filter(req -> Objects.equals(req.name(), actionName)).toList())
                    .orElseGet(List::of)
                    ;
        }
    }

    /**
     * Class responsible for building a state graph.
     */
    class Builder extends AgentExecutorBuilder<Builder, State> {

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
        public StateGraph<State> build( Function<AgentExecutorBuilder<?,?>, AgentExecutor.ChatService> chatServiceFactory ) throws GraphStateException {

            if (stateSerializer == null) {
                stateSerializer = new SpringAIStateSerializer<>(AgentExecutorEx.State::new);
            }

            final var chatService = requireNonNull(chatServiceFactory, "chatServiceFactory cannot be null!").apply(this);

            // verify approval
            final var toolService = new SpringAIToolService(tools());

            return AgentEx.<Message, State, ToolCallback>builder()
                    .stateSerializer( stateSerializer )
                    .schema( State.SCHEMA )
                    .toolName( tool -> tool.getToolDefinition().name() )
                    .callModelAction( CallModel.of( chatService, streaming ) )
                    .dispatchToolsAction( dispatchTools( approvals.keySet() ) )
                    .executeToolFactory( ( toolName ) -> executeTooL( toolService, toolName ) )
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

                var tools = state.toolCallsByName( actionName );

                if(tools.isEmpty())  {
                    throw new IllegalStateException("no tool execution request found!");
                }

                var toolResponses = tools.stream().map( toolCall ->
                        new ToolResponseMessage.ToolResponse(toolCall.id(), actionName, "execution has been DENIED!")
                ).toList();

                var toolResponseMessages = new ToolResponseMessage( toolResponses );

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

    static AsyncNodeActionWithConfig<State> executeTooL(SpringAIToolService toolService, String actionName  ) {
        return ( state, config ) -> {
            log.trace( "ExecuteTool" );

            var toolCalls = state.toolCallsByName(actionName);

            if( toolCalls.isEmpty() ) {
                return CompletableFuture.failedFuture( new IllegalArgumentException("no tool execution request found!") );
            }

            return toolService.executeFunctions( toolCalls )
                    .thenApply(result -> Map.of("tool_execution_results", result));

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

