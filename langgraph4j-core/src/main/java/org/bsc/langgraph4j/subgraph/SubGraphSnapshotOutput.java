package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.SnapshotOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

public final class SubGraphSnapshotOutput<State extends AgentState> extends SubGraphOutput<State> implements SnapshotOutput {

    private final String checkpointId;
    private final String checkpointNextNodeId;

    public SubGraphSnapshotOutput(StateSnapshot<State> snapshot, String subGraphId, HasMetadata metadataProvider) {
        super(snapshot, subGraphId, metadataProvider);
        this.checkpointId = snapshot.checkpointId();
        this.checkpointNextNodeId = snapshot.nextNodeId();
    }

    public String nextNodeId( ) {
        return checkpointNextNodeId;
    }

    @Override
    public String checkpointId() {
        return checkpointId;
    }

    @Override
    public String toString() {
        return "SubGraphSnapshotOutput{node=%s, state=%s, checkpointId=%s, checkpointNextNodeId=%s}"
                .formatted(node(), state(), checkpointId, checkpointNextNodeId);
    }

}
