package org.bsc.langgraph4j.internal.hook;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.action.NodeResult;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public class NodeHooks<State extends AgentState> {

    static class Calls<T> extends HookCalls<T> {

        Calls(Type type) {
            super(type);
        }

        void validate(GraphDefinition.Nodes<?> nodes ) throws GraphStateException {
            if( callMap == null || callMap.isEmpty() ) return;

            for( var nodeId : callMap.keySet() ) {
                if( !nodes.anyMatchById(nodeId) ) {
                    throw StateGraph.Errors.validationError.exception( "nodeId '%s' declared in hook '%s' doesn't exist in graph".formatted(nodeId, toString()));
                }
            }

        }
    }

    // BEFORE CALL HOOK
    public class BeforeCalls extends Calls<NodeHook.BeforeCall<State>> {

        BeforeCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<State> apply( String nodeId, State state, RunnableConfig config, AgentStateFactory<State> stateFactory, Map<String, Channel<?>> schema ) {
            if( isEmpty() ) {
                return completedFuture(state);
            }

            final var futureReturn = Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce( completedFuture(state.data()),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyBefore(nodeId, stateFactory.apply(result), config)),
                                            // Update state with partial result  returned by hook
                                            //.thenApply( partial -> AgentState.updateState( result, partial, schema ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );

            return futureReturn.thenApply( processedResult -> {
                final var newStateData = AgentState.updateState(state, processedResult, schema);
                return stateFactory.apply(newStateData);
            });
        }

    }
    public final BeforeCalls beforeCalls = new BeforeCalls();

    // AFTER CALL HOOK
    public class AfterCalls extends Calls<NodeHook.AfterCall<State>> {

        AfterCalls() {
            super(Type.LIFO);
        }


        public CompletableFuture<NodeResult> apply(String nodeId, State state, RunnableConfig config, NodeResult nodeResult ) {
            if( isEmpty() ) {
                return completedFuture(nodeResult);
            }
            return Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce( completedFuture(nodeResult),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyAfterWithResult( nodeId, state, config, result)),
                                            // Merge original result with partial result returned by hook
                                            //.thenApply( partial -> mergeMap(result, partial, ( oldValue, newValue) -> newValue ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );
        }

    }

    public final AfterCalls afterCalls = new AfterCalls();

    // WRAP CALL HOOK

    private record WrapCallChainLink<State extends AgentState>  (
            String nodeId,
            NodeHook.WrapCall<State> delegate,
            AsyncNodeActionWithConfig<State> action
    )  implements AsyncNodeActionWithConfig<State> {

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return delegate.applyWrap(nodeId, state, config, action);
        }

        @Override
        public CompletableFuture<NodeResult> applyWithResult(State state, RunnableConfig config) {
            return delegate.applyWrapWithResult(nodeId, state, config, action);
        }
    }


    public class WrapCalls extends Calls<NodeHook.WrapCall<State>> {
        WrapCalls() {
            super(Type.FIFO);
        }

        public CompletableFuture<NodeResult> apply( String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action ) {
            if( isEmpty() ) {
                return action.applyWithResult( state, config );
            }
            return Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce(action,
                            (acc, wrapper) -> new WrapCallChainLink<>(nodeId, wrapper, acc),
                            (v1, v2) -> v1)
                    .applyWithResult(state, config);
        }

    }
    public final WrapCalls wrapCalls = new WrapCalls();

    private boolean hasStreamingGenerator( Map<String, Object> partial ) {
        return partial.values().stream().anyMatch(AsyncGenerator.class::isInstance);
    }

    public record Result<State extends AgentState>(
            CompletableFuture<NodeResult> nodeResult,
            CompletableFuture<InterruptionMetadata<State>> interruptionMetadata) {

        public Result {
            if( nodeResult == null && interruptionMetadata == null ) {
                throw new IllegalArgumentException( "Either nodeResult or interruptionMetadata must be provided");
            }
            if( nodeResult != null && interruptionMetadata != null ) {
                throw new IllegalArgumentException( "Only one of nodeResult or interruptionMetadata can be provided");
            }

        }
        public Result(InterruptionMetadata<State> interruptionMetadata) {
            this(null, CompletableFuture.completedFuture(interruptionMetadata));
        }
        public Result( CompletableFuture<NodeResult> nodeResult) {
            this( nodeResult, null);
        }

        public boolean hasNodeResult() {
            return nodeResult != null;
        }
    }

    private Result<State> applyWrapCallHooksHandlingInterruption(String nodeId,
                                                                 State newState,
                                                                 RunnableConfig config,
                                                                 AsyncNodeActionWithConfig<State> action)
    {
        if( action instanceof InterruptableAction<?>) {
            @SuppressWarnings("unchecked")
            final var interruption = (InterruptableAction<State>) action;
            final var interruptMetadata = interruption.interrupt( config.nodeId(), newState, config );
            if( interruptMetadata.isPresent() ) {
                return new Result<>( interruptMetadata.get() );
            }
        }
        return new Result<>( wrapCalls.apply(nodeId, newState, config, action ));

    }

    // ALL IN ONE METHODS
    public CompletableFuture<Result<State>> applyActionWithHooksHandlingInterruption(AsyncNodeActionWithConfig<State> action,
                                                                                   String nodeId,
                                                                                   State state,
                                                                                   RunnableConfig config,
                                                                                   AgentStateFactory<State> stateFactory,
                                                                                   Map<String, Channel<?>> schema ) {
        // FIX #336, #342
        return beforeCalls.apply(nodeId, state, config, stateFactory, schema)
                .thenCompose(newState -> {
                    final var result = applyWrapCallHooksHandlingInterruption(nodeId, newState, config, action);
                    if( result.hasNodeResult() ) {
                        return result.nodeResult().thenApply( nodeResult -> {
                            // Checking if the Node return AsyncGenerator as a Streaming node
                            if( nodeResult.hasGenerator() ) {
                                // Streaming: Skip AfterHook call here，Call in embedGenerator after get the completed result
                                return new Result<>( completedFuture(nodeResult), null );
                            }
                            return new Result<>(afterCalls.apply(nodeId, newState, config, nodeResult), null );
                        });
                    }

                    return completedFuture(result);

                });
    }


    public void validate( StateGraph.Nodes<?> nodes ) throws GraphStateException {
        beforeCalls.validate(nodes);
        afterCalls.validate(nodes);
        wrapCalls.validate(nodes);
    }
}
