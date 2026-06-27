package org.bsc.langgraph4j;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Exception thrown when there is an error during the execution of a graph runner.
 */
public class GraphRunnerException extends Exception {

    private final RunnableConfig config;

    public GraphRunnerException(RunnableConfig config, String errorMessage ) {
        super(errorMessage);
        this.config = requireNonNull(config, "config cannot be null");

    }

    public GraphRunnerException(RunnableConfig config, Throwable cause ) {
        super(cause);
        this.config = requireNonNull(config, "config cannot be null");
    }

    public RunnableConfig config() {
        return config;
    }

    /**
     * Returns the identifier of the node that was being executed when the error occurred.
     * <p>
     * The value is resolved from the {@link RunnableConfig} carried by this exception
     * (metadata key {@link RunnableConfig#NODE_ID}). Unlike {@link RunnableConfig#nodeId()},
     * which throws when no node id is present, this accessor returns an empty {@link Optional}
     * when the failure happens outside the scope of a specific node (for example, during
     * entry-point resolution), so it is safe to call while handling a failure.
     *
     * @return an {@link Optional} containing the failing node id, or empty if it cannot be determined
     * @since 1.9.0
     */
    public Optional<String> nodeId() {
        return config.metadata(RunnableConfig.NODE_ID).map(Object::toString);
    }
}
