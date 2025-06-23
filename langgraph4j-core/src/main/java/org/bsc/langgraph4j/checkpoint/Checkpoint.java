package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

import static java.lang.String.format;

/**
 * Represents a checkpoint of an agent state.
 *
 * The checkpoint is an immutable object that holds an {@link AgentState}
 * and a {@code String} that represents the next state.
 *
 * The checkpoint is serializable and can be persisted and restored.
 *
 * @see AgentState
 */
public class Checkpoint {

    private String id = UUID.randomUUID().toString();
    private Map<String,Object> state = null;
    private String nodeId = null ;
    private String nextNodeId = null;

    public String getId() {
        return id;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    private Checkpoint() {
    }

    public Checkpoint( Checkpoint checkpoint ) {
        this.id = Objects.requireNonNull( checkpoint.id, "id cannot be null" );
        this.state =  Objects.requireNonNull( checkpoint.state, "state cannot be null" );
        this.nodeId = Objects.requireNonNull( checkpoint.nodeId, "nodeId cannot be null" );
        this.nextNodeId =  Objects.requireNonNull( checkpoint.nextNodeId, "Checkpoint.nextNodeId cannot be null" );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Checkpoint result = new Checkpoint();

        public Builder id( String id ) {
            result.id = id;
            return this;
        }
        public Builder state( AgentState state ) {
            result.state = state.data();
            return this;
        }
        public Builder state( Map<String,Object> state ) {
            result.state = state;
            return this;
        }
        public Builder nodeId( String nodeId ) {
            result.nodeId = nodeId;
            return this;
        }
        public Builder nextNodeId( String nextNodeId ) {
            result.nextNodeId = nextNodeId;
            return this;
        }

        public Checkpoint build() {
            return new Checkpoint(result);

        }
    }

    public Checkpoint updateState(Map<String,Object> values, Map<String, Channel<?>> channels ) {

        Checkpoint result = new Checkpoint( this );
        result.state = AgentState.updateState( state, values, channels );
        return result;
    }

    @Override
    public String toString() {
        return  format("Checkpoint{ id=%s, nodeId=%s, nextNodeId=%s, state=%s }" ,
                id,
                nodeId,
                nextNodeId,
                state
        );
    }


}
