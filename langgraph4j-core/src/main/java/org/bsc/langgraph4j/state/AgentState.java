package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.entryOf;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Represents the state of an agent with a map of data.
 */
public class AgentState {
    /**
     * Experimental Interface representing a schema for an agent's state, defining the channels and their configurations.
     * This schema is used to validate and manage the state of an agent in a structured manner.
     *
     * @since 1.9.8
     */
    public sealed interface Schema permits SchemaImpl {

        static Schema emptySchema( ) {
            return new SchemaImpl(Map.of());
        }

        static Schema overallSchema( Map<String, Channel<?>> channels ) {
            return new SchemaImpl(channels);
        }

        static Schema filterSchema( String ...allowChannelNames ) {
            return new SchemaImpl(Arrays.stream(allowChannelNames)
                    .collect(Collectors.toMap(name -> name, name -> Channels.bare())));
        }

        Map<String, Channel<?>> channels();

        default Schema merge(Map<String, Channel<?>> other ) {
            return new SchemaImpl(mergeMap( this.channels(), other, (a,b) -> a ));
        }

        default Schema merge(Schema other ) {
            return merge( other.channels() );
        }
    }


    /**
     * Marker value that resets a state entry through its configured {@link Channel}.
     * <p>
     * When used as a value in a partial state update, the current value is replaced
     * with the channel's default value. If the channel has no default value, or no
     * channel is configured for the key, the key is removed from the resulting state.
     * The marker is handled before any configured {@link Reducer} is invoked.
     * <p>
     * This marker is recognized by identity; use this constant directly.
     *
     * @see Channel#update(String, Object, Object)
     */
    public static final Object MARK_FOR_RESET = new Object() {
        @Override
        public String toString() { return "MARK_FOR_RESET"; }
    };

    /**
     * Marker value that removes a state entry.
     * <p>
     * When used as a value in a partial state update, the corresponding key is
     * excluded from the resulting state. The marker is handled before any configured
     * {@link Reducer} is invoked.
     * <p>
     * This marker is recognized by identity; use this constant directly.
     *
     * @see Channel#update(String, Object, Object)
     */
    public static final Object MARK_FOR_REMOVAL = new Object() {
        @Override
        public String toString() { return "MARK_FOR_REMOVAL"; }
    };

    private static Collector<Map.Entry<String, Object>, ?, Map<String, Object>> toMapRemovingItemMarkedForRemoval() {
        final BinaryOperator<Object> mergeFunction = (currentValue, newValue) -> newValue;

        return Collector.of(
                HashMap::new,
                (map, element) -> {
                    var key = element.getKey();
                    var value = element.getValue();
                    if (value == null || value == MARK_FOR_RESET || value == MARK_FOR_REMOVAL) {
                        map.remove(key);
                    } else {
                        map.merge(key, value, mergeFunction);
                    }
                },
                (map1, map2) -> {
                    map2.forEach((key, value) -> {
                        if (value != null && value != MARK_FOR_RESET && value != MARK_FOR_REMOVAL) {
                            map1.merge(key, value, mergeFunction);
                        }
                    });
                    return map1;
                },
                Collector.Characteristics.UNORDERED);
    }

    private static Collector<Map.Entry<String, Object>, ?, Map<String, Object>> toMapAllowingNulls() {
        return Collector.of(
                HashMap::new,
                (map, element) -> map.put(element.getKey(), element.getValue()),
                (map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                },
                Collector.Characteristics.UNORDERED);
    }

    /**
     * Updates the partial state from a schema using channels.
     *
     * @param state        The current state as a map of key-value pairs.
     * @param partialState The partial state to be updated.
     * @param channels     A map of channel names to their implementations.
     * @return An updated version of the partial state after applying the schema and channels.
     */
    private static Map<String, Object> updatePartialStateFromSchema(@NonNull Map<String, Object> state, @NonNull Map<String, Object> partialState, @Nullable Map<String, Channel<?>> channels) {
        if (channels == null || channels.isEmpty()) {
            return partialState;
        }
        return partialState.entrySet().stream().map(entry -> {

                    Channel<?> channel = channels.get(entry.getKey());
                    if (channel != null) {
                        Object newValue = channel.update(entry.getKey(), state.get(entry.getKey()), entry.getValue());
                        return entryOf(entry.getKey(), newValue);
                    }

                    return entry;
                })
                .collect(toMapAllowingNulls());
    }

    /**
     * Updates a state with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     * If {@code channels} is {@code null} or empty, channel resolution is skipped;
     * the partial state is still merged into the current state, including the standard
     * handling for reset and removal markers.
     *
     * @param state        the current state
     * @param partialState the partial state to update from
     * @param channels     the channels used to update matching partial-state values; may be
     *                     {@code null} or empty
     * @return the updated state
     * @throws NullPointerException if state is null
     */
    public static Map<String, Object> updateState(@NonNull Map<String, Object> state, @Nullable Map<String, Object> partialState, @Nullable Map<String, Channel<?>> channels) {
        Objects.requireNonNull(state, "state cannot be null");
        if (partialState == null || partialState.isEmpty() || state == partialState) {
            return state;
        }

        final var updatedPartialState = updatePartialStateFromSchema(state, partialState, channels);

        return Stream.concat(state.entrySet().stream(), updatedPartialState.entrySet().stream())
                .collect(toMapRemovingItemMarkedForRemoval());
    }

