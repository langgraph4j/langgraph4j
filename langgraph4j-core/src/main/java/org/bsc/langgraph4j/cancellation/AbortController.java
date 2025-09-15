package org.bsc.langgraph4j.cancellation;

/**
 * Controls cancellation of graph execution through cooperative cancellation.
 * Users can check cancellation status in their node actions and throw
 * GraphCancellationException to stop execution cleanly.
 */
public interface AbortController {

    /**
     * Check if cancellation has been requested.
     *
     * @return true if cancelled, false otherwise
     */
    boolean isCancelled();

    /**
     * Request cancellation of the current graph execution.
     * This does not immediately stop execution but signals that
     * nodes should check isCancelled() and exit cleanly.
     */
    void cancel();

    /**
     * Throw GraphCancellationException if cancellation has been requested.
     * This is a convenience method for nodes to check cancellation status.
     *
     * @throws GraphCancellationException if cancelled
     */
    void throwIfCancelled() throws GraphCancellationException;

    /**
     * Returns a no-op AbortController for backward compatibility.
     * This controller is never cancelled and does nothing when cancel() is called.
     *
     * @return a non-cancellable AbortController
     */
    static AbortController noop() {
        return NoOpAbortController.INSTANCE;
    }

    /**
     * Creates a new cancellable AbortController.
     *
     * @return a new AbortController that can be cancelled
     */
    static AbortController create() {
        return new DefaultAbortController();
    }
}

/**
 * No-operation implementation for backward compatibility.
 */
final class NoOpAbortController implements AbortController {
    static final NoOpAbortController INSTANCE = new NoOpAbortController();

    private NoOpAbortController() {}

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void cancel() {
        // No-op
    }

    @Override
    public void throwIfCancelled() {
        // No-op
    }

    @Override
    public String toString() {
        return "NoOpAbortController";
    }
}