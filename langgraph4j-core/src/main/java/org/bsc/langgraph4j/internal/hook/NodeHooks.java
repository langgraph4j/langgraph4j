package org.bsc.langgraph4j.internal.hook;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
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

        void validate(GraphDefinition.Nodes<?> nodes) throws GraphStateException {
            if (callMap == null || callMap.isEmpty()) return;

            for (var nodeId : callMap.keySet()) {
                if (!nodes.anyMatchById(nodeId)) {
                    throw StateGraph.Errors.validationError.exception("nodeId '%s' declared in hook '%s' doesn't exist in graph".formatted(nodeId, toString()));
                }
            }

        }
    }

    // BEFORE CALL HOOK
    public class BeforeCalls extends Calls<NodeHook.BeforeCall<State>> {

        BeforeCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<State> apply(String nodeId, State state, RunnableConfig config, AgentStateFactory<State> stateFactory, Map<String, Channel<?>> schema) {
            if (isEmpty()) {
                return completedFuture(state);
            }

            final var futureReturn = Stream.concat(callListAsStream(), callMapAsStream(nodeId))
                    .reduce(completedFuture(state.data()),
                            (futureResult, call) ->
                                    futureResult.thenCompose(result -> call.applyBefore(nodeId, stateFactory.apply(result), config)),
                            // Update state with partial result  returned by hook
                            //.thenApply( partial -> AgentState.updateState( result, partial, schema ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );

            return futureReturn.thenApply(processedResult -> {
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

        public CompletableFuture<Map<String, Object>> apply(String nodeId, State state, RunnableConfig config, Map<String, Object> partialResult) {
            if (isEmpty()) {
                return completedFuture(partialResult);
            }
            return Stream.concat(callListAsStream(), callMapAsStream(nodeId))
                    .reduce(completedFuture(partialResult),
                            (futureResult, call) ->
                                    futureResult.thenCompose(result -> call.applyAfter(nodeId, state, config, result)),
                            // Merge original result with partial result returned by hook
                            //.thenApply( partial -> mergeMap(result, partial, ( oldValue, newValue) -> newValue ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );
        }

    }

    public final AfterCalls afterCalls = new AfterCalls();

    // WRAP CALL HOOK

    private record WrapCallChainLink<State extends AgentState>(
            String nodeId,
            NodeHook.WrapCall<State> delegate,
            AsyncNodeActionWithConfig<State> action
    ) implements AsyncNodeActionWithConfig<State> {

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return delegate.applyWrap(nodeId, state, config, action);
        }
    }


    public class WrapCalls extends Calls<NodeHook.WrapCall<State>> {
        WrapCalls() {
            super(Type.FIFO);
        }

        public CompletableFuture<Map<String, Object>> apply(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {
            if (isEmpty()) {
                return action.apply(state, config);
            }
            return Stream.concat(callListAsStream(), callMapAsStream(nodeId))
                    .reduce(action,
                            (acc, wrapper) -> new WrapCallChainLink<>(nodeId, wrapper, acc),
                            (v1, v2) -> v1)
                    .apply(state, config);
        }

    }

    public final WrapCalls wrapCalls = new WrapCalls();

    private boolean hasStreamingGenerator(Map<String, Object> partial) {
        return partial.values().stream().anyMatch(AsyncGenerator.class::isInstance);
    }

    public record Result<State extends AgentState>(
            CompletableFuture<Map<String, Object>> partialState,
            CompletableFuture<InterruptionMetadata<State>> interruptionMetadata) {

        public static <State extends AgentState> Result<State> ofPartialState(CompletableFuture<Map<String, Object>> partialState) {
            return new Result<>(partialState, null);
        }

        public static <State extends AgentState> Result<State> ofInterruptionMetadata(CompletableFuture<InterruptionMetadata<State>> interruptionMetadata) {
            return new Result<>(null, interruptionMetadata);
        }

        public Result {
            if (partialState == null && interruptionMetadata == null) {
                throw new IllegalArgumentException("Either partialState or interruptionMetadata must be provided");
            }
            if (partialState != null && interruptionMetadata != null) {
                throw new IllegalArgumentException("Only one of partialState or interruptionMetadata can be provided");
            }
        }

        public boolean hasPartialState() {
            return partialState != null;
        }
    }

    private Result<State> applyWrapCallHooksHandlingInterruption(
            String nodeId,
            State newState,
            CompileConfig compileConfig,
            RunnableConfig config,
            AsyncNodeActionWithConfig<State> action) {
        if (action instanceof InterruptableAction<?>) {
            @SuppressWarnings("unchecked") final var interruption = (InterruptableAction<State>) action;
            final var interruptMetadata = interruption.interrupt(config.nodeId(), newState, config);
            if (interruptMetadata.isPresent()) {
                final var future = compileConfig.checkpointSaver()
                        .map(saver -> saver.registerInterruption(config, interruptMetadata.get()))
                        .orElseGet(() -> completedFuture(interruptMetadata.get()));
                return Result.ofInterruptionMetadata(future);
            }
        }
        return Result.ofPartialState(wrapCalls.apply(nodeId, newState, config, action));

    }

    // ALL IN ONE METHODS
    public CompletableFuture<Result<State>> applyActionWithHooksHandlingInterruption(
            AsyncNodeActionWithConfig<State> action,
            String nodeId,
            State state,
            CompileConfig compileConfig,
            RunnableConfig config,
            AgentStateFactory<State> stateFactory,
            Map<String, Channel<?>> schema) {
// FIX #336, #342
        return beforeCalls.apply(nodeId, state, config, stateFactory, schema)
                .thenCompose(newState -> {
                    final var result = applyWrapCallHooksHandlingInterruption(nodeId, newState, compileConfig, config, action);
                    if (result.hasPartialState()) {
                        return result.partialState().thenApply(partial -> {
                            // Checking if the Node return AsyncGenerator as a Streaming node
                            if (hasStreamingGenerator(partial)) {
                                // Streaming: Skip AfterHook call here，Call in embedGenerator after get the completed result
                                return new Result<>(completedFuture(partial), null);
                            }
                            return new Result<>(afterCalls.apply(nodeId, newState, config, partial), null);
                        });
                    }

                    return completedFuture(result);

                });
    }


    public void validate(StateGraph.Nodes<?> nodes) throws GraphStateException {
        beforeCalls.validate(nodes);
        afterCalls.validate(nodes);
        wrapCalls.validate(nodes);
    }
}
