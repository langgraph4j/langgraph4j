package org.bsc.langgraph4j.state;

import java.util.function.BiFunction;

/**
 * Represents a binary operator that combines two values of the same type into a single value.
 * In LangGraph4j, a Reducer is invoked by a {@link Channel} to merge a new value
 * with the channel's current value. The first argument (old value) is the channel's
 * current value (or its default if the channel has not been written to yet); the
 * second argument (new value) is the value written by the current node.
 *
 * <p>Example built-in reducers:
 * <ul>
 *   <li>{@link AppenderChannel} - appends new values to a list</li>
 *   <li>{@link RemoveByHash} - removes a value from a list by identity comparison</li>
 * </ul>
 *
 * <p><b>Null handling:</b> implementations must decide how to handle null values.
 * A common pattern is to treat null as "no change" (return the old value unchanged)
 * or to treat it as a signal to remove the channel. Consult the specific
 * implementation's documentation.
 *
 * <p><b>Thread safety:</b> a single Reducer instance may be invoked concurrently
 * by multiple threads during parallel graph execution. Reducers should be stateless
 * or use appropriate synchronization.
 *
 * @param <T> the type of the values to combine
 * @see BiFunction
 * @see AppenderChannel
 * @see RemoveByHash
 */
public interface Reducer<T> extends BiFunction<T,T,T> {
}