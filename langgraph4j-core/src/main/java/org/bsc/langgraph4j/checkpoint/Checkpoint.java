package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.jspecify.annotations.NonNull;

import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents a checkpoint of an agent state.
 * The checkpoint is an immutable object that holds an {@link AgentState}
 * and a {@code String} that represents the next state.
 * The checkpoint is serializable and can be persisted and restored.
 *
 * @see AgentState
 */
public record Checkpoint(
        String id,
        Map<String, Object> state,
        String nodeId,
        String nextNodeId
) {

    public Checkpoint {

        requireNonNull( id, "id cannot be null" );
        requireNonNull( state, "state cannot be null" );
        requireNonNull( nodeId, "nodeId cannot be null" );
        requireNonNull( nextNodeId, "Checkpoint.nextNodeId cannot be null" );

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
     * create a copy of given checkpoint with a new id
     * @param checkpoint value from which copy is created
     * @return new copy with different id
     */
    public static Checkpoint copyOf( Checkpoint checkpoint ) {
        requireNonNull( checkpoint, "checkpoint cannot be null" );
        return new Checkpoint( UUID.randomUUID().toString(),
                                checkpoint.state,
                                checkpoint.nodeId,
                                checkpoint.nextNodeId);
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private Map<String,Object> state = null;
        private String nodeId = null ;
        private String nextNodeId = null;

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

        public Checkpoint build() {
            return new Checkpoint(  id,
                                    state,
                                    nodeId,
                                    nextNodeId );
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
                requireNonNull(nextNodeId, "nextNodeId cannot be null") );
    }

    @Override
    @NonNull
    public String toString() {
        return  "Checkpoint{ id=%s, nodeId=%s, nextNodeId=%s, state=%s }".formatted(
                id,
                nodeId,
                nextNodeId,
                state
        );
    }


}
