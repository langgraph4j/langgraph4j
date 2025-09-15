package org.bsc.langgraph4j.cancellation;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;

/**
 * Container for a stream and its associated abort controller.
 */
public class StreamWithController<State extends AgentState> {
    private final AsyncGenerator<NodeOutput<State>> stream;
    private final AbortController abortController;

    public StreamWithController(AsyncGenerator<NodeOutput<State>> stream, AbortController abortController) {
        this.stream = stream;
        this.abortController = abortController;
    }

    public AsyncGenerator<NodeOutput<State>> getStream() {
        return stream;
    }

    public AbortController getAbortController() {
        return abortController;
    }

    /**
     * Cancel the stream execution.
     */
    public void cancel() {
        abortController.cancel();
    }

    /**
     * Cancel with a specific reason.
     */
    public void cancel(String reason) {
        if (abortController instanceof DefaultAbortController) {
            ((DefaultAbortController) abortController).cancel(reason);
        } else {
            abortController.cancel();
        }
    }

    /**
     * Check if the stream is cancelled.
     */
    public boolean isCancelled() {
        return abortController.isCancelled();
    }
}