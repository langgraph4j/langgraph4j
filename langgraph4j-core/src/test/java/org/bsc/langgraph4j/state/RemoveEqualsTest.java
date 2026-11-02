package org.bsc.langgraph4j.state;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RemoveEquals} removes the value it refers to and nothing else.
 *
 * <p>The first case is the reason this identifier exists: {@code String.hashCode} collides for
 * "Aa" and "BB", so a hash-based identifier would remove both of them.
 */
public class RemoveEqualsTest {

    private static final Map<String, Channel<?>> CHANNELS = Map.of(
            "messages", Channels.appender(ArrayList::new));

    @Test
    public void removeEqualsShouldNotRemoveAValueWithTheSameHashCode() {

        // guard: the two values are distinct, they only share the hash code
        assertEquals("Aa".hashCode(), "BB".hashCode());

        final var state = Map.<String, Object>of("messages", List.of("Aa", "BB"));
        final var partial = Map.<String, Object>of("messages", RemoveEquals.of("Aa"));

        final var updated = AgentState.updateState(state, partial, CHANNELS);

        assertEquals(List.of("BB"), updated.get("messages"));
    }

    @Test
    public void removeEqualsShouldRemoveTheReferencedValue() {

        final var state = Map.<String, Object>of("messages", List.of("message1", "message2"));
        final var partial = Map.<String, Object>of("messages", RemoveEquals.of("message2"));

        final var updated = AgentState.updateState(state, partial, CHANNELS);

        assertEquals(List.of("message1"), updated.get("messages"));
    }

    @Test
    public void removeEqualsShouldBeUsableFromAListOfPartialUpdates() {

        final var state = Map.<String, Object>of("messages", List.of("message1", "message2"));
        final var partial = Map.<String, Object>of(
                "messages", List.of("message3", RemoveEquals.of("message2")));

        final var updated = AgentState.updateState(state, partial, CHANNELS);

        assertEquals(List.of("message1", "message3"), updated.get("messages"));
    }
}
