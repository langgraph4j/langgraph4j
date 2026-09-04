package org.bsc.langgraph4j.internal.node;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class ParallelNode<State extends AgentState> extends Node<State> {
    private static final String PARALLEL_PREFIX = "__PARALLEL__";

    public static String formatNodeId(String nodeId) {
        return "%s(%s)".formatted(PARALLEL_PREFIX, requireNonNull(nodeId, "nodeId cannot be null!"));
    }

    public record AsyncParallelNodeAction<State extends AgentState>(
            String nodeId,
            List<AsyncNodeActionWithConfig<State>> actions,
            Map<String, Channel<?>> channels) implements AsyncNodeActionWithConfig<State> {

        private CompletableFuture<Map<String, Object>> evalGenerator(AsyncGenerator<NodeOutput<State>> generator, Map<String, Object> initPartialState) {
            return generator.reduce(new ArrayList<NodeOutput<State>>(), (result, value) -> {
                        result.add(value);
                        return result;
            
                        for (var output : list) {
                            if (output.data().isPresent()) {
                                result = AgentState.updateState(result, output.data().get(), channels);
                            }
                        }
                    });
                });
        }