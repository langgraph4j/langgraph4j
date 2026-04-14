package org.bsc.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Represents the output of a node in a graph.
 *
 * @param <State> the type of the state associated with the node output
 */
public class NodeOutput<State extends AgentState> implements HasMetadata {


    public static <State extends AgentState> NodeOutput<State> of(String node, State state) {
        return new NodeOutput<>(node, state);
    }

    /**
     * The identifier of the node.
     */
    private final String node;

    /**
     * The state associated with the node.
     */
    private final State state;

    protected final transient HasMetadata metadataSupplier;

    /**
     * Returns the node name.
     *
     * @return the node name
     */
    public String node() {
        return node;
    }

    public State state() {
        return state;
    }

    /**
     * Checks if the current node refers to the start of the graph processing.
     *
     * @return {@code true} if the current node refers to the start of the graph processing
     */
    public boolean isSTART() {
        return Objects.equals(node(), START);
    }

    /**
     * Checks if the current node refers to the end of the graph processing.
     * useful to understand if the workflow has been interrupted.
     *
     * @return {@code true} if the current node refers to the end of the graph processing
     */
    public boolean isEND() {
        return Objects.equals(node(), END);
    }

    public NodeOutput(String node, State state, HasMetadata metadataSupplier) {
        this.node = node;
        this.state = state;
        this.metadataSupplier = metadataSupplier;
    }

    public NodeOutput(String node, State state) {
        this(node, state, null);
    }

    public NodeOutput( NodeOutput<State> nodeOutput ) {
        this(nodeOutput.node(), nodeOutput.state(), nodeOutput.metadataSupplier);
    }

    @Override
    public String toString() {
        return "NodeOutput{ node=%s, state=%s}".formatted(
                node(),
                state());
    }

    @Override
    public Optional<Object> metadata(String key) {
        return ofNullable(metadataSupplier)
                .flatMap( provider -> provider.metadata(key) );
    }

    @Override
    public Set<String> metadataKeys() {
        return ofNullable(metadataSupplier)
                .map(HasMetadata::metadataKeys)
                .orElseGet(Set::of);
    }
}
