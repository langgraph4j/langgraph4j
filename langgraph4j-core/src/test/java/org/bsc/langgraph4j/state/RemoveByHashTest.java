package org.bsc.langgraph4j.state;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Removing one value from an appendable list must not remove a different value.
 *
 * <p>{@link RemoveByHash} identifies its target by hash code only, so every value that
 * shares that hash code is removed as well. Java's own {@code String.hashCode} collides
 * for "Aa" and "BB", hence a state holding both loses both when only one is removed.
 */
public class RemoveByHashTest {

    private static final Map<String, Channel<?>> CHANNELS = Map.of(
            "messages", Channels.appender(ArrayList::new));

    @Test
    public void removeByHashShouldNotRemoveAValueWithTheSameHashCode() {

        // guard: the two values are distinct, they only share the hash code
        assertEquals("Aa".hashCode(), "BB".hashCode());

        final var state = Map.<String, Object>of("messages", List.of("Aa", "BB"));
        final var partial = Map.<String, Object>of("messages", RemoveByHash.of("Aa"));

        final var updated = AgentState.updateState(state, partial, CHANNELS);

        assertEquals(List.of("BB"), updated.get("messages"));
    }

    @Test
    public void removeByHashShouldRemoveTheReferencedValue() {

        final var state = Map.<String, Object>of("messages", List.of("message1", "message2"));
        final var partial = Map.<String, Object>of("messages", RemoveByHash.of("message2"));

        final var updated = AgentState.updateState(state, partial, CHANNELS);

        assertEquals(List.of("message1"), updated.get("messages"));
    }
}
