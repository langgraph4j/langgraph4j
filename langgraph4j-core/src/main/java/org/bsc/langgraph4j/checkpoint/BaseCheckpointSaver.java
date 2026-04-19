package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.util.*;

import static java.util.Optional.ofNullable;

public interface BaseCheckpointSaver {
    String THREAD_ID_DEFAULT = "$default";

    record Tag(String threadId, Collection<Checkpoint> checkpoints, int version ) {
        public Tag(String threadId, Collection<Checkpoint> checkpoints, int version ) {
            this.threadId = threadId;
            this.checkpoints = ofNullable(checkpoints).map(List::copyOf).orElseGet(List::of);
            this.version = version;
        }
        public Tag(String threadId, Collection<Checkpoint> checkpoints) {
            this(threadId, checkpoints,0);
        }

        public Optional<Checkpoint> lastCheckpoint() {
            return checkpoints().stream().findFirst();
        }

    }

    Collection<Checkpoint> list(RunnableConfig config);

    Optional<Checkpoint> get(RunnableConfig config);

    RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception;

    Tag release(RunnableConfig config) throws Exception;

    default String threadId( RunnableConfig config ) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }
}
