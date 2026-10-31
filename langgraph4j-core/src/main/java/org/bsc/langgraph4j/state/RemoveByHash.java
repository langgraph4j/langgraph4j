package org.bsc.langgraph4j.state;

import java.util.Objects;

/**
 * Represents a record that implements the {@link AppenderChannel.RemoveIdentifier<T>} interface.
 *
 * @param <T> the type of the value to be associated with this RemoveByHash instance
 */
public record RemoveByHash<T>(T value ) implements AppenderChannel.RemoveIdentifier<T> {
    /**
     * Checks whether the given element is the value this identifier refers to.
     * <p>
     * The comparison is by value ({@link Objects#equals(Object, Object)}), not by hash
     * code: two distinct values can share a hash code (e.g. {@code "Aa"} and {@code "BB"}
     * both hash to 2112), and comparing hashes would treat them as the same element.
     * Removing one of them would then silently remove the unrelated one as well.
     *
     * @param element the element to compare with
     * @param atIndex the index of the element in the context (ignored in comparison)
     * @return zero if the element is the referenced value, a positive value otherwise
     */
    @Override
    public int compareTo(T element, int atIndex) {
        return Objects.equals(value, element) ? 0 : 1;
    }

    /**
     * Creates a new {@code RemoveByHash} instance with the specified value.
     *
     * @param <T>   the type of the value
     * @param value the value to store in the {@code RemoveByHash}
     * @return a new {@code RemoveByHash} instance
     */
    public static <T> RemoveByHash<T> of ( T value ) {
        return new RemoveByHash<>(value);
    }
}
