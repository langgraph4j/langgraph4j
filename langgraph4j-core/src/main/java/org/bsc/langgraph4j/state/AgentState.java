package org.bsc.langgraph4j.state;

import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.entryOf;

/**
 * Represents the state of an agent with a map of data.
 */
public class AgentState {
    public static final Object MARK_FOR_RESET = new Object() {
        @Override
        public String toString() { return "MARK_FOR_RESET"; }
    };
    public static final Object MARK_FOR_REMOVAL = new Object() {
        @Override
        public String toString() { return "MARK_FOR_REMOVAL"; }
    };

    private final java.util.Map<String,Object> data;

    /**
     * Constructs an AgentState with the given initial data.
     *
     * @param initData the initial data for the agent state
     */
    public AgentState(Map<String,Object> initData) {
        this.data = new HashMap<>(initData);
    }

    /**
     * Returns an unmodifiable view of the data map.
     *
     * @return an unmodifiable map of the data
     */
    public final java.util.Map<String,Object> data() {
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
    public final <T> Optional<T> value(String key) { return ofNullable((T) data().get(key));}

    /**
     * Returns a string representation of the agent state.
     *
     * @return a string representation of the data map
     */
    @Override
    public String toString() {
        return CollectionsUtils.toString(data);
    }

    private static Collector<Map.Entry<String,Object>, ?, Map<String, Object>> toMapRemovingItemMarkedForRemoval() {
        final BinaryOperator<Object> mergeFunction = ( currentValue, newValue ) -> newValue;

        return Collector.of(
                HashMap::new,
                (map, element) -> {
                    var key     = element.getKey();
                    var value   = element.getValue();
                    if( value == null || value == MARK_FOR_RESET || value == MARK_FOR_REMOVAL) {
                        map.remove(key);
                    }
                    else {
                        map.merge(key, value, mergeFunction);
                    }
                },
                (map1, map2) -> {
                    map2.forEach( (key, value) -> {
                        if ( value != null && value != MARK_FOR_RESET && value != MARK_FOR_REMOVAL) {
                            map1.merge(key, value, mergeFunction);
                        }
                    });
                    return map1;
                },
                Collector.Characteristics.UNORDERED);
    }


    private static Collector<Map.Entry<String,Object>, ?, Map<String, Object>> toMapAllowingNulls() {
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
    private static Map<String,Object> updatePartialStateFromSchema(  Map<String,Object> state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        if( channels == null || channels.isEmpty() ) {
            return partialState;
        }
        return partialState.entrySet().stream().map( entry -> {

            Channel<?> channel = channels.get(entry.getKey());
            if (channel != null) {
                Object newValue = channel.update( entry.getKey(), state.get(entry.getKey()), entry.getValue());
                return entryOf(entry.getKey(), newValue);
            }

            return entry;
        })
        .collect(toMapAllowingNulls());
    }


    /**
     * Updates a state with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     *
     * @param state the current state
     * @param partialState the partial state to update from
     * @param channels the channels used to update the partial state if necessary
     * @return the updated state
     * @throws NullPointerException if state is null
     */
    public static Map<String,Object> updateState( Map<String,Object> state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        Objects.requireNonNull(state, "state cannot be null");
        if (partialState == null || partialState.isEmpty() || state == partialState ) {
            return state;
        }

        Map<String, Object> updatedPartialState = updatePartialStateFromSchema(state, partialState, channels);

        return  Stream.concat( state.entrySet().stream(), updatedPartialState.entrySet().stream())
                .collect(toMapRemovingItemMarkedForRemoval());
    }

    /**
     * Updates a state with the provided partial state.
     * The merge function is used to merge the current state value with the new value.
     *
     * @param state the current state
     * @param partialState the partial state to update from
     * @param channels the channels used to update the partial state if necessary
     * @return the updated state
     * @throws NullPointerException if state is null
     */
    public static Map<String,Object> updateState( AgentState state, Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        return updateState(state.data(), partialState, channels);
    }

    /**
     * Returns the value associated with the specified key or a default value if the key is not present.
     *
     * @param key The key whose associated value is to be returned.
     * @param defaultValue The value to use if no entry for the specified key is found.
     * @param <T> the type of the value
     * @return The value to which the specified key is mapped, or {@code defaultValue} if this map contains no mapping for the key.
     * @deprecated This method is deprecated and may be removed in future versions.
     */
    @Deprecated(forRemoval = true)
    public final <T> T value(String key, T defaultValue ) { return this.<T>value(key).orElse(defaultValue);}

    /**
     * Returns the value associated with the given key or a default value if no such key exists.
     *
     * @param key The key to retrieve the value for.
     * @param defaultProvider A provider function that returns the default value if the key is not found.
     * @param <T> the type of the value
     * @return The value associated with the key, or the default value provided by {@code defaultProvider}.
     */
    @Deprecated(forRemoval = true)
    public final <T> T value(String key, Supplier<T>  defaultProvider ) { return this.<T>value(key).orElseGet(defaultProvider); }

    /**
     * Merges the current state with a partial state and returns a new state.
     *
     * @param partialState the partial state to merge with
     * @param channels the channels used to update the partial state if necessary
     * @return a new state resulting from the merge
     * @deprecated use {@link #updateState(AgentState, Map, Map)}
     */
    @Deprecated(forRemoval = true)
    public final Map<String,Object> mergeWith( Map<String,Object> partialState, Map<String, Channel<?>> channels ) {
        return updateState(data(), partialState, channels);
    }

}