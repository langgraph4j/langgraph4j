package org.bsc.langgraph4j.agent;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.internal.hook.EdgeHooks;
import org.bsc.langgraph4j.internal.hook.NodeHooks;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.START;
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
public interface AgentEx {

    String CONTINUE_LABEL = "continue";
    String END_LABEL = "end";
    String APPROVAL_RESULT = "approval_result";

    String CALL_MODEL_NODE = "model";
    String ACTION_DISPATCHER_NODE = "action_dispatcher";
    String APPROVAL_ACTION = "approval_action";

    enum ApprovalState {
        APPROVED,
        REJECTED
    }

    Map.Entry<String,Channel<?>> ApprovalResultChannelEntry = Map.entry( APPROVAL_RESULT, Channels.base( (prevValue, newValue ) -> {
        if( newValue instanceof ApprovalState approval ) {
            return approval.name();
        }
        return newValue;
    }) );

    interface ToolBehaviour<M, State extends MessagesState<M>>   {
        String name();
        void addToGraph( StateGraph<State> graph ) throws GraphStateException;
    }


    final class ApprovalNodeAction<M, State extends MessagesState<M>> implements AsyncNodeActionWithConfig<State>, InterruptableAction<State> {

        private final BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider;

        private ApprovalNodeAction( Builder<M,State> builder ) {
            this.interruptionMetadataProvider = builder.interruptionMetadataProvider;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return completedFuture(Map.of());
        }

        @Override
        public Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state, RunnableConfig config) {
            if( state.<String>value(APPROVAL_RESULT).map(String::isEmpty).orElse(true) ) {
                var metadata = interruptionMetadataProvider.apply(nodeId,state);
                return Optional.of(metadata);
            }
            return Optional.empty();
        }

        public static <M, State extends MessagesState<M>> Builder<M,State> builder() {
            return new Builder<>();
        }

        public static class Builder<M, State extends MessagesState<M>> {
            private BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider;

            public Builder<M,State> interruptionMetadataProvider(  BiFunction<String, State, InterruptionMetadata<State>> provider  ) {
                interruptionMetadataProvider = provider;
                return this;
            }

