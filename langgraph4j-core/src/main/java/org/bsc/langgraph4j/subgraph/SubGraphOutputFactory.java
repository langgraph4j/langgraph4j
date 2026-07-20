package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.metadata.MetadataBag;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

public interface SubGraphOutputFactory {

    static <State extends AgentState> SubGraphOutput<State> createFromNodeOutput(NodeOutput<State> output, String subGraphId, RunnableConfig config ) {
        if( output instanceof SubGraphOutput<State> subGraphOutput) {
            return subGraphOutput;
        }
        else {
            final var nodePath = config.nodePath().replaceLast(output.node());
            final var metadata = MetadataBag.builder()
                    .putMetadata( RunnableConfig.GRAPH_NODE_PATH, nodePath)
                    .build();
            if( output instanceof StateSnapshot<State> subGraphSnapshotOutput ) {
                return new SubGraphSnapshotOutput<>( subGraphSnapshotOutput, subGraphId, metadata );
            }
            return new SubGraphOutput<>( output, subGraphId, metadata );
        }

    }
}
