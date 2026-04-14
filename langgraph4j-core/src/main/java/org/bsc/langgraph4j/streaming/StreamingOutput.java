package org.bsc.langgraph4j.streaming;

import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.MetadataUtils;

public class StreamingOutput<State extends AgentState> extends NodeOutput<State> {

    private final String chunk; // null

    public StreamingOutput(String chunk, String node, State state, HasMetadata metadataSupplier) {
        super(node, state, metadataSupplier);

        this.chunk = chunk;
    }

    public String chunk() {
        return chunk;
    }

    /**
     * @deprecated use {@link #isStreamingEnd()} instead
     * @return true if the streaming is ended
     */
    @Deprecated(forRemoval = true)
    public boolean isEnd() {
        return false;
    }

    public boolean isStreamingEnd() {
        return false;
    }

    @Override
    public String toString() {
        if (node() == null) {
            return "%s{chunk=%s}".formatted(getClass().getSimpleName(), chunk());
        }
        return "%s{node=%s, chunk=%s, state=%s,%n metadata=%s%n }".formatted(
                getClass().getSimpleName(),
                node(),
                chunk(),
                state(),
                MetadataUtils.toString(metadataSupplier));
    }

}
