package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.RetryPolicy;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class GraphExecutorTest {

    private static final String THREAD_PREFIX = "lg4j-test-executor-";
    private static final String CALLER_CONTEXT = "caller-context";
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private final AtomicInteger threadCount = new AtomicInteger();
    private final ExecutorService pool =
            Executors.newCachedThreadPool(r -> new Thread(r, THREAD_PREFIX + threadCount.incrementAndGet()));
    private final ExecutorService otherPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "other-pool"));
    private final List<String> seen = new CopyOnWriteArrayList<>();

    private final Executor executor = task -> {
        final var captured = CONTEXT.get();
        pool.execute(() -> {
            CONTEXT.set(captured);
            try {
                task.run();
            } finally {
                CONTEXT.remove();
            }
        });
    };

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
        otherPool.shutdownNow();
        CONTEXT.remove();
    }

    private Map<String, Object> record(String step) {
        seen.add(step + "@" + Thread.currentThread().getName() + "|" + CONTEXT.get());
        return Map.of();
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private AsyncNodeAction<AgentState> recordingNode(String step) {
        return node_async(state -> record(step));
    }

    private StateGraph<AgentState> recordingGraph() throws Exception {
        return new StateGraph<>(AgentState::new)
                .addNode("n1", recordingNode("n1"))
                .addEdge(START, "n1")
                .addEdge("n1", END);
    }

    private RunnableConfig.Builder configWithExecutor() {
        return RunnableConfig.builder().executor(executor);
    }

    private void assertAllRanOnExecutorWithCallerContext(List<String> expectedSteps) {
        assertFalse(seen.isEmpty(), "no node ran");
        assertEquals(expectedSteps, seen.stream().map(entry -> entry.substring(0, entry.indexOf('@'))).toList());
        seen.forEach(entry -> {
            assertTrue(entry.contains("@" + THREAD_PREFIX), entry);
            assertTrue(entry.endsWith("|" + CALLER_CONTEXT), entry);
        });
    }

    @Test
    void streamRunsTheGraphOnTheSuppliedExecutor() throws Exception {
        CONTEXT.set(CALLER_CONTEXT);

        recordingGraph().compile()
                .stream(GraphInput.noArgs(), configWithExecutor().build())
                .stream()
                .toList();

        assertAllRanOnExecutorWithCallerContext(List.of("n1"));
    }

    @Test
    void streamSnapshotsRunsTheGraphOnTheSuppliedExecutor() throws Exception {
        CONTEXT.set(CALLER_CONTEXT);

        recordingGraph().compile()
                .streamSnapshots(GraphInput.noArgs(), configWithExecutor().build())
                .stream()
                .toList();

        assertAllRanOnExecutorWithCallerContext(List.of("n1"));
    }

    @Test
    void hooksAndEdgesAfterAnAsyncNodeRunOnTheExecutor() throws Exception {
        // Completes after the loop has chained onto it, so without awaiting it the continuation would run on other-pool.
        AsyncNodeAction<AgentState> onOtherPool = state -> CompletableFuture.supplyAsync(() -> {
            sleepMillis(100);
            return Map.of();
        }, otherPool);
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", onOtherPool)
                .addNode("n2", recordingNode("n2"))
                .addAfterCallNodeHook("n1", (nodeId, state, config, result) -> completedFuture(record("after")))
                .addEdge(START, "n1")
                .addConditionalEdges("n1", edge_async(state -> {
                    record("edge");
                    return "n2";
                }), Map.of("n2", "n2"))
                .addEdge("n2", END)
                .compile();

        CONTEXT.set(CALLER_CONTEXT);
        graph.invoke(GraphInput.noArgs(), configWithExecutor().build());

        assertAllRanOnExecutorWithCallerContext(List.of("after", "edge", "n2"));
    }

    @Test
    void retriedAttemptsRunOnTheExecutor() throws Exception {
        var attempts = new AtomicInteger();
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", node_async(state -> {
                    record("attempt" + attempts.incrementAndGet());
                    if (attempts.get() == 1) {
                        throw new IOException("transient");
                    }
                    return Map.of();
                }))
                .addWrapCallNodeHook("n1", RetryPolicy.builder()
                        .retryDelay(Duration.ofMillis(10))
                        .jitter(false)
                        .retryOn(IOException.class)
                        .build()
                        .asHook())
                .addEdge(START, "n1")
                .addEdge("n1", END)
                .compile();

        CONTEXT.set(CALLER_CONTEXT);
        graph.invoke(GraphInput.noArgs(), configWithExecutor().build());

        assertAllRanOnExecutorWithCallerContext(List.of("attempt1", "attempt2"));
    }

    @Test
    void retriesOfAnAsyncNodeFailingOnItsOwnPoolKeepTheCallerContext() throws Exception {
        var attempts = new AtomicInteger();
        AsyncNodeAction<AgentState> failsOnceOnOtherPool = state -> {
            final var attempt = attempts.incrementAndGet();
            record("attempt" + attempt);
            return attempt == 1
                    ? CompletableFuture.supplyAsync(() -> { throw new CompletionException(new IOException("transient")); }, otherPool)
                    : completedFuture(Map.of());
        };
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", failsOnceOnOtherPool)
                .addWrapCallNodeHook("n1", RetryPolicy.builder()
                        .retryDelay(Duration.ofMillis(10))
                        .jitter(false)
                        .retryOn(IOException.class)
                        .build()
                        .asHook())
                .addEdge(START, "n1")
                .addEdge("n1", END)
                .compile();

        CONTEXT.set(CALLER_CONTEXT);
        graph.invoke(GraphInput.noArgs(), configWithExecutor().build());

        assertAllRanOnExecutorWithCallerContext(List.of("attempt1", "attempt2"));
    }

    @Test
    void retryBackOffLetsAForkJoinPoolExecutorRunOtherWork() throws Exception {
        var forkJoinPool = new ForkJoinPool(1);
        var firstAttemptFailed = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", node_async(state -> {
                    if (attempts.incrementAndGet() == 1) {
                        firstAttemptFailed.countDown();
                        throw new IOException("transient");
                    }
                    return Map.of();
                }))
                .addWrapCallNodeHook("n1", RetryPolicy.builder()
                        .retryDelay(Duration.ofSeconds(2))
                        .jitter(false)
                        .retryOn(IOException.class)
                        .build()
                        .asHook())
                .addEdge(START, "n1")
                .addEdge("n1", END)
                .compile();
        try {
            var run = CompletableFuture.runAsync(() ->
                    graph.invoke(GraphInput.noArgs(), RunnableConfig.builder().executor(forkJoinPool).build()));
            assertTrue(firstAttemptFailed.await(5, TimeUnit.SECONDS));

            CompletableFuture.runAsync(() -> {}, forkJoinPool).get(1, TimeUnit.SECONDS);

            run.get(5, TimeUnit.SECONDS);
        } finally {
            forkJoinPool.shutdownNow();
        }
    }

    @Test
    void resumedRunsUseTheSuppliedExecutor() throws Exception {
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", recordingNode("n1"))
                .addNode("n2", recordingNode("n2"))
                .addEdge(START, "n1")
                .addEdge("n1", "n2")
                .addEdge("n2", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .interruptAfter("n1")
                        .build());
        var config = configWithExecutor().threadId("resume").build();

        CONTEXT.set(CALLER_CONTEXT);
        graph.stream(GraphInput.noArgs(), config).stream().toList();
        graph.stream(GraphInput.resume(), config).stream().toList();

        assertAllRanOnExecutorWithCallerContext(List.of("n1", "n2"));
    }

    @Test
    void compiledSubgraphsInheritTheExecutor() throws Exception {
        var parent = new StateGraph<>(AgentState::new)
                .addNode("sub", recordingGraph().compile())
                .addEdge(START, "sub")
                .addEdge("sub", END)
                .compile();

        CONTEXT.set(CALLER_CONTEXT);
        parent.invoke(GraphInput.noArgs(), configWithExecutor().build());

        assertAllRanOnExecutorWithCallerContext(List.of("n1"));
    }
}
