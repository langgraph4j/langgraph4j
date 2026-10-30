package org.bsc.langgraph4j.node;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.SubCompiledGraphNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.subgraph.SubGraphOutput;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class ParallelNode<State extends AgentState> extends Node<State> {

    public record AsyncParallelNodeAction<State extends AgentState>(
            String nodeId,
            List<AsyncNodeActionWithConfig<State>> actions,
            Map<String, Channel<?>> channels) implements AsyncNodeActionWithConfig<State> {

        // True when a subgraph's final value for a key is a list that extends the
        // parent list by appending: the parent list is a prefix of the final list.
        // A wholesale replacement (or element removal) is not an append and keeps
        // the pass-1 replacement behavior.
        private static boolean isAppendedList(Object parentValue, Object finalValue) {
            if (!(parentValue instanceof List<?> parentList) || !(finalValue instanceof List<?> finalList)) {
                return false;
            }
            if (finalList.size() < parentList.size()) {
                return false;
            }
            return finalList.subList(0, parentList.size()).equals(parentList);
        }

        // For each append-style list key, the subgraph's contribution is the suffix
        // it appended beyond the parent list (a copy, never a view of the state).
        private static Map<String, Object> appendedListDelta(Map<String, Object> parentState, Map<String, Object> finalState) {
            Map<String, Object> delta = new HashMap<>();
            for (var entry : finalState.entrySet()) {
                var parentValue = parentState.get(entry.getKey());
                if (isAppendedList(parentValue, entry.getValue())) {
                    var parentList = (List<?>) parentValue;
                    var finalList = (List<?>) entry.getValue();
                    if (finalList.size() > parentList.size()) {
                        delta.put(entry.getKey(), new ArrayList<>(finalList.subList(parentList.size(), finalList.size())));
                    }
                }
            }
            return delta;
        }

        private CompletableFuture<Map<String, Object>> evalGenerator(AsyncGenerator<NodeOutput<State>> generator, Map<String, Object> initPartialState) {
            return generator.reduce(new ArrayList<NodeOutput<State>>(), (result, value) -> {
                        result.add(value);
                        return result;
                    })
                    .thenApply(list -> {
                        // A subgraph branch yields full-state snapshots. Merging every snapshot
                        // would apply non-idempotent reducers multiple times, so its contribution
                        // is the final state itself (apply() merges it via key replacement).
                        if (!list.isEmpty() && list.get(0) instanceof SubGraphOutput) {
                            return subgraphFinalState(generator);
                        }
                        // Chunk outputs are progress notifications: their state is the branch's
                        // starting state, not a delta. Like the serial path (embedGenerator),
                        // only the generator's final result value contributes to the branch delta.
                        // Merging every chunk's full state would turn the branch result into a
                        // full-state map, which apply() would then merge as a delta - reverting
                        // sibling branches' writes and double-applying non-idempotent reducers.
                        return mergeGeneratorResultValue(generator, initPartialState);
                    });
        }

        // Extracts the final full state produced by a subgraph branch, without merging
        // intermediate snapshots (which would double-apply non-idempotent reducers).
        // Returns null if the branch produced no result (empty/cancelled).
        private Map<String, Object> subgraphFinalState(AsyncGenerator<NodeOutput<State>> generator) {
            final var result = GraphResult.from(generator);

            if (result.isEmpty() || result.isCancelled()) {
                return null;
            }
            if (result.isStateData()) {
                return result.asStateData();
            }
            if (result.isInterruptionMetadata()) {
                throw new UnsupportedOperationException("Interruption metadata cannot be returned from a parallel branch streaming generator");
            }
            throw new IllegalArgumentException("Unsupported parallel branch streaming result type: %s".formatted(result.type()));
        }

        @SuppressWarnings("unchecked")
        private CompletableFuture<Map<String, Object>> evalNodeActionSync(AsyncNodeActionWithConfig<State> action, State state, RunnableConfig config) {

            return action.apply(state, config).thenCompose(partialState ->
                    partialState.entrySet().stream()
                            .filter(e -> e.getValue() instanceof AsyncGenerator)
                            .findFirst()
                            .map(generatorEntry -> {

                                var partialStateWithoutGenerator = partialState.entrySet().stream()
                                        .filter(e -> !Objects.equals(e.getKey(), generatorEntry.getKey()))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                return evalGenerator((AsyncGenerator<NodeOutput<State>>) generatorEntry.getValue(), partialStateWithoutGenerator);

                            })
                            .orElse(completedFuture(partialState))
            );
        }

        private CompletableFuture<Map<String, Object>> evalNodeActionAsync(AsyncNodeActionWithConfig<State> action,
                                                                           State state,
                                                                           RunnableConfig config,
                                                                           Executor executor) {
            return CompletableFuture.supplyAsync(() -> evalNodeActionSync(action, state, config).join(), executor);

        }
        private Optional<Executor> getExecutor(RunnableConfig config) {
            return config.metadata(nodeId)
                    .filter(value -> value instanceof Executor)
                    .map(Executor.class::cast);
        }

        // merges the generator result value into the branch partial state, like embedGenerator() on the serial path
        private Map<String, Object> mergeGeneratorResultValue(AsyncGenerator<NodeOutput<State>> generator, Map<String, Object> partialState) {

            final var result = GraphResult.from(generator);

            if (result.isEmpty() || result.isCancelled()) {
                return partialState;
            }
            if (result.isStateData()) {
                return AgentState.updateState(partialState, result.asStateData(), channels);
            }
            if (result.isInterruptionMetadata()) {
                throw new UnsupportedOperationException("Interruption metadata cannot be returned from a parallel branch streaming generator");
            }
            throw new IllegalArgumentException("Unsupported parallel branch streaming result type: %s".formatted(result.type()));
        }

        private CompletableFuture<Void> allOfFailFast(RunnableConfig config, CompletableFuture<?>... futures) {
            CompletableFuture<Void> manager = new CompletableFuture<>();

            for (CompletableFuture<?> future : futures) {
                future.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        // One failed, so fail the manager immediately
                        manager.completeExceptionally(throwable);
                    } else {
                        // Check if all others are done
                        if (Stream.of(futures).allMatch(CompletableFuture::isDone)) {
                            manager.complete(null);
                        }
                    }
                });
            }

            // Handle the case of an empty array
            if (futures.length == 0) {
                manager.complete(null);
            }

            return manager;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {

            final var evalNodeAction = getExecutor(config)
                    .map(executor -> (Function<AsyncNodeActionWithConfig<State>, CompletableFuture<Map<String, Object>>>) action -> evalNodeActionAsync(action, state, config, executor))
                    .orElseGet(() -> (Function<AsyncNodeActionWithConfig<State>, CompletableFuture<Map<String, Object>>>) action -> evalNodeActionSync(action, state, config));

            // Track which branches are subgraphs: they produce a full final state (not a delta),
            // so their results must REPLACE changed keys instead of being merged as deltas
            // (merging a full state as a delta would apply non-idempotent reducers twice).
            record Branch(boolean isSubgraph, CompletableFuture<Map<String, Object>> future) {}

            final var branches = actions.stream()
                    .map(action -> new Branch(
                            action instanceof SubCompiledGraphNodeAction,
                            evalNodeAction.apply(action)))
                    .toList();

            final var futuresArray = branches.stream()
                    .map(Branch::future)
                    .toArray(CompletableFuture[]::new);

            return allOfFailFast(config, futuresArray).thenApply(v -> {
                // Two-pass merge.
                //
                // Pass 1: subgraph branches first, via key replacement. This is
                // load-bearing for non-append keys: a subgraph that removes a key
                // (or computes an aggregate through a non-idempotent reducer) must
                // win over sibling deltas, which are applied afterwards.
                //
                // Pass 2: every branch's contribution in declaration order. Regular
                // branches merge their deltas via channels, as before. A subgraph
                // branch contributes only the suffix it appended to each
                // append-style list key (its final list minus the parent prefix).
                // Merging a subgraph's full list here would re-apply the parent
                // prefix, and replacing it would let a later subgraph clobber an
                // earlier branch's elements -- both observed as Issue104Test
                // failures, where subgraph messages jumped ahead of siblings.
                final var parentState = state.data();
                final Map<String, Object> result = new HashMap<>(parentState);

                for (var branch : branches) {
                    if (!branch.isSubgraph()) continue;
                    var finalState = branch.future().join();
                    if (finalState == null) continue; // cancelled/empty branch: no contribution
                    for (var entry : finalState.entrySet()) {
                        // Append-style list keys are deferred to pass 2.
                        if (isAppendedList(parentState.get(entry.getKey()), entry.getValue())) continue;
                        // Replace keys the subgraph changed; drop keys it removed.
                        if (!Objects.equals(entry.getValue(), parentState.get(entry.getKey()))) {
                            result.put(entry.getKey(), entry.getValue());
                        }
                    }
                    for (var key : parentState.keySet()) {
                        if (!finalState.containsKey(key)) {
                            result.remove(key);
                        }
                    }
                }

                var merged = result;
                for (var branch : branches) {
                    if (branch.isSubgraph()) {
                        var finalState = branch.future().join();
                        if (finalState == null) continue;
                        var delta = appendedListDelta(parentState, finalState);
                        if (!delta.isEmpty()) {
                            merged = AgentState.updateState(merged, delta, channels);
                        }
                    } else {
                        merged = AgentState.updateState(merged, branch.future().join(), channels);
                    }
                }
                return merged;
            });

        }
    }

    private static final String PARALLEL_PREFIX = "__PARALLEL__";

    public static String formatNodeId(String nodeId) {
        return "%s(%s)".formatted(PARALLEL_PREFIX, requireNonNull(nodeId, "nodeId cannot be null!"));
    }

    public ParallelNode(String id, List<AsyncNodeActionWithConfig<State>> actions, Map<String, Channel<?>> channels) {
        super(formatNodeId(id),
                (config) -> new AsyncParallelNodeAction<>(formatNodeId(id), actions, channels));
    }

    @Override
    public final boolean isParallel() {
        return true;
    }

}
