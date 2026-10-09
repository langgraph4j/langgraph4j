package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.utils.ExceptionUtils.getRootCause;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class NodeContinuationThreadTest {

    private static final String OTHER_POOL = "other-pool";

    private final ExecutorService otherPool = Executors.newSingleThreadExecutor(r -> new Thread(r, OTHER_POOL));
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @AfterEach
    void shutdown() {
        otherPool.shutdownNow();
    }

    private Map<String, Object> record(String step) {
        seen.add(step + "@" + Thread.currentThread().getName());
        return Map.of();
    }

    private String threadOf(String step) {
        return seen.stream()
                .filter(entry -> entry.startsWith(step + "@"))
                .map(entry -> entry.substring(entry.indexOf('@') + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError(step + " did not run: " + seen));
    }

    // Completes after the loop has chained onto it, so without awaiting it the continuation would run on other-pool.
    private CompletableFuture<Map<String, Object>> slowlyOnOtherPool(Map<String, Object> result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result;
        }, otherPool);
    }

    @Test
    void hooksAndEdgesAfterAnAsyncNodeRunOnTheLoopThread() throws Exception {
        AsyncNodeAction<AgentState> onOtherPool = state -> slowlyOnOtherPool(Map.of());
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", onOtherPool)
                .addNode("n2", node_async(state -> record("n2")))
                .addAfterCallNodeHook("n1", (nodeId, state, config, result) -> completedFuture(record("after")))
                .addEdge(START, "n1")
                .addConditionalEdges("n1", edge_async(state -> {
                    record("edge");
                    return "n2";
                }), Map.of("n2", "n2"))
                .addEdge("n2", END)
                .compile();

        graph.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        final var loopThread = threadOf("n2");
        assertNotEquals(OTHER_POOL, loopThread);
        assertEquals(loopThread, threadOf("after"));
        assertEquals(loopThread, threadOf("edge"));
    }

    @Test
    void retriesOfAnAsyncNodeAreInvokedOnTheLoopThread() throws Exception {
        var attempts = new AtomicInteger();
        AsyncNodeAction<AgentState> failsOnceOnOtherPool = state -> {
            final var attempt = attempts.incrementAndGet();
            record("attempt" + attempt);
            return attempt == 1
                    ? CompletableFuture.supplyAsync(() -> { throw new CompletionException(new IOException("transient")); }, otherPool)
                    : slowlyOnOtherPool(Map.of());
        };
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", failsOnceOnOtherPool)
                .addNode("n2", node_async(state -> record("n2")))
                .addWrapCallNodeHook("n1", RetryPolicy.builder()
                        .retryDelay(Duration.ofMillis(10))
                        .jitter(false)
                        .retryOn(IOException.class)
                        .build()
                        .asHook())
                .addEdge(START, "n1")
                .addEdge("n1", "n2")
                .addEdge("n2", END)
                .compile();

        graph.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        final var loopThread = threadOf("n2");
        assertEquals(loopThread, threadOf("attempt1"));
        assertEquals(loopThread, threadOf("attempt2"));
    }

    @Test
    void interruptingTheLoopThreadEndsARunWaitingOnANode() throws Exception {
        var loopThread = new AtomicReference<Thread>();
        AsyncNodeAction<AgentState> neverCompletes = state -> {
            loopThread.set(Thread.currentThread());
            return new CompletableFuture<>();
        };
        var graph = new StateGraph<>(AgentState::new)
                .addNode("n1", neverCompletes)
                .addEdge(START, "n1")
                .addEdge("n1", END)
                .compile();

        var generator = graph.stream(GraphInput.noArgs(), RunnableConfig.empty());
        var run = CompletableFuture.runAsync(() -> generator.stream().toList(), otherPool);
        while (loopThread.get() == null) {
            Thread.onSpinWait();
        }
        loopThread.get().interrupt();

        var failure = assertThrows(Exception.class, () -> run.get(5, TimeUnit.SECONDS));
        assertInstanceOf(InterruptedException.class, getRootCause(failure));
    }
}
