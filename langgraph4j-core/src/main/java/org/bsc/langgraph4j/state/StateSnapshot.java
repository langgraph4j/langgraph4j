package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.SnapshotOutput;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

import static java.lang.String.*;

public final class StateSnapshot<State extends AgentState> extends NodeOutput<State> implements SnapshotOutput {
    public static <State extends AgentState> StateSnapshot<State> of(Checkpoint checkpoint, AgentStateFactory<State> factory) {

        /*
        RunnableConfig newConfig = RunnableConfig.builder(config)
                .checkPointId( checkpoint.getId() )
                .nextNode( checkpoint.getNextNodeId() )
                .build() ;

         */
        return new StateSnapshot<>( checkpoint.nodeId(), checkpoint, factory);
    }

    private final String checkpointId;
    private final String checkpointNextNodeId;
    private final @Nullable Instant checkpointSavedAt;

    public String nextNodeId( ) {
        return checkpointNextNodeId;
    }

    public String checkpointId() {
        return checkpointId;
    }

    /**
     * The time the checkpoint this snapshot refers to was persisted.
     *
     * @return the checkpoint saved-at time, {@code null} when the storage did not supply one
     * @since 1.9.8
     */
    public @Nullable Instant savedAt() {
        return checkpointSavedAt;
    }

    /**
     * Alias of {@link #savedAt()}, kept so that code reading the attribute
     * introduced in 1.9.6 keeps compiling after the rename.
     *
     * @return the checkpoint saved-at time
     * @deprecated use {@link #savedAt()} instead; this alias will be removed once
     *             consumers had one release to migrate
     * @since 1.9.6
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
    public @Nullable Instant createdAt() {
        return checkpointSavedAt;
    }

    private StateSnapshot( String node, Checkpoint checkpoint, AgentStateFactory<State> factory) {
        super( node, factory.apply(checkpoint.state()) );
        this.checkpointId = Objects.requireNonNull(checkpoint.id(), "checkpointId cannot be null");
        this.checkpointNextNodeId = Objects.requireNonNull(checkpoint.nextNodeId(), "checkpointNextNodeId cannot be null");
        this.checkpointSavedAt = checkpoint.savedAt();
    }

    @Override
    public String toString() {

        return "StateSnapshot{node=%s, state=%s, checkpointId=%s, checkpointNextNodeId=%s, savedAt=%s}"
                .formatted(node(), state(), checkpointId(), nextNodeId(), checkpointSavedAt);
    }



}
