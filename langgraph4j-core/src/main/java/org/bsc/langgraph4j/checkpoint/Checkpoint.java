package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Represents a checkpoint of an agent state.
 * The checkpoint is an immutable object that holds an {@link AgentState}
 * and a {@code String} that represents the next state.
 * The checkpoint is serializable and can be persisted and restored.
 *
 * <p>Every checkpoint carries the {@link #savedAt() saved-at time}, that is the
 * instant the checkpoint was persisted, which can be used to order checkpoints
 * reliably ( ie. by custom checkpoint savers ) even when the underlying storage
 * doesn't preserve insertion order.
 *
 * <p>The value is meant to be assigned by the side that writes to the storage,
 * because when several instances of a graph share one store the local clock of
 * the node that built the checkpoint is not comparable across nodes. Savers that
 * cannot pre-fill it ( or that read a payload written before it existed ) leave
 * it {@code null}, which means "the storage did not give us a time" and not
 * "the checkpoint was never saved".
 *
 * @see AgentState
 * @since 1.0.0
 */
public record Checkpoint(
        @NonNull String id,
        @NonNull Map<String, Object> state,
        @NonNull String nodeId,
        @NonNull String nextNodeId,
        @Nullable Instant savedAt
) {

    public Checkpoint {
        requireNonNull( id, "id cannot be null" );
        requireNonNull( state, "state cannot be null" );
        requireNonNull( nodeId, "nodeId cannot be null" );
        requireNonNull( nextNodeId, "Checkpoint.nextNodeId cannot be null" );

    }

    /**
     * Creates a checkpoint without an explicit saved-at time.
     * The saved-at time defaults to {@link Instant#now()} truncated to
     * millisecond precision, so a value that survives a round trip through a
     * database ( most of which store milliseconds at best ) compares equal to
     * the one held in memory. Savers able to pre-fill the field from the
     * storage should do so instead of relying on this default.
     *
     * @param id         the checkpoint id
     * @param state      the checkpoint state
     * @param nodeId     the node that produced the checkpoint
     * @param nextNodeId the next node to execute
     */
    public Checkpoint( String id, Map<String,Object> state, String nodeId, String nextNodeId ) {
        this( id, state, nodeId, nextNodeId, Instant.now().truncatedTo(ChronoUnit.MILLIS) );
    }

    @Deprecated
    public String getId() { return id; }

    @Deprecated
    public Map<String, Object> getState() {
        return state;
    }

    @Deprecated
    public String getNodeId() {
        return nodeId;
    }

    @Deprecated
    public String getNextNodeId() {
        return nextNodeId;
    }

    /**
     * Alias of {@link #savedAt()}, kept so that code reading the attribute
     * introduced in 1.9.6 keeps compiling after the rename.
     *
     * @return the saved-at time, {@code null} when the storage did not supply one
     * @deprecated use {@link #savedAt()} instead; this alias will be removed once
     *             consumers had one release to migrate
     * @since 1.9.6
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
    public @Nullable Instant createdAt() { return savedAt; }

    /**
     * create a copy of given checkpoint with a new id
     * @param checkpoint value from which copy is created
     * @return new copy with different id
     */
    public static Checkpoint copyOf( Checkpoint checkpoint ) {
        requireNonNull( checkpoint, "checkpoint cannot be null" );
        return new Checkpoint( UUID.randomUUID().toString(),
                                checkpoint.state,
                                checkpoint.nodeId,
                                checkpoint.nextNodeId,
                                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private Map<String,Object> state = null;
        private String nodeId = null ;
        private String nextNodeId = null;
        private @Nullable Instant savedAt = null;
        // whether a saved-at time was supplied at all: null is a valid answer, meaning
        // "my storage did not give me one", and must not fall back to the local clock
        private boolean savedAtSupplied = false;

        public Builder id( String id ) {
            this.id = id;
            return this;
        }
        public Builder state( AgentState state ) {
            this.state = state.data();
            return this;
        }
        public Builder state( Map<String,Object> state ) {
            this.state = state;
            return this;
        }
        public Builder nodeId( String nodeId ) {
            this.nodeId = nodeId;
            return this;
        }
        public Builder nextNodeId( String nextNodeId ) {
            this.nextNodeId = nextNodeId;
            return this;
        }

        /**
         * Set the time the checkpoint was persisted.
         * This is how a saver hands over the time assigned by its own storage; when
         * it is not supplied the saved-at time falls back to {@link Instant#now()}
         * truncated to millisecond precision.
         * Warning: The Instant by default {@link Instant#now()} use nanoseconds precision.
         * This could have side effects on your Checkpoint saver underlying storage.
         * Just in case use {@code truncatedTo(ChronoUnit.MILLIS)} to truncated to millisecond precision.
         *
         * @param savedAt the saved-at time, may be null
         * @return this builder
         * @since 1.9.8
         */
        public Builder savedAt( @Nullable Instant savedAt ) {
            this.savedAt = savedAt;
            this.savedAtSupplied = true;
            return this;
        }

        /**
         * Set the time the checkpoint was persisted using a {@link Date}.
         *
         * @param savedAt the saved-at time, may be null
         * @return this builder
         * @since 1.9.8
         */
        public Builder savedAt( @Nullable Date savedAt ) {
            this.savedAt = savedAt != null ? savedAt.toInstant() : null;
            this.savedAtSupplied = true;
            return this;
        }

        /**
         * Alias of {@link #savedAt(Instant)}.
         *
         * @param createdAt the saved-at time
         * @return this builder
         * @deprecated use {@link #savedAt(Instant)} instead; kept so savers written
         *             against 1.9.6/1.9.7 ( eg. {@code MongoDBSaver} ) keep compiling
         * @since 1.9.7
         */
        @Deprecated( since = "1.9.8", forRemoval = true )
        public Builder createdAt( @Nullable Instant createdAt ) {
            return savedAt( createdAt );
        }

        /**
         * Alias of {@link #savedAt(Date)}.
         *
         * @param createdAt the saved-at time
         * @return this builder
         * @deprecated use {@link #savedAt(Date)} instead, see {@link #createdAt(Instant)}
         * @since 1.9.7
         */
        @Deprecated( since = "1.9.8", forRemoval = true )
        public Builder createdAt( @Nullable Date createdAt ) {
            return savedAt( createdAt );
        }

        public Checkpoint build() {
            return new Checkpoint(  id,
                                    state,
                                    nodeId,
                                    nextNodeId,
                                    savedAtSupplied
                                            ? savedAt
                                            : Instant.now().truncatedTo(ChronoUnit.MILLIS) );
        }
    }

    /**
     * Returns a new Checkpoint with updated state, keeping the current nextNodeId.
     *
     * @param values   the partial state updates to merge into this checkpoint's state
     * @param channels the registered channels used to resolve and update values;
     *                 may be null if values are already resolved
     * @return a new immutable Checkpoint; the original is unchanged
     * @see AgentState#updateState
     */
    public Checkpoint updateState(Map<String,Object> values, Map<String, Channel<?>> channels ) {
        return updateState( values, channels, this.nextNodeId );
    }

    /**
     * Returns a new Checkpoint with updated state and a new nextNodeId.
     *
     * <p>The saved-at time is preserved because the checkpoint keeps its identity
     * ( ie. the same {@link #id()} ) and it is updated in place.
     *
     * @param values     the partial state updates to merge into this checkpoint's state
     * @param channels   the registered channels used to resolve and update values;
     *                   may be null if values are already resolved
     * @param nextNodeId the identifier of the next node to execute; must not be null
     * @return a new immutable Checkpoint; the original is unchanged
     * @throws NullPointerException if nextNodeId is null
     * @see AgentState#updateState
     */
    public Checkpoint updateState(Map<String,Object> values, Map<String, Channel<?>> channels, String nextNodeId ) {

        return new Checkpoint( this.id,
                AgentState.updateState( this.state, values, channels ),
                this.nodeId,
                requireNonNull(nextNodeId, "nextNodeId cannot be null"),
                this.savedAt );
    }

    @Override
    @NonNull
    public String toString() {
        return  "Checkpoint{ id=%s, nodeId=%s, nextNodeId=%s, savedAt=%s, state=%s }".formatted(
                id,
                nodeId,
                nextNodeId,
                savedAt,
                state
        );
    }


}
