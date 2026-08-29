package org.bsc.langgraph4j;


import org.bsc.langgraph4j.utils.ExceptionUtils;

import java.util.Optional;

/**
 * Exception thrown when the execution of a graph is interrupted.
 *
 * @since 1.9.0-beta4
 */
public class GraphInterruptException extends GraphRunnerException {

    public static Optional<GraphInterruptException> of(Throwable throwable) {
        return ExceptionUtils.findCauseByType(throwable, GraphInterruptException.class);
    }

    public GraphInterruptException(RunnableConfig config, String reason) {
        super(config, reason);
    }

}
