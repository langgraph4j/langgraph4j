package org.bsc.langgraph4j;

import org.bsc.langgraph4j.utils.ExceptionUtils;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when there is an error during the execution of a graph runner.
 */
public class GraphRunnerException extends Exception {

    public static Optional<? extends GraphRunnerException> of(Throwable throwable) {
        return ExceptionUtils.findCauseByType(throwable, GraphRunnerException.class);
    }

    private final RunnableConfig config;

    public GraphRunnerException(RunnableConfig config, String errorMessage ) {
        super(errorMessage);
        this.config = requireNonNull(config, "config cannot be null");

    }

    public GraphRunnerException(RunnableConfig config, Throwable cause ) {
        super(cause);
        this.config = requireNonNull(config, "config cannot be null");;
    }

    public RunnableConfig config() {
        return config;
    }

}