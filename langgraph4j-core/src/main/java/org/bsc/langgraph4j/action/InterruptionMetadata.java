package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Represents the metadata associated with a graph execution interruption.
 * This class is immutable and captures the state of the graph at the point of interruption,
 * the node where the interruption occurred, and any additional custom metadata.
 *
 * @param <State> the type of the agent state, which must extend {@link AgentState}
 */
public final class InterruptionMetadata<State extends AgentState> implements HasMetadata  {
    public static final String INTERRUPTION_REASON = "LG4J_INTERRUPTION_REASON";

    private final String nodeId;
    private final State state;

    private final Map<String,Object> metadata;

    private InterruptionMetadata(Builder<State> builder) {
        this.metadata = builder.metadata();
        this.nodeId = requireNonNull(builder.nodeId,"nodeId cannot be null!");
        this.state = requireNonNull(builder.state, "state cannot be null!");
    }

    /**
     * Gets the ID of the node where the interruption occurred.
     *
     * @return the node ID
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * Gets the state of the graph at the time of the interruption.
     *
     * @return the agent state
     */
    public State state() {
        return state;
    }

    /**
     * Gets the reason for the interruption, if available.
     *
     * @return an {@link Optional} containing the interruption reason, or an empty {@link Optional} if not available
     */
    public Optional<String> reason() {
        return metadata(INTERRUPTION_REASON).map( Object::toString );
    }

    /**
     * Retrieves a metadata value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @return an {@link Optional} containing the value to which the specified key is mapped,
     *         or an empty {@link Optional} if this metadata contains no mapping for the key.
     */
    @Override
    public Optional<Object> metadata(String key) {
        return ofNullable(metadata)
                .map( m -> m.get( key ) );
    }

    @Override
    public Set<String> metadataKeys() {
        return ofNullable(metadata).map( Map::keySet ).orElseGet(Set::of);
    }

    @Override
    public String toString() {
        return String.format("""
                InterruptionMetadata{
                \tnodeId='%s',
                \tstate=%s,
                \tmetadata=%s
                }""",
                nodeId,
                state,
                CollectionsUtils.toString(metadata));
    }

    /**
     * Creates a new builder for {@link InterruptionMetadata}.
     *
     * @param <State> the type of the agent state
     * @return a new {@link Builder} instance
     */
    public static <State extends AgentState> Builder<State> builder( String nodeId, State state ) {
        return new Builder<>( nodeId, state );
    }

    /**
     * A builder for creating instances of {@link InterruptionMetadata}.
     *
     * @param <State> the type of the agent state
     */
    public static class Builder<State extends AgentState> extends HasMetadata.Builder<Builder<State>> {
        final String nodeId;
        final State state;

        /**
         * Constructs a new builder.
         *
         */
        public Builder( String nodeId, State state ) {
            this.nodeId = nodeId;
            this.state = state;
        }

        public Builder<State> reason(String reason) {
            return putMetadata(INTERRUPTION_REASON, reason);
        }

        /**
         * Builds the {@link InterruptionMetadata} instance.
         *
         * @return a new, immutable {@link InterruptionMetadata} instance
         */
        public InterruptionMetadata<State> build() {
            return new InterruptionMetadata<>(this);
        }
    }

}