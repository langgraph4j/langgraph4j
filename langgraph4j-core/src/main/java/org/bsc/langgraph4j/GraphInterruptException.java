package org.bsc.langgraph4j;


/**
 * Exception thrown when the execution of a graph is interrupted.
 *
 * @since 1.9.0-beta4
 */
public class GraphInterruptException extends GraphRunnerException {

    public GraphInterruptException(RunnableConfig config, String reason) {
        super(config, reason);
    }

}
