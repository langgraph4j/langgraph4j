package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .retryOn(IOException.class)
                .build()
                .asHook();

        var future = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        var result = future.join();

        assertEquals(3, attempts.get());
        assertEquals("done", result.get("result"));
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

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .retryOn(IllegalStateException.class)
                .build()
                .asHook();

        var result = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        var output = result.join();

        assertEquals(3, attempts.get());
        assertEquals("done", output.get("result"));
    }

    @Test
    void delaysRetry() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> attempts.incrementAndGet() == 1
                ? failedFuture(new IOException("temporary failure"))
                : completedFuture(Map.of("result", "done"));

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .maxAttempts(2)
                .retryOn(IOException.class)
                .build()
                .asHook();

        var start = System.nanoTime();
        var result = hook.applyWrap("call_api", new State(Map.of()), RunnableConfig.empty(), action).join();
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertEquals(2, attempts.get());
        assertEquals("done", result.get("result"));
        assertTrue(elapsed.compareTo(Duration.ofMillis(450)) >= 0);
    }

    @Test
    void increasesDelayForEachRetry() {
        var attempts = new AtomicInteger();
        AsyncNodeActionWithConfig<State> action = (state, config) -> attempts.incrementAndGet() < 3
                ? failedFuture(new IOException("temporary failure"))
                : completedFuture(Map.of("result", "done"));

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .retryDelay(Duration.ofMillis(50))
                .backoffFactor(2.0)
                .retryOn(IOException.class)
                .build()
                .asHook();

        var start = System.nanoTime();
        var result = hook.applyWrap("call_api", new State(Map.of()), RunnableConfig.empty(), action).join();
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertEquals(3, attempts.get());
        assertEquals("done", result.get("result"));
        assertTrue(elapsed.compareTo(Duration.ofMillis(125)) >= 0);
    }

    @Test
    void capsBackoffDelayAtMaxInterval() {
        var retry = RetryPolicy.builder()
                .retryDelay(Duration.ofMillis(50))
                .backoffFactor(2.0)
                .maxInterval(Duration.ofMillis(75))
                .jitter(false)
                .retryOn(IOException.class)
                .build();

        assertEquals(Duration.ofMillis(50).toNanos(), retry.delayNanos(1));
        assertEquals(Duration.ofMillis(75).toNanos(), retry.delayNanos(2));
        assertEquals(Duration.ofMillis(75).toNanos(), retry.delayNanos(3));
    }

    @Test
    void addsJitterAfterCappingDelay() {
        var retry = RetryPolicy.builder()
                .retryDelay(Duration.ofMillis(50))
                .maxInterval(Duration.ofMillis(75))
                .retryOn(IOException.class)
                .build();

        var delay = retry.delayNanos(2);

        assertTrue(delay >= Duration.ofMillis(75).toNanos());
        assertTrue(delay < Duration.ofMillis(150).toNanos());
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        var attempts = new AtomicInteger();

        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            attempts.incrementAndGet();
            return failedFuture(new IOException("temporary failure"));
        };

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .retryOn(IllegalStateException.class)
                .build()
                .asHook();

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

        NodeHook.WrapCall<State> hook = RetryPolicy.builder()
                .retryOn(IOException.class)
                .build()
                .asHook();

        var future = hook.applyWrap(
                "call_api",
                new State(Map.of()),
                RunnableConfig.empty(),
                action);

        assertThrows(CompletionException.class, future::join);
        assertEquals(3, attempts.get());
    }
}
