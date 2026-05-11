package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents the unified result of a graph execution.
 * <p>
 * This class encapsulates different types of results that can be produced by a graph,
 * such as interruption metadata, node output, checkpoint tags, or raw state data.
 * It provides methods to check the type of the result and retrieve it safely.
 * </p>
 */
public record GraphResult( Object result, Type type ) {
    public enum Type {
        EMPTY,
        INTERRUPTION_METADATA,
        NODE_OUTPUT,
        CHECKPOINT_SAVER_TAG,
        STATE_DATA,
        CANCELLED
    }

    private static final GraphResult EMPTY = new GraphResult( null, Type.EMPTY );

    /**
     * Returns an empty {@code GraphResult}.
     *
     * @return the empty result.
     */
    public static GraphResult empty() {
        return EMPTY;
    }

    /**
     * Creates a {@code GraphResult} from the object returning by the AsyncGenerator obtained by StateGraph.stream() call.
     * <p>
     * The method attempts to determine the type of the result and wrap it in a {@code GraphResult}.
     * Supported types are:
     * <ul>
     *     <li>{@code GraphResult} (returns the object itself)</li>
     *     <li>{@code Map} (treated as state data)</li>
     *     <li>{@code NodeOutput}</li>
     *     <li>{@code InterruptionMetadata}</li>
     *     <li>{@code BaseCheckpointSaver.Tag}</li>
     * </ul>
     * If the input is {@code null}, an empty {@code GraphResult} is returned.
     * </p>
     *
     * @param result the object to wrap.
     * @return a {@code GraphResult} instance.
     * @throws IllegalArgumentException if the result type is not supported.
     */
    public static GraphResult from( Object result ) {

        if( result == null ) {
            return EMPTY;
        }
        if( result instanceof GraphResult graphResult ) {
            return graphResult;
        }
        if( result instanceof Map<?,?>) {
            return new GraphResult( result, Type.STATE_DATA);
        }
        if( result instanceof NodeOutput<?>  ) {
            return new GraphResult( result, Type.NODE_OUTPUT);
        }
        if( result instanceof InterruptionMetadata<?> ) {
            return new GraphResult( result, Type.INTERRUPTION_METADATA);
        }
        if( result instanceof BaseCheckpointSaver.Tag ) {
            return new GraphResult( result, Type.CHECKPOINT_SAVER_TAG);
        }
        if( result == AsyncGenerator.IsCancellable.CANCELLED ) {
            return new GraphResult( result, Type.CANCELLED);
        }
        throw new IllegalArgumentException( "Invalid result type: %s".formatted(result.getClass()) );
    }

    /**
     * Creates a {@code GraphResult} from an {@link AsyncGenerator}.
     * <p>
     * This method extracts the result value from the generator and wraps it in a {@code GraphResult}.
     * </p>
     *
     * @param generator the generator to extract the result from.
     * @return a {@code GraphResult} instance.
     * @throws NullPointerException if the generator is null.
     */
    public static GraphResult from(AsyncGenerator<?> generator) {
        requireNonNull( generator, "generator cannot be null");

       return from( AsyncGenerator.resultValue( generator ).orElse(null) );
    }


    /**
     * Checks if the result is empty (i.e., contains no data).
     *
     * @return true if the result is empty, false otherwise.
     */
    public boolean isEmpty() {
        return type == Type.EMPTY;
    }

    public boolean isCancelled() {
        return type == Type.CANCELLED;
    }

    /**
     * Checks if the result contains interruption metadata.
     *
     * @return true if interruption metadata is present, false otherwise.
     */
    public boolean isInterruptionMetadata() {
        return type == Type.INTERRUPTION_METADATA;
    }

    /**
     * Retrieves the result as {@link InterruptionMetadata}.
     *
     * @param <State> the type of the agent state.
     * @return the interruption metadata.
     * @throws IllegalStateException if the result does not contain interruption metadata.
     */
    @SuppressWarnings("unchecked")
    public <State extends AgentState> InterruptionMetadata<State> asInterruptionMetadata() {
        if( isInterruptionMetadata()) {
            return (InterruptionMetadata<State>)result;
        }
        throw new IllegalStateException("Result doesn't contain an interruption metadata object");
    }

    /**
     * Checks if the result contains node output.
     *
     * @return true if node output is present, false otherwise.
     */
    public boolean isNodeOutput() {
        return type == Type.NODE_OUTPUT;
    }

    /**
     * Retrieves the result as {@link NodeOutput}.
     *
     * @param <State> the type of the agent state.
     * @return the node output.
     * @throws IllegalStateException if the result does not contain node output.
     */
    @SuppressWarnings("unchecked")
    public <State extends AgentState> NodeOutput<State> asNodeOutput() {
        if( isNodeOutput() ) {
            return (NodeOutput<State>)result;
        }
        throw new IllegalStateException("Result doesn't contain a node output object");
    }

    /**
     * Checks if the result contains a checkpoint tag.
     *
     * @return true if a tag is present, false otherwise.
     */
    public boolean isCheckpointSaverTag() {
        return type == Type.CHECKPOINT_SAVER_TAG;
    }

    /**
     * Retrieves the result as a {@link BaseCheckpointSaver.Tag}.
     *
     * @return the checkpoint tag.
     * @throws IllegalStateException if the result does not contain a tag.
     */
    public BaseCheckpointSaver.Tag asCheckpointSaverTag() {
        if( isCheckpointSaverTag() ) {
            return (BaseCheckpointSaver.Tag)result;
        }
        throw new IllegalStateException("Result doesn't contain a tag object");
    }

    /**
     * Checks if the result contains state data.
     *
     * @return true if state data is present, false otherwise.
     */
    public boolean isStateData() {
        return type == Type.STATE_DATA;
    }

    /**
     * Retrieves the result as a map of state data.
     *
     * @return the state data map.
     * @throws IllegalStateException if the result does not contain state data.
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> asStateData() {
        if( isStateData() ) {
            return (Map<String,Object>)result;
        }
        throw new IllegalStateException("Result doesn't contain a state object");
    }

    public Map<String,Object> asLastCheckpointStateData() {
        return asCheckpointSaverTag().lastCheckpoint()
                    .map( Checkpoint::getState )
                    .orElseThrow(() -> new IllegalStateException("Checkpoint saver tag doesn't contain any checkpoint"));
    }

    public boolean isStateDataOrCheckpointSaverTag() {
        return isStateData() || isCheckpointSaverTag();
    }

    public Map<String,Object> asStateDataOrLastCheckpointStateData() {
        if( isStateData() ) {
            return asStateData();
        }
        return asLastCheckpointStateData();
    }

    @Override
    public String toString() {
        if( isStateData() ) {
            return CollectionsUtils.toString(asStateData());
        }
        return Objects.toString(result);

    }

}