package org.bsc.langgraph4j;

import org.bsc.langgraph4j.utils.ExceptionUtils;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when there is an error during the execution of a graph runner.
 */
public class GraphRunException extends Exception {

    public static Optional<? extends GraphRunException> of(Throwable throwable) {
        return ExceptionUtils.findCauseByType(throwable, GraphRunException.class);
    }

    private final RunnableConfig config;

    public GraphRunException(RunnableConfig config, String errorMessage ) {
        super(errorMessage);
        this.config = requireNonNull(config, "config cannot be null");

    }

    public GraphRunException(RunnableConfig config, Throwable cause ) {
        super(cause);
        this.config = requireNonNull(config, "config cannot be null");;
    }

    public RunnableConfig config() {
        return config;
    }

}