package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.SnapshotOutput;
import org.bsc.langgraph4j.checkpoint.Checkpoint;

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
        return new StateSnapshot<>( checkpoint.getNodeId(), checkpoint, factory);
    }

    private final String checkpointId;
    private final String checkpointNextNodeId;

    public String nextNodeId( ) {
        return checkpointNextNodeId;
    }

    public String checkpointId() {
        return checkpointId;
    }

    private StateSnapshot( String node, Checkpoint checkpoint, AgentStateFactory<State> factory) {
        super( node, factory.apply(checkpoint.getState()) );
        this.checkpointId = Objects.requireNonNull(checkpoint.getId(), "checkpointId cannot be null");
        this.checkpointNextNodeId = Objects.requireNonNull(checkpoint.getNextNodeId(), "checkpointNextNodeId cannot be null");
    }

    @Override
    public String toString() {

        return "StateSnapshot{node=%s, state=%s, checkpointId=%s, checkpointNextNodeId=%s}"
                .formatted(node(), state(), checkpointId(), nextNodeId());
    }



}
