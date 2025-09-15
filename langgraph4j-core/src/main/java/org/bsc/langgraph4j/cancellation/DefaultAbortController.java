package org.bsc.langgraph4j.cancellation;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe implementation of AbortController using atomic operations.
 * This implementation is safe for concurrent access from multiple threads.
 */
public class DefaultAbortController implements AbortController {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String cancellationReason;

    /**
     * Creates a new DefaultAbortController in non-cancelled state.
     */
    public DefaultAbortController() {
        this.cancellationReason = null;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        cancel("Graph execution was cancelled");
    }

    /**
     * Cancel with a specific reason.
     *
     * @param reason the reason for cancellation
     */
    public void cancel(String reason) {
        if (cancelled.compareAndSet(false, true)) {
            this.cancellationReason = reason != null ? reason : "Graph execution was cancelled";
        }
    }

    @Override
    public void throwIfCancelled() throws GraphCancellationException {
        if (cancelled.get()) {
            throw new GraphCancellationException(
                    cancellationReason != null ? cancellationReason : "Graph execution was cancelled"
            );
        }
    }

    /**
     * Get the cancellation reason if cancelled.
     *
     * @return the cancellation reason, or null if not cancelled
     */
    public String getCancellationReason() {
        return cancelled.get() ? cancellationReason : null;
    }

    @Override
    public String toString() {
        return "DefaultAbortController{" +
                "cancelled=" + cancelled.get() +
                ", reason='" + cancellationReason + '\'' +
                '}';
    }
}