package org.bsc.langgraph4j.state;

import java.util.Objects;

/**
 * Represents a record that implements the {@link AppenderChannel.RemoveIdentifier<T>} interface,
 * identifying its target by value instead of by hash code.
 *
 * <p>Use this identifier when exactly the referenced value has to be removed. Two distinct values
 * can share a hash code — {@code "Aa"} and {@code "BB"} both hash to 2112 — so a hash-based
 * identifier reports zero for both of them and the unrelated one is removed as well. See
 * {@link RemoveByHash} for that behaviour.
 *
 * @param <T> the type of the value to be associated with this RemoveEquals instance
 */
public record RemoveEquals<T>(T value ) implements AppenderChannel.RemoveIdentifier<T> {
    /**
     * Checks whether the given element is the value this identifier refers to.
     *
     * @param element the element to compare with
     * @param atIndex the index of the element in the context (ignored in comparison)
     * @return zero if the element is equal to the referenced value, a positive value otherwise
     */
    @Override
    public int compareTo(T element, int atIndex) {
        return Objects.equals(value, element) ? 0 : 1;
    }

    /**
     * Creates a new {@code RemoveEquals} instance with the specified value.
     *
     * @param <T>   the type of the value
     * @param value the value to store in the {@code RemoveEquals}
     * @return a new {@code RemoveEquals} instance
     */
    public static <T> RemoveEquals<T> of ( T value ) {
        return new RemoveEquals<>(value);
    }
}
