package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public class EdgeHooks<State extends AgentState> {
    
    static class Calls<T> extends HookCalls<T> {

        Calls(Type type) {
            super(type);
        }

        void validate(GraphDefinition.Edges<?> edges ) throws GraphStateException {
            if( callMap == null || callMap.isEmpty() ) return;

            for( var nodeId : callMap.keySet() ) {

                final var edge =  edges.edgeBySourceId( nodeId );

                if( edge.isEmpty() ) {
                    throw StateGraph.Errors.validationError.exception( "sourceId '%s' declared in edge hook '%s' doesn't exist in graph".formatted(nodeId, toString()));

                }
                if( edge.get().isParallel()) {
                    throw StateGraph.Errors.validationError.exception( "edge by sourceId '%s' is parallel and is not possible hook it".formatted(nodeId));
                }
                if( edge.get().target().value() == null ) {
                    throw StateGraph.Errors.validationError.exception( "edge by sourceId '%s' must be a conditional branch".formatted(nodeId));
                }
            }

        }
    }

    // BEFORE CALL HOOK
    public class BeforeCalls extends Calls<EdgeHook.BeforeCall<State>> {

        BeforeCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<State> apply(String sourceId, State state, RunnableConfig config, AgentStateFactory<State> stateFactory, Map<String, Channel<?>> schema ) {
            if( isEmpty() ) {
                return completedFuture(state);
            }

            final var futureReturn = Stream.concat( callListAsStream(), callMapAsStream(sourceId))
                    .reduce( completedFuture(state.data()),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyBefore(sourceId, stateFactory.apply(result), config)
                                            .thenApply( command -> AgentState.updateState( result, command.update(), schema ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );

            return futureReturn.thenApply( processedResult -> {
                final var newStateData = state.update(processedResult, schema);
                return stateFactory.apply(newStateData);
            });
        }

    }
    public final BeforeCalls beforeCalls = new BeforeCalls();

    // AFTER CALL HOOK
    public class AfterCalls extends Calls<EdgeHook.AfterCall<State>> {

        AfterCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<Command> apply(String sourceId, State state, RunnableConfig config, Command partialResult ) {
            if( isEmpty() ) {
                return completedFuture(partialResult);
            }
            return Stream.concat( callListAsStream(), callMapAsStream(sourceId))
                    .reduce( completedFuture(partialResult),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyAfter( sourceId, state, config, result)),
                                            // Merge original Command with Command returned by hook
                                            //.thenApply( command -> new Command(
                                            //            command.gotoNodeSafe().orElse(null),
                                            //            mergeMap(result.update(), command.update(), ( oldValue, newValue) -> newValue ))
                                            //)),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );
        }

    }

    public final AfterCalls afterCalls = new AfterCalls();

    // WRAP CALL HOOK

    private record WrapCallChainLink<State extends AgentState>  (
            String sourceId,
            EdgeHook.WrapCall<State> delegate,
            AsyncCommandAction<State> action
    )  implements AsyncCommandAction<State> {

        @Override
        public CompletableFuture<Command> apply(State state, RunnableConfig config) {
            return delegate.applyWrap(sourceId, state, config, action);
        }
    }


    public class WrapCalls extends Calls<EdgeHook.WrapCall<State>> {
        WrapCalls() {
            super(Type.FIFO);
        }

        public CompletableFuture<Command> apply(String sourceId, State state, RunnableConfig config, AsyncCommandAction<State> action ) {
            if( isEmpty() ) {
                return action.apply( state, config );
            }
            return Stream.concat( callListAsStream(), callMapAsStream(sourceId))
                    .reduce(action,
                            (acc, wrapper) -> new WrapCallChainLink<>(sourceId, wrapper, acc),
                            (v1, v2) -> v1)
                    .apply(state, config);
        }

    }
    public final WrapCalls wrapCalls = new WrapCalls();

    // ALL IN ONE METHODS

    public CompletableFuture<Command> applyActionWithHooks( AsyncCommandAction<State> action,
                                                                        String sourceId,
                                                                        State state,
                                                                        RunnableConfig config,
                                                                        AgentStateFactory<State> stateFactory,
                                                                        Map<String, Channel<?>> schema ) {
        return beforeCalls.apply( sourceId, state, config, stateFactory, schema )
                .thenCompose( newState -> wrapCalls.apply(sourceId, newState, config, action)
                        .thenCompose( command -> afterCalls.apply(sourceId, newState, config, command) ));

    }

    public void validate( GraphDefinition.Edges<?> edges ) throws GraphStateException {
        beforeCalls.validate(edges);
        afterCalls.validate(edges);
        wrapCalls.validate(edges);
    }

}