    /**
     * Updates a state with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     * If {@code channels} is {@code null} or empty, channel resolution is skipped;
     * the partial state is still merged into the current state, including the standard
     * handling for reset and removal markers.
     *
     * @param state        the current state
     * @param partialState the partial state to update from
     * @param schema       the schema used to update matching partial-state values; may be
     *                     {@code null} or empty
     * @return the updated state
     * @throws NullPointerException if state is null
     */
    public static Map<String, Object> updateState(@NonNull Map<String, Object> state, @Nullable Map<String, Object> partialState, @Nullable Schema schema) {
        return updateState(state, partialState, schema != null ? schema.channels() : null);
    }


    /**
     * Filters the state to include only the specified attribute names.
     *
     * @param state          the current state
     * @param schema the input/output schema to include in the filtered state
     * @return a new map containing only the specified attributes from the state
     * @throws NullPointerException if state is null
     */
    public static Map<String, Object> filterBySchema(@NonNull Map<String, Object> state, @Nullable Schema schema) {
        Objects.requireNonNull(state, "state cannot be null");
        if (schema == null || schema.channels().isEmpty()) {
            return state;
        }

        return state.entrySet().stream()
                .filter(entry -> schema.channels().containsKey(entry.getKey()))
                .collect(toMapAllowingNulls());
    }

    /**
     * Updates a state with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     * If {@code channels} is {@code null} or empty, channel resolution is skipped;
     * the partial state is still merged into the current state, including the standard
     * handling for reset and removal markers.
     *
     * @param state        the current state
     * @param partialState the partial state to update from
     * @param channels     the channels used to update matching partial-state values; may be
     *                     {@code null} or empty
     * @return the updated state
     * @throws NullPointerException if state is null
     * @deprecated Use {@link #update(Map, Map)} instead.
     */
    @Deprecated(
            since = "1.9.8",
            forRemoval = true
    )
    public static Map<String, Object> updateState(AgentState state, Map<String, Object> partialState, Map<String, Channel<?>> channels) {
        return updateState(state.data(), partialState, channels);
    }

    private final java.util.Map<String, Object> data;

    /**
     * Constructs an AgentState with the given initial data.
     *
     * @param initData the initial data for the agent state
     */
    public AgentState( Map<String, Object> initData ) {
        this.data = new HashMap<>(initData);
    }

    /**
     * Returns an unmodifiable view of the data map.
     *
     * @return an unmodifiable map of the data
     */
    public final java.util.Map<String, Object> data() {
        return unmodifiableMap(data);
    }


    /**
     * Retrieves the value associated with the given key, if present.
     *
     * @param key the key whose associated value is to be returned
     * @param <T> the type of the value
     * @return an Optional containing the value if present, otherwise an empty Optional
     */
    @SuppressWarnings("unchecked")
    public final <T> Optional<T> value( String key ) {
        return ofNullable((T) data().get(key));
    }

    /**
     * Returns a string representation of the agent state.
     *
     * @return a string representation of the data map
     */
    @Override
    public String toString() {
        return CollectionsUtils.toString(data);
    }

    /**
     * Provides a new state's data as result of the  state updates with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     * If {@code channels} is {@code null} or empty, channel resolution is skipped;
     * the partial state is still merged into the current state, including the standard
     * handling for reset and removal markers.
     *
     * @param partialState the partial state to update from
     * @param schema     the channels used to update matching partial-state values; may be
     *                     {@code null} or empty
     * @return a new copy of the updated state's data
     * @since 1.9.8
     */
    public Map<String, Object> update(Map<String, Object> partialState, @Nullable Schema schema) {
        return updateState(this.data(), partialState, schema != null ? schema.channels() : null);
    }

    /**
     * Provides a new state's data as result of the  state updates with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     * If {@code channels} is {@code null} or empty, channel resolution is skipped;
     * the partial state is still merged into the current state, including the standard
     * handling for reset and removal markers.
     *
     * @param partialState the partial state to update from
     * @param channels     the channels used to update matching partial-state values; may be
     *                     {@code null} or empty
     * @return a new copy of the updated state's data
     * @since 1.9.8
     */
    public Map<String, Object> update(Map<String, Object> partialState, @Nullable Map<String, Channel<?>> channels) {
        return updateState(this.data(), partialState, channels);
    }

    /**
     * Provides a new state's data as result of filtering the state data to include only the specified attribute names.
     *
     * @param schema the input schema to include in the filtered state
     * @return a new map containing only the specified attributes from the state data
     * @since 1.9.8
     */
    public Map<String, Object> filterBySchema(@Nullable Schema schema) {
        return filterBySchema(this.data(), schema);
    }
}


record SchemaImpl(Map<String, Channel<?>> channels) implements AgentState.Schema {

    SchemaImpl(Map<String, Channel<?>> channels) {
        this.channels = requireNonNullElse(channels, Map.of());
    }


}
