package org.bsc.langgraph4j;

import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.junit.jupiter.api.Assertions.*;

public class LG4JCancellationTest {

    private static StateGraph<AgentState> trackedGraph(Set<Thread> executionThreads) {
        return new StateGraph<AgentState>(Map.of(), AgentState::new)
                .addBeforeCallNodeHook((nodeId, state, config) -> {
                    executionThreads.add(Thread.currentThread());
                    return completedFuture(state.data());
                });
    }

    private static void awaitGraphExecution(Set<Thread> executionThreads) throws InterruptedException {
        assertFalse(executionThreads.isEmpty(), "graph execution did not start");
        for (var thread : Set.copyOf(executionThreads)) {
            if (thread instanceof ForkJoinWorkerThread worker) {
                assertTrue(worker.getPool().awaitQuiescence(5, TimeUnit.SECONDS),
                        "graph execution did not finish");
            } else {
                thread.join(TimeUnit.SECONDS.toMillis(5));
                assertFalse(thread.isAlive(), "graph execution did not finish");
            }
        }
    }


    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void cancellationDoesNotStartTheNextNode(boolean mayInterruptIfRunning, boolean synchronousNode) throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var entered = new CountDownLatch(1);
        var pending = new CompletableFuture<Map<String, Object>>();
        var nextNodeRan = new AtomicBoolean();

        var graph = trackedGraph(executionThreads)
                .addNode("waiting", (state, config) -> {
                    entered.countDown();
                    return synchronousNode ? completedFuture(pending.join()) : pending;
                })
                .addNode("next", (state, config) -> {
                    nextNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "waiting")
                .addEdge("waiting", "next")
                .addEdge("next", END)
                .compile();

        var generator = graph.stream(GraphInput.args(Map.of()), RunnableConfig.builder().build());
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "waiting node did not start");
            assertTrue(generator.cancel(mayInterruptIfRunning));
            pending.complete(Map.of());
            awaitGraphExecution(executionThreads);

