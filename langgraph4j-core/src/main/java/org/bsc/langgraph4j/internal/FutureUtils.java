package org.bsc.langgraph4j.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Internal helpers for the graph runtime; not part of the public API.
 */
public final class FutureUtils {

    private FutureUtils() {}

    /**
     * Blocks until the future completes, without throwing, so stages chained on it run on this
     * thread rather than on whichever thread completed it. If interrupted, returns the future still
     * pending with the interrupt flag set.
     */
    public static <T> CompletableFuture<T> awaitCompletion(CompletableFuture<T> future) {
        try {
            future.handle((result, error) -> null).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException impossible) {
            throw new IllegalStateException(impossible);
        }
        return future;
    }
}
