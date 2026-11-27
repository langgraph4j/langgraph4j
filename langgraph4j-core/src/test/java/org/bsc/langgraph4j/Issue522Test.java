package org.bsc.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test for issue <a href="https://github.com/langgraph4j/langgraph4j/issues/522">#522</a>
 */
@Timeout(10)
public class Issue522Test {

    private final ExecutorService callerPool =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "caller-pool"));

    @AfterEach
    void shutdown() {
        callerPool.shutdownNow();
    }

    private CompiledGraph<AgentState> graphCapturing(AtomicReference<Thread> runningThread) throws Exception {
        return new StateGraph<>(AgentState::new)
                .addNode("answer", node_async(state -> {
                    runningThread.set(Thread.currentThread());
                    return Map.of("answer", 42);
                }))
                .addEdge(START, "answer")
                .addEdge("answer", END)
                .compile();
    }

    @Test
    void defaultExecutorRunsOnDaemonThreads() throws Exception {
        var runningThread = new AtomicReference<Thread>();

        var answer = graphCapturing(runningThread)
                .invoke(GraphInput.noArgs(), RunnableConfig.empty())
                .orElseThrow()
                .<Integer>value("answer")
                .orElseThrow();

        assertEquals(42, answer);
        var thread = runningThread.get();
        assertNotNull(thread, "the node did not run");
        assertTrue(thread.getName().startsWith(CompiledGraph.DEFAULT_EMITTER_THREAD_PREFIX), thread.getName());
        assertTrue(thread.isDaemon(), "a non-daemon default emitter thread keeps the JVM alive until it is reaped");
    }

    @Test
    void suppliedExecutorStillWinsOverTheDefault() throws Exception {
        var runningThread = new AtomicReference<Thread>();

        graphCapturing(runningThread)
                .invoke(GraphInput.noArgs(), RunnableConfig.builder().emitterExecutor(callerPool).build());

        assertEquals("caller-pool", runningThread.get().getName());
    }
}