            assertTrue(generator.isCancelled());
            assertFalse(nextNodeRan.get(), "next node ran after cancellation");
            assertTrue(generator.next().isDone(), "cancelled stream returned buffered output");
            assertTrue(GraphResult.from(generator).isCancelled());
        } finally {
            pending.complete(Map.of());
            generator.cancel(true);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void parentCancellationStopsTheSubgraph(boolean mayInterruptIfRunning, boolean snapshots) throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var entered = new CountDownLatch(1);
        var pending = new CompletableFuture<Map<String, Object>>();
        var nextChildNodeRan = new AtomicBoolean();
        var nextParentNodeRan = new AtomicBoolean();

        var child = trackedGraph(executionThreads)
                .addNode("waiting", (state, config) -> {
                    entered.countDown();
                    return pending;
                })
                .addNode("child_next", (state, config) -> {
                    nextChildNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "waiting")
                .addEdge("waiting", "child_next")
                .addEdge("child_next", END)
                .compile();
        var graph = trackedGraph(executionThreads)
                .addNode("child", child)
                .addNode("parent_next", (state, config) -> {
                    nextParentNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "child")
                .addEdge("child", "parent_next")
                .addEdge("parent_next", END)
                .compile();

        var input = GraphInput.args(Map.of());
        var config = RunnableConfig.builder().build();
        var generator = snapshots ? graph.streamSnapshots(input, config) : graph.stream(input, config);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "child node did not start");
            assertTrue(generator.cancel(mayInterruptIfRunning));
            pending.complete(Map.of());
            awaitGraphExecution(executionThreads);

            assertFalse(nextChildNodeRan.get(), "child node ran after parent cancellation");
            assertFalse(nextParentNodeRan.get(), "parent node ran after cancellation");
            assertTrue(generator.next().isDone(), "cancelled stream returned buffered output");
            assertTrue(GraphResult.from(generator).isCancelled());
        } finally {
            pending.complete(Map.of());
            generator.cancel(true);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationStopsParallelSubgraphs(boolean mayInterruptIfRunning) throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var entered = new CountDownLatch(2);
        var pending = new CompletableFuture<Map<String, Object>>();
        var nextChildNodeRan = new AtomicBoolean();
        var nextParentNodeRan = new AtomicBoolean();

        var child = trackedGraph(executionThreads)
                .addNode("waiting", (state, config) -> {
                    entered.countDown();
                    return pending;
                })
                .addNode("child_next", (state, config) -> {
                    nextChildNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "waiting")
                .addEdge("waiting", "child_next")
                .addEdge("child_next", END)
                .compile();
        var graph = trackedGraph(executionThreads)
                .addNode("left", child)
                .addNode("right", child)
                .addNode("parent_next", (state, config) -> {
                    nextParentNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "left")
                .addEdge(START, "right")
                .addEdge("left", "parent_next")
                .addEdge("right", "parent_next")
                .addEdge("parent_next", END)
                .compile();
        var config = RunnableConfig.builder().addParallelNodeExecutor(START, ForkJoinPool.commonPool()).build();
        var generator = graph.stream(GraphInput.args(Map.of()), config);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "parallel child nodes did not start");
            assertTrue(generator.cancel(mayInterruptIfRunning));
            pending.complete(Map.of());
            awaitGraphExecution(executionThreads);

            assertFalse(nextChildNodeRan.get(), "parallel child node ran after cancellation");
            assertFalse(nextParentNodeRan.get(), "parent node ran after cancellation");
        } finally {
            pending.complete(Map.of());
            generator.cancel(true);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationCompletesTheAsyncConsumer(boolean mayInterruptIfRunning) throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var entered = new CountDownLatch(1);
        var consumed = new CountDownLatch(1);
        var pending = new CompletableFuture<Map<String, Object>>();
        var graph = trackedGraph(executionThreads)
                .addNode("waiting", (state, config) -> {
                    entered.countDown();
                    return pending;
                })
                .addEdge(START, "waiting")
                .addEdge("waiting", END)
                .compile();
        var generator = graph.stream(GraphInput.args(Map.of()), RunnableConfig.builder().build());
        try {
            var result = generator.forEachAsync(output -> consumed.countDown());
            assertTrue(entered.await(5, TimeUnit.SECONDS), "waiting node did not start");
            assertTrue(consumed.await(5, TimeUnit.SECONDS), "consumer did not start");
            assertTrue(generator.cancel(mayInterruptIfRunning));
            assertTrue(GraphResult.from(result.get(5, TimeUnit.SECONDS)).isCancelled());
        } finally {
            pending.complete(Map.of());
            generator.cancel(true);
            awaitGraphExecution(executionThreads);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationKeepsCheckpointsForResume(boolean mayInterruptIfRunning) throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var entered = new CountDownLatch(1);
        var pending = new CompletableFuture<Map<String, Object>>();
        var nextNodeRan = new AtomicBoolean();
        var saver = new MemorySaver();
        var graph = trackedGraph(executionThreads)
                .addNode("waiting", (state, config) -> {
                    entered.countDown();
                    return pending;
                })
                .addNode("next", (state, config) -> {
                    nextNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "waiting")
                .addEdge("waiting", "next")
                .addEdge("next", END)
                .compile(CompileConfig.builder().checkpointSaver(saver).build());
        var config = RunnableConfig.builder().threadId("cancelled-thread").build();
        var generator = graph.streamSnapshots(GraphInput.args(Map.of()), config);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "waiting node did not start");
            assertTrue(generator.cancel(mayInterruptIfRunning));
            pending.complete(Map.of());
            awaitGraphExecution(executionThreads);

            assertFalse(nextNodeRan.get(), "next node ran after cancellation");
            assertTrue(saver.get(config).isPresent(), "cancellation released the active checkpoints");
            assertTrue(saver.tag(config, null).isEmpty(), "cancellation tagged a normally completed thread");

            var resumed = graph.stream(GraphInput.resume(), config).forEachAsync(output -> {});
            assertTrue(GraphResult.from(resumed.get(5, TimeUnit.SECONDS)).isStateDataOrCheckpointSaverTag());
            assertTrue(nextNodeRan.get(), "resume did not run the next node");
        } finally {
            pending.complete(Map.of());
            generator.cancel(true);
        }
    }

    @Test
    void cancelledEmbeddedGeneratorDoesNotEvaluateTheNextEdge() throws Exception {
        var executionThreads = ConcurrentHashMap.<Thread>newKeySet();
        var embedded = AsyncGeneratorFlow.builder().<NodeOutput<AgentState>>build();
        embedded.cancel(false);
        var edgeEvaluated = new AtomicBoolean();
        var nextNodeRan = new AtomicBoolean();
        var graph = trackedGraph(executionThreads)
                .addNode("embedded", (state, config) -> completedFuture(Map.of("generator", embedded)))
                .addNode("next", (state, config) -> {
                    nextNodeRan.set(true);
                    return completedFuture(Map.of());
                })
                .addEdge(START, "embedded")
                .addConditionalEdges("embedded", edge_async(state -> {
                    edgeEvaluated.set(true);
                    return "next";
                }), Map.of("next", "next"))
                .addEdge("next", END)
                .compile();

        var generator = graph.stream(GraphInput.args(Map.of()), RunnableConfig.builder().build());
        try {
            var result = generator.forEachAsync(output -> {}).get(5, TimeUnit.SECONDS);
            assertTrue(GraphResult.from(result).isCancelled());
            awaitGraphExecution(executionThreads);
            assertFalse(edgeEvaluated.get(), "edge evaluated after the embedded generator was cancelled");
            assertFalse(nextNodeRan.get(), "next node ran after the embedded generator was cancelled");
        } finally {
            generator.cancel(true);
        }
    }
}
