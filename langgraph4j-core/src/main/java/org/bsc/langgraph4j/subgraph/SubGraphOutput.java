package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents the output of a subgraph node.
 * This class is sealed and permits {@link SubGraphSnapshotOutput} subclasses.
 *
 * @param <State> the type of the agent state
 */
public sealed class SubGraphOutput<State extends AgentState> extends NodeOutput<State> permits SubGraphSnapshotOutput {

    /**
     * subgraph node id
     */
    private final String subGraphId;

    /**
     * Returns the ID of the subgraph.
     * @return the subgraph ID
     */
    public String subGraphId() {
        return subGraphId;

    }

    public SubGraphOutput( NodeOutput<State> output, String subGraphId) {
        super( output );
        this.subGraphId = requireNonNull(subGraphId, "subGraphId cannot be null");
    }

    @Override
    public String toString() {
        return format("SubGraphOutput{node=%s, subGraphId=%s, state=%s}",
                node(),
                subGraphId(),
                state());
    }

}
