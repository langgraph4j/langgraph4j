package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.StateSchema;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.jspecify.annotations.NonNull;

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
public final class GraphResult {
    private final Object result;
    private final Type type;
    private final AgentStateFactory<?> stateFactory;
    private final StateSchema outputSchema;

    private GraphResult(Object result, Type type, AgentStateFactory<?> stateFactory, StateSchema outputSchema) {
        this.result = result;
        this.type = type;
        this.stateFactory = stateFactory;
        this.outputSchema = outputSchema;
    }
    private GraphResult(Object result, Type type) {
        this.result = result;
        this.type = type;
        this.stateFactory = null;
        this.outputSchema = null;
    }

    public enum Type {
        EMPTY,
        INTERRUPTION_METADATA,
        /**
         * @deprecated the graph do not return NodeOutput object anymore, use STATE_DATA or CHECKPOINT_SAVER_TAG instead
         */
        @Deprecated(since = "1.9.8", forRemoval = true)
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
     * Creates a {@code GraphResult} like {@link #from(Object)}, additionally binding the state factory
     * and output schema of the given graph so state data can later be filtered by the output schema.
     *
     * @param result     the object to wrap; null yields the empty result, an existing {@code GraphResult} is returned as is.
     * @param stateGraph the graph providing the state factory and output schema.
     * @param <S>        the type of the agent state.
     * @return a {@code GraphResult} instance.
     * @throws IllegalArgumentException if the result type is not supported.
     */
    static <S extends AgentState> GraphResult from(Object result, StateGraph<S> stateGraph) {

        if( result == null ) {
            return EMPTY;
        }
        if( result instanceof GraphResult graphResult ) {
            return graphResult;
        }

        if( result instanceof Map<?,?>) {
            return new GraphResult( result, Type.STATE_DATA, stateGraph.stateFactory(), stateGraph.outputSchema);
        }
        if( result instanceof InterruptionMetadata<?> ) {
            return new GraphResult( result, Type.INTERRUPTION_METADATA, stateGraph.stateFactory(), stateGraph.outputSchema);
        }
        if( result instanceof BaseCheckpointSaver.Tag ) {
            return new GraphResult( result, Type.CHECKPOINT_SAVER_TAG, stateGraph.stateFactory(), stateGraph.outputSchema);
        }
        if( result == AsyncGenerator.IsCancellable.CANCELLED ) {
            return new GraphResult( result, Type.CANCELLED, stateGraph.stateFactory(), stateGraph.outputSchema);
        }
        if( result instanceof NodeOutput<?>  ) {
            return new GraphResult( result, Type.NODE_OUTPUT, stateGraph.stateFactory(), stateGraph.outputSchema);
        }
        throw new IllegalArgumentException( "Invalid result type: %s".formatted(result.getClass()) );

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
        if( result instanceof InterruptionMetadata<?> ) {
            return new GraphResult( result, Type.INTERRUPTION_METADATA);
        }
        if( result instanceof BaseCheckpointSaver.Tag ) {
            return new GraphResult( result, Type.CHECKPOINT_SAVER_TAG);
        }
        if( result == AsyncGenerator.IsCancellable.CANCELLED ) {
            return new GraphResult( result, Type.CANCELLED);
        }
        if( result instanceof NodeOutput<?>  ) {
            return new GraphResult( result, Type.NODE_OUTPUT);
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
     * Returns the kind of result held by this instance.
     *
     * @return the result type, never null.
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the raw, unfiltered result object.
     * <p>
     * Prefer the typed accessors (e.g. {@link #asStateData()}), which also apply the output schema.
     * </p>
     *
     * @return the wrapped object, or null if the result is {@link Type#EMPTY}.
     */
    public Object result() {
        return result;
    }

    /**
     * Checks if the result is empty (i.e., contains no data).
     *
     * @return true if the result is empty, false otherwise.
     */
    public boolean isEmpty() {
        return type == Type.EMPTY;
    }

    /**
     * Checks if the graph execution was cancelled.
     *
     * @return true if the result type is {@link Type#CANCELLED}, false otherwise.
     */
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
     * @deprecated the graph do not return NodeOutput object anymore, use isStateData() or isCheckpointSaverTag() instead
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
    public boolean isNodeOutput() {
        return type == Type.NODE_OUTPUT;
    }

    /**
     * Retrieves the result as {@link NodeOutput}.
     *
     * @param <State> the type of the agent state.
     * @return the node output.
     * @throws IllegalStateException if the result does not contain node output.
     * @deprecated the graph do not return NodeOutput object anymore, use asStateData() or asLastCheckpointStateData() instead
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
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
        if( type == Type.CHECKPOINT_SAVER_TAG ) {
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
        return type == Type.STATE_DATA || type == Type.CHECKPOINT_SAVER_TAG;
    }

    /**
     * Retrieves the result as a map of state data, filtered by the graph's output schema.
     * <p>
     * If the result is a checkpoint saver tag, the state of its last checkpoint is returned instead,
     * so callers can treat both result kinds uniformly.
     * </p>
     *
     * @return the state data map, restricted to the keys allowed by the output schema.
     * @throws IllegalStateException if the result is neither state data nor a checkpoint saver tag,
     *         or if the tag does not contain any checkpoint.
     * @see AgentState#filterBySchema(Map, StateSchema)
     * @see #asLastCheckpointStateData()
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> asStateData() {
        if( type == Type.STATE_DATA ) {
            return AgentState.filterBySchema((Map<String,Object>)result, outputSchema);
        }
        if( type == Type.CHECKPOINT_SAVER_TAG ) {
            return asLastCheckpointStateData();
        }
        throw new IllegalStateException("Result doesn't contain a state object");
    }


    /**
     * Retrieves the result as a typed agent state, built from {@link #asStateData()} through the graph's state factory.
     * <p>
     * Only results created through the graph (see {@link #from(Object, StateGraph)}) carry a state factory;
     * instances created with {@link #from(Object)} or {@link #empty()} cannot be converted.
     * </p>
     *
     * @param <S> the expected agent state type; the cast is unchecked, so a mismatch surfaces as a
     *            {@link ClassCastException} at the call site.
     * @return a new state instance populated with the (schema-filtered) state data.
     * @throws IllegalStateException if no state factory is available, if the result is neither state data
     *         nor a checkpoint saver tag, or if the tag does not contain any checkpoint.
     * @see #asStateData()
     */
    @SuppressWarnings("unchecked")
    public <S extends AgentState> S asState() {
        if( stateFactory == null ) {
            throw new IllegalStateException("State factory is not available for this result instance");
        }
        final var stateData = asStateData();
        return (S)stateFactory.apply(stateData);
    }

    /**
     * Retrieves the last checkpoint state data from the checkpoint saver tag.
     *
     * @return the last checkpoint state data map.
     * @throws IllegalStateException if the result does not contain a checkpoint saver tag or if the tag does not contain any checkpoint.
     * @see BaseCheckpointSaver.Tag#lastCheckpoint()
     * @see Checkpoint#state()
     * @see AgentState#filterBySchema(Map, StateSchema)
     * @see StateSchema
     * @see GraphResult#isCheckpointSaverTag()
     */
    public Map<String,Object> asLastCheckpointStateData() {
        return asCheckpointSaverTag().lastCheckpoint()
                    .map( Checkpoint::state )
                    .map(state -> AgentState.filterBySchema(state, outputSchema))
                    .orElseThrow(() -> new IllegalStateException("Checkpoint saver tag doesn't contain any checkpoint"));
    }

    /**
     * Checks if the result carries state, either directly as state data or indirectly
     * through a checkpoint saver tag.
     *
     * @return true if the result is state data or a checkpoint saver tag, false otherwise.
     * @see #asStateDataOrLastCheckpointStateData()
     * @deprecated Use {@link #isStateData()}.
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
    public boolean isStateDataOrCheckpointSaverTag() {
        return isStateData();
    }

    /**
     * Retrieves the state data regardless of whether the result holds it directly or
     * through a checkpoint saver tag.
     * <p>
     * If the result is state data, it is returned filtered by the output schema; otherwise
     * the state of the last checkpoint of the tag is returned.
     * </p>
     *
     * @return the state data map.
     * @throws IllegalStateException if the result is neither state data nor a checkpoint saver tag,
     *         or if the tag does not contain any checkpoint.
     * @see #isStateDataOrCheckpointSaverTag()
     * @see #asLastCheckpointStateData()
     * @deprecated Use {@link #asStateData()} instead for clarity.
     */
    @Deprecated( since = "1.9.8", forRemoval = true )
    public Map<String,Object> asStateDataOrLastCheckpointStateData() {
        if( isStateData() ) {
            return asStateData();
        }
        return asLastCheckpointStateData();
    }

    @Override
    public @NonNull String toString() {
        if( isStateData() ) {
            return CollectionsUtils.toString(asStateData());
        }
        return Objects.toString(result);

    }

}