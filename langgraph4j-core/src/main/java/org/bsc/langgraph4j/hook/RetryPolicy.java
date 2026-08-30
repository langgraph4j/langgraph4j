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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Retries a node action when a configured failure occurs.
 */
public final class RetryPolicy {

    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(500);
    private static final double DEFAULT_BACKOFF_FACTOR = 2.0;
    private static final Duration DEFAULT_MAX_INTERVAL = Duration.ofSeconds(128);

    private final int maxAttempts;
    private final Duration retryDelay;
    private final double backoffFactor;
    private final Duration maxInterval;
    private final boolean jitter;
    private final Predicate<Throwable> retryOn;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.retryDelay = builder.retryDelay;
        this.backoffFactor = builder.backoffFactor;
        this.maxInterval = builder.maxInterval;
        this.jitter = builder.jitter;
        this.retryOn = builder.retryOn;
    }

    public static Builder builder() {
        return new Builder();
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

            return delay(attempt).thenCompose(ignored -> apply(action, state, config, attempt + 1));
        })
                .thenCompose(Function.identity());
    }

    private static Throwable unwrap(Throwable error) {
        return (error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null
                        ? error.getCause()
                        : error;
    }

    private CompletableFuture<Void> delay(int attempt) {
        return runAsync(() -> {
        }, delayedExecutor(delayNanos(attempt), NANOSECONDS));
    }

    long delayNanos(int attempt) {
        var baseDelayNanos = Math.min(
                retryDelay.toNanos() * Math.pow(backoffFactor, attempt - 1.0),
                maxInterval.toNanos());

        var jitterDelay = 0.0;
        if (jitter && baseDelayNanos > 0) {
            jitterDelay = ThreadLocalRandom.current().nextDouble(baseDelayNanos);
        }

        return (long) (baseDelayNanos + jitterDelay);
    }

    public static final class Builder {

        private int maxAttempts = 3;
        private Duration retryDelay = DEFAULT_RETRY_DELAY;
        private double backoffFactor = DEFAULT_BACKOFF_FACTOR;
        private Duration maxInterval = DEFAULT_MAX_INTERVAL;
        private boolean jitter = true;
        private Predicate<Throwable> retryOn;

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be greater than zero");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder retryDelay(Duration retryDelay) {
            Objects.requireNonNull(retryDelay, "retryDelay cannot be null");
            if (retryDelay.isNegative()) {
                throw new IllegalArgumentException("retryDelay cannot be negative");
            }
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder backoffFactor(double backoffFactor) {
            if (!Double.isFinite(backoffFactor) || backoffFactor < 1) {
                throw new IllegalArgumentException("backoffFactor must be finite and at least one");
            }
            this.backoffFactor = backoffFactor;
            return this;
        }

        public Builder maxInterval(Duration maxInterval) {
            Objects.requireNonNull(maxInterval, "maxInterval cannot be null");
            if (maxInterval.isNegative()) {
                throw new IllegalArgumentException("maxInterval cannot be negative");
            }
            this.maxInterval = maxInterval;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        @SafeVarargs
        public final Builder retryOn(Class<? extends Throwable>... exceptionTypes) {
            Objects.requireNonNull(exceptionTypes, "exceptionTypes cannot be null");
            if (exceptionTypes.length == 0) {
                throw new IllegalArgumentException("exceptionTypes cannot be empty");
            }

            var types = Arrays.copyOf(exceptionTypes, exceptionTypes.length);
            for (var type : types) {
                Objects.requireNonNull(type, "exceptionTypes cannot contain null");
            }

            return retryOn(error -> Arrays.stream(types).anyMatch(type -> type.isInstance(error)));
        }

        public Builder retryOn(Predicate<? super Throwable> condition) {
            Objects.requireNonNull(condition, "condition cannot be null");
            Predicate<Throwable> predicate = condition::test;
            retryOn = retryOn == null ? predicate : retryOn.or(predicate);
            return this;
        }

        public RetryPolicy build() {
            if (retryOn == null) {
                throw new IllegalStateException("retryOn must be configured");
            }
            return new RetryPolicy(this);
        }
    }
}