            public ApprovalNodeAction<M,State> build() {
                Objects.requireNonNull(interruptionMetadataProvider, "interruptionMetadataProvider cannot be null!");
                return new ApprovalNodeAction<>(this);
            }

        }

    }

    static <M, S extends MessagesState<M>, TOOL> Builder<M,S, TOOL> builder() {
        return new Builder<>();
    }

    class Builder<M, S extends MessagesState<M>, TOOL> {

        private StateSerializer<S> stateSerializer;
        private AsyncNodeActionWithConfig<S> callModelAction;
        private AsyncNodeActionWithConfig<S> dispatchToolsAction;
        private AsyncCommandAction<S> dispatchActionEdge;
        private AsyncCommandAction<S> shouldContinueEdge;
        private AsyncCommandAction<S> approvalActionEdge;
        private Map<String, Channel<?>> schema;

        private final NodeHooks<S> nodeHooks = new NodeHooks<>();
        private final EdgeHooks<S> edgeHooks = new EdgeHooks<>();

        private static <H> void addHook( Map<String,List<H>> map, String id, H hook) {
            map.computeIfAbsent(id, k -> new LinkedList<>()).add(hook);
        }


        public Builder<M, S, TOOL> stateSerializer(StateSerializer<S> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        public Builder<M, S, TOOL> schema(Map<String, Channel<?>> schema) {
            this.schema = schema;
            return this;
        }

        public Builder<M, S, TOOL> addNodeHook(NodeHook.WrapCall<S> wrapCall ) {
            nodeHooks.wrapCalls.add(wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addNodeHook( String nodeId, NodeHook.WrapCall<S> wrapCall ) {
            nodeHooks.wrapCalls.add(nodeId, wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addNodeHook(NodeHook.BeforeCall<S> wrapCall ) {
            nodeHooks.beforeCalls.add(wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addNodeHook( String nodeId, NodeHook.BeforeCall<S> wrapCall ) {
            nodeHooks.beforeCalls.add(nodeId, wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addNodeHook(NodeHook.AfterCall<S> wrapCall ) {
            nodeHooks.afterCalls.add(wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addNodeHook( String nodeId, NodeHook.AfterCall<S> wrapCall ) {
            nodeHooks.afterCalls.add(nodeId, wrapCall);
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook(EdgeHook.WrapCall<S> wrapCall ) {
            edgeHooks.wrapCalls.add( wrapCall );
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook( String nodeId, EdgeHook.WrapCall<S> wrapCall ) {
            edgeHooks.wrapCalls.add( nodeId, wrapCall );
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook(EdgeHook.BeforeCall<S> wrapCall ) {
            edgeHooks.beforeCalls.add( wrapCall );
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook( String nodeId, EdgeHook.BeforeCall<S> wrapCall ) {
            edgeHooks.beforeCalls.add( nodeId, wrapCall );
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook(EdgeHook.AfterCall<S> wrapCall ) {
            edgeHooks.afterCalls.add( wrapCall );
            return this;
        }
        public Builder<M, S, TOOL> addEdgeHook( String nodeId, EdgeHook.AfterCall<S> wrapCall ) {
            edgeHooks.afterCalls.add( nodeId, wrapCall );
            return this;
        }

        public Builder<M, S, TOOL> callModelAction(AsyncNodeActionWithConfig<S> callModelAction) {
            this.callModelAction = callModelAction;
            return this;
        }

        public Builder<M, S, TOOL> dispatchToolsAction(AsyncNodeActionWithConfig<S> dispatchToolsAction) {
            this.dispatchToolsAction = dispatchToolsAction;
            return this;
        }

        public Builder<M, S, TOOL> shouldContinueEdge(AsyncCommandAction<S> shouldContinueEdge) {
            this.shouldContinueEdge = shouldContinueEdge;
            return this;
        }

        public Builder<M, S, TOOL> dispatchActionEdge(AsyncCommandAction<S> dispatchActionEdge) {
            this.dispatchActionEdge = dispatchActionEdge;
            return this;
        }

        public Builder<M, S, TOOL> approvalActionEdge(AsyncCommandAction<S> approvalActionEdge) {
            this.approvalActionEdge = approvalActionEdge;
            return this;
        }

        public StateGraph<S> build(Collection<? extends ToolBehaviour<M, S>> tools, Map<String, ApprovalNodeAction<M, S>> approvals) throws GraphStateException {

            // verify approval
            for (var approval : approvals.keySet()) {

                tools.stream()
                        .filter(tool -> Objects.equals(tool.name(), approval))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(format("approval action %s not found!", approval)));
            }

            var graph = new StateGraph<>(
                    requireNonNull(schema, "schema is required!"),
                    requireNonNull(stateSerializer, "stateSerializer is required!"))
                    .addNode(CALL_MODEL_NODE, requireNonNull(callModelAction, "callModelAction is required!"))
                    .addNode(ACTION_DISPATCHER_NODE, requireNonNull(dispatchToolsAction, "dispatchToolsAction is required!"))
                    .addAfterCallNodeHook(ACTION_DISPATCHER_NODE, (nodeId, state, config, lastResult ) -> {
                        final Map<String,Object> result = ( config.isRunningInStudio() ) ?
                                mergeMap( lastResult, Map.of(APPROVAL_RESULT, ""), (left, right) -> right):
                                lastResult;
                        return completedFuture( result );
                    })
                    .addEdge(START, CALL_MODEL_NODE)
                    .addConditionalEdges(CALL_MODEL_NODE,
                            requireNonNull(shouldContinueEdge, "shouldContinueEdge is required!"),
                            EdgeMappings.builder()
                                    .to(ACTION_DISPATCHER_NODE, "continue")
                                    .toEND("end")
                                    .build());

            var actionMappingBuilder = EdgeMappings.builder()
                    .to(CALL_MODEL_NODE)
                    .toEND();

            // apply hooks

            // apply global node hooks
            nodeHooks.beforeCalls.callListAsStream().forEach(graph::addBeforeCallNodeHook);
            nodeHooks.wrapCalls.callListAsStream().forEach(graph::addWrapCallNodeHook);
            nodeHooks.afterCalls.callListAsStream().forEach(graph::addAfterCallNodeHook);
            // apply node hooks by node id
            nodeHooks.beforeCalls.callMapAsStream().forEach( entry ->
                    entry.getValue().forEach( hook -> graph.addBeforeCallNodeHook(entry.getKey(), hook)));
            nodeHooks.wrapCalls.callMapAsStream().forEach( entry ->
                     entry.getValue().forEach( hook -> graph.addWrapCallNodeHook(entry.getKey(), hook)));
            nodeHooks.afterCalls.callMapAsStream().forEach( entry ->
                    entry.getValue().forEach( hook -> graph.addAfterCallNodeHook(entry.getKey(), hook)));

            // apply global edge hooks
            edgeHooks.beforeCalls.callListAsStream().forEach(graph::addBeforeCallEdgeHook);
            edgeHooks.wrapCalls.callListAsStream().forEach(graph::addWrapCallEdgeHook);
            edgeHooks.afterCalls.callListAsStream().forEach(graph::addAfterCallEdgeHook);
            // apply edge hooks by node id
            edgeHooks.beforeCalls.callMapAsStream()
                    .filter(entry -> !entry.getKey().equals(APPROVAL_ACTION))
                    .forEach( entry ->
                            entry.getValue().forEach( hook -> graph.addBeforeCallEdgeHook(entry.getKey(), hook)));
            edgeHooks.wrapCalls.callMapAsStream()
                    .filter(entry -> !entry.getKey().equals(APPROVAL_ACTION))
                    .forEach( entry ->
                            entry.getValue().forEach( hook -> graph.addWrapCallEdgeHook(entry.getKey(), hook)));
            edgeHooks.afterCalls.callMapAsStream()
                    .filter(entry -> !entry.getKey().equals(APPROVAL_ACTION))
                    .forEach( entry ->
                            entry.getValue().forEach( hook -> graph.addAfterCallEdgeHook(entry.getKey(), hook)));

            for (var tool : tools) {

                final var toolName = tool.name();

                if (approvals.containsKey(toolName)) {

                    var approval_nodeId = "approval_%s".formatted( toolName );

                    var approvalAction = approvals.get(toolName);

                    // apply approval action hooks
                    edgeHooks.beforeCalls.callMapAsStream(APPROVAL_ACTION)
                            .forEach( hook -> graph.addBeforeCallEdgeHook(approval_nodeId, hook));
                    edgeHooks.wrapCalls.callMapAsStream(APPROVAL_ACTION)
                            .forEach( hook -> graph.addWrapCallEdgeHook(approval_nodeId, hook));
                    edgeHooks.afterCalls.callMapAsStream(APPROVAL_ACTION)
                            .forEach( hook -> graph.addAfterCallEdgeHook(approval_nodeId, hook));

                    graph.addNode(approval_nodeId, approvalAction);

                    graph.addConditionalEdges(approval_nodeId,
                            requireNonNull(approvalActionEdge, "approvalActionEdge is required!"),
                            EdgeMappings.builder()
                                    .to(CALL_MODEL_NODE)
                                    .to(ACTION_DISPATCHER_NODE)
                                    .to(toolName, ApprovalState.APPROVED.name())
                                    .build()
                    );

                    actionMappingBuilder.to(approval_nodeId);
                } else {
                    actionMappingBuilder.to(toolName);
                }

                tool.addToGraph(graph);
                graph.addEdge(toolName, ACTION_DISPATCHER_NODE);

            }

            return graph.addConditionalEdges(ACTION_DISPATCHER_NODE,
                    requireNonNull(dispatchActionEdge, "dispatchActionEdge is required!" ),
                    actionMappingBuilder.build())
                    ;
        }
    }

}
