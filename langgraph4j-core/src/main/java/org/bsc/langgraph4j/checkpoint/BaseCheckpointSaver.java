package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public interface BaseCheckpointSaver {
    String THREAD_ID_DEFAULT = "$default";

    final class Tag  {
        private final String threadId;
        private final Integer version;
        private final List<Checkpoint> checkpoints;

        public Tag(String threadId, @Nullable Integer version, @Nullable Collection<Checkpoint> checkpoints) {
            this.threadId = requireNonNull(threadId, "threadId cannot be null");
            this.checkpoints = ofNullable(checkpoints).map(List::copyOf).orElseGet(List::of);
            this.version = version;
        }
        public Tag(String threadId, Collection<Checkpoint> checkpoints) {
            this(threadId, null, checkpoints );
        }

        public Collection<Checkpoint> checkpoints() {
            return checkpoints;
        }

        public Optional<Checkpoint> lastCheckpoint() {
            return checkpoints.stream().findFirst();
        }

        public String threadId() {
            return threadId;
        }

        public Optional<Integer> version() {
            return ofNullable(version);
        }
    }

    record SubGraphSaver( String threadId, BaseCheckpointSaver saver ) {
        public SubGraphSaver {
            requireNonNull(threadId, "threadId cannot be null");
            requireNonNull(saver, "saver cannot be null");
        }
    }

    Collection<Checkpoint> list(RunnableConfig config);

    Optional<Checkpoint> get(RunnableConfig config);

    RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception;

    Tag release(RunnableConfig config) throws Exception;

    /**
     * Release the checkpoints for the given config and return a Tag object that contains
     * the threadId, version, and list of checkpoints.
     * this is used internally and should not be called directly by the user.
     *
     * @param config the RunnableConfig for which to release checkpoints
     * @param message custom message to include in the Tag object (can be null)
     * @return a Tag object containing the threadId, version, and list of checkpoints
     * @throws Exception if an error occurs while releasing the checkpoints
     * @since 1.9.0-beta3
     */
    Tag release(RunnableConfig config, @Nullable String message) throws Exception;

    /**
     * Release the checkpoints for the given config when an exception occurs and return a Tag object that contains
     * the threadId, version, and list of checkpoints.
     * @param config the RunnableConfig for which to release checkpoints
     * @param exception the exception that caused the release (cannot be null)
     * @return a Tag object containing the threadId, version, and list of checkpoints
     * @throws Exception if an error occurs while releasing the checkpoints
     * @since 1.9.0-beta3
     */
    Tag releaseOnError(RunnableConfig config, Exception exception) throws Exception;

    /**
     * Register Interrupt of the execution for the given config and provide interruption metadata.
     * this is used internally and should not be called directly by the user.
     *
     * @param config the RunnableConfig for which to register the interruption
     * @param interruptionMetadata the metadata associated with the interruption
     * @since 1.9.0-beta3
     */
    <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config,
                                                                                                   InterruptionMetadata<State> interruptionMetadata);

    Optional<Tag> tag( RunnableConfig config, @Nullable Integer version ) throws Exception;

    default Optional<Tag> lastTag(  RunnableConfig config ) throws Exception {
        return tag( config, null );
    }

    default String threadId( RunnableConfig config ) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    /**
     * Put a sub-graph saver for the given parent and its configurations.
     * @param parentConfig the parent configuration
     * @param subGraphConfig the sub-graph configuration
     * @param subGraphSaver the sub-graph saver
     * @since 1.9.0-beta1
     */
    void putSubGraphSaver( RunnableConfig parentConfig, RunnableConfig subGraphConfig, BaseCheckpointSaver subGraphSaver );

    /**
     * List all sub-graph savers for the given parent configuration.
     * @param parentConfig the parent configuration
     * @return a collection of sub-graph savers
     * @since 1.9.0-beta1
     */
    Collection<SubGraphSaver> listSubGraphSaver( RunnableConfig parentConfig );

}
