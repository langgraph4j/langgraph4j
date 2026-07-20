package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.SnapshotOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

import static java.lang.String.format;

public final class SubGraphSnapshotOutput<State extends AgentState> extends SubGraphOutput<State> implements SnapshotOutput {
    private final RunnableConfig config;

    public String next( ) {
        return config.nextNode().orElse(null);
    }

    public RunnableConfig config() {
        return config;
    }

    public SubGraphSnapshotOutput(StateSnapshot<State> snapshot, String subGraphId, HasMetadata metadataProvider) {
        super(snapshot, subGraphId, metadataProvider);
        this.config = snapshot.config();
    }

    @Override
    public String toString() {
        return format("SubGraphSnapshotOutput{node=%s, state=%s, config=%s}", node(), state(), config());
    }

}
