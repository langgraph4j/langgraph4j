package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Retries a node action when a configured failure occurs.
 */
public final class RetryPolicy {

    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(500);

    private final int maxAttempts;
    private final Duration retryDelay;
    private final Predicate<Throwable> retryOn;

    private RetryPolicy(int maxAttempts, Duration retryDelay, Predicate<Throwable> retryOn) {
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.retryOn = retryOn;
    }

    @SafeVarargs
    public static RetryPolicy of(int maxAttempts, Class<? extends Throwable>... exceptionTypes) {
        return of(maxAttempts, DEFAULT_RETRY_DELAY, exceptionTypes);
    }

    @SafeVarargs
    public static RetryPolicy of(
            int maxAttempts,
            Duration retryDelay,
            Class<? extends Throwable>... exceptionTypes) {
        Objects.requireNonNull(exceptionTypes, "exceptionTypes cannot be null");
        if (exceptionTypes.length == 0) {
            throw new IllegalArgumentException("exceptionTypes cannot be empty");
        }

        var types = Arrays.copyOf(exceptionTypes, exceptionTypes.length);
        for (var type : types) {
            Objects.requireNonNull(type, "exceptionTypes cannot contain null");
        }

        return of(maxAttempts, retryDelay, error -> Arrays.stream(types).anyMatch(type -> type.isInstance(error)));
    }

    public static RetryPolicy of(int maxAttempts, Predicate<? super Throwable> retryOn) {
        return of(maxAttempts, DEFAULT_RETRY_DELAY, retryOn);
    }

    public static RetryPolicy of(
            int maxAttempts,
            Duration retryDelay,
            Predicate<? super Throwable> retryOn) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        Objects.requireNonNull(retryDelay, "retryDelay cannot be null");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay cannot be negative");
        }
        Objects.requireNonNull(retryOn, "retryOn cannot be null");

        return new RetryPolicy(maxAttempts, retryDelay, retryOn::test);
    }

    public <S extends AgentState> NodeHook.WrapCall<S> asHook() {
        return (nodeId, state, config, action) -> apply(action, state, config, 1);
    }

    private <S extends AgentState> CompletableFuture<Map<String, Object>> apply(
            AsyncNodeActionWithConfig<S> action,
            S state,
            RunnableConfig config,
            int attempt) {

        CompletableFuture<Map<String, Object>> futureResult;

        try {
            futureResult = action.apply(state, config);
        } catch (Throwable error) {
            futureResult = failedFuture(error);
        }

        return futureResult.handle((result, error) -> {
            if (error == null) {
                return completedFuture(result);
            }

            var cause = unwrap(error);
            if (attempt == maxAttempts || !retryOn.test(cause)) {
                return CompletableFuture.<Map<String, Object>>failedFuture(cause);
            }

            return delay().thenCompose(ignored -> apply(action, state, config, attempt + 1));
        })
                .thenCompose(Function.identity());
    }

    private static Throwable unwrap(Throwable error) {
        return (error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null
                        ? error.getCause()
                        : error;
    }

    private CompletableFuture<Void> delay() {
        return runAsync(() -> {
        }, delayedExecutor(retryDelay.toNanos(), NANOSECONDS));
    }
}
