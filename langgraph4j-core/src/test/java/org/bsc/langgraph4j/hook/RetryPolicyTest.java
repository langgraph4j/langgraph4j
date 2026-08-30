package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyTest {

    static class State extends AgentState {
        State(Map<String, Object> data) {
            super(data);
        }
    }

    @Test
    void retriesRetryableFailureUntilSuccess() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            if (attempts.incrementAndGet() < 3) {
                return failedFuture(new IOException("temporary failure"));
            }

            return completedFuture(Map.of("result", "done"));
        };

        NodeHook.WrapCall<State> hook = RetryPolicy.of(3, IOException.class).asHook();

        var future = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        assertEquals(3, attempts.get());
        assertEquals("done", future.join().get("result"));
    }

    @Test
    void retriesSynchronousRetryableFailureUntilSuccess() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary failure");
            }

            return completedFuture(Map.of("result", "done"));
        };

        NodeHook.WrapCall<State> hook = RetryPolicy.of(3, IllegalStateException.class).asHook();

        var result = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        assertEquals(3, attempts.get());
        assertEquals("done", result.join().get("result"));
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            attempts.incrementAndGet();
            return failedFuture(new IOException("temporary failure"));
        };

        NodeHook.WrapCall<State> hook = RetryPolicy.of(3, IllegalStateException.class).asHook();

        var future = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        var error = assertThrows(CompletionException.class, future::join);

        assertEquals(1, attempts.get());
        assertInstanceOf(IOException.class, error.getCause());
    }

    @Test
    void stopsAfterMaxAttempts() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            attempts.incrementAndGet();
            return failedFuture(new IOException("temporary failure"));
        };

        NodeHook.WrapCall<State> hook = RetryPolicy.of(3, IOException.class).asHook();

        var future = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        assertThrows(CompletionException.class, future::join);
        assertEquals(3, attempts.get());
    }
}
