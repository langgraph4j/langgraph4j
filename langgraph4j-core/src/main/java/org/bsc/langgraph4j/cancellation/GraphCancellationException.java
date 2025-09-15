package org.bsc.langgraph4j.cancellation;

/**
 * Exception thrown when graph execution is cancelled through the AbortController.
 * This exception should be caught and handled gracefully by the graph execution engine.
 *
 * Unlike InterruptedException, this exception indicates user-requested cancellation
 * rather than thread interruption.
 */
public class GraphCancellationException extends RuntimeException {

    /**
     * Creates a new GraphCancellationException with the specified message.
     *
     * @param message the detail message explaining the cancellation
     */
    public GraphCancellationException(String message) {
        super(message);
    }

    /**
     * Creates a new GraphCancellationException with the specified message and cause.
     *
     * @param message the detail message explaining the cancellation
     * @param cause the underlying cause of the cancellation
     */
    public GraphCancellationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new GraphCancellationException with a default message.
     */
    public GraphCancellationException() {
        this("Graph execution was cancelled");
    }

    /**
     * Creates a new GraphCancellationException with the specified cause.
     *
     * @param cause the underlying cause of the cancellation
     */
    public GraphCancellationException(Throwable cause) {
        this("Graph execution was cancelled", cause);
    }
}