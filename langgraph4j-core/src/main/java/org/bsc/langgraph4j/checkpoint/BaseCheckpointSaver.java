package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.jspecify.annotations.Nullable;

import java.util.*;

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

    Optional<Tag> tag( RunnableConfig config, @Nullable Integer version ) throws Exception;


    default Optional<Tag> lastTag(  RunnableConfig config ) throws Exception {
        return tag( config, null );
    }

    default String threadId( RunnableConfig config ) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    void putSubGraphSaver( RunnableConfig parentConfig, RunnableConfig subGraphConfig, BaseCheckpointSaver subGraphSaver );

    Collection<SubGraphSaver> listSubGraphSaver( RunnableConfig parentConfig );

}
