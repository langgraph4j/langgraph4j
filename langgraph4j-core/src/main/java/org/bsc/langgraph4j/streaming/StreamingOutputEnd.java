package org.bsc.langgraph4j.streaming;

import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.state.AgentState;

public final class StreamingOutputEnd<State extends AgentState> extends StreamingOutput<State> {

    public StreamingOutputEnd( String finalResponse, String node, State state, HasMetadata metadataSupplier) {
        super(finalResponse, node, state, metadataSupplier);
    }

    @Override
    public boolean isEnd() {
        return true;
    }

    @Override
    public boolean isStreamingEnd() {
        return true;
    }
}
