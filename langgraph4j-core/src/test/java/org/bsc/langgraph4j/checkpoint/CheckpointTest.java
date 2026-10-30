package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the creation date support added to {@link Checkpoint} ( see issue #234 )
 */
public class CheckpointTest {

    private static final Instant FIXED_DATE = Instant.parse("2025-09-10T10:37:31Z");

    @Test
    void createdAtDefaultsToNowOnConstructor() {
        // createdAt is truncated to milliseconds, so the lower bound is truncated too;
        // otherwise Instant.now()'s stray nanos would make createdAt look "before" it.
        final var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final var checkpoint = new Checkpoint("id", Map.of(), "node", "next");
        final var after = Instant.now();

        assertNotNull(checkpoint.createdAt());
        assertFalse(checkpoint.createdAt().isBefore(before), "createdAt should not be before the construction");
        assertFalse(checkpoint.createdAt().isAfter(after), "createdAt should not be after the construction");
    }

    @Test
    void createdAtDefaultsToNowOnBuilder() {
        // createdAt is truncated to milliseconds, so the lower bound is truncated too;
        // otherwise Instant.now()'s stray nanos would make createdAt look "before" it.
        final var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final var checkpoint = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .build();
        final var after = Instant.now();

        assertNotNull(checkpoint.createdAt());
        assertFalse(checkpoint.createdAt().isBefore(before), "createdAt should not be before the construction");
        assertFalse(checkpoint.createdAt().isAfter(after), "createdAt should not be after the construction");
    }

    @Test
    void createdAtIsMandatory() {
        assertThrows(NullPointerException.class,
                () -> new Checkpoint("id", Map.of(), "node", "next", null));
    }

    @Test
    void createdAtDefaultsToMillisecondPrecision() {
        // Instant.now() carries nanosecond precision, which most databases cannot
        // round-trip. The generated creation date is truncated to milliseconds so a
        // stored checkpoint compares equal to the one held in memory.
        final var fromConstructor = new Checkpoint("id", Map.of(), "node", "next");
        final var fromBuilder = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .build();
        final var copy = Checkpoint.copyOf(fromConstructor);

        assertEquals(0, fromConstructor.createdAt().getNano() % 1_000_000,
                "constructor default should be truncated to milliseconds");
        assertEquals(0, fromBuilder.createdAt().getNano() % 1_000_000,
                "builder default should be truncated to milliseconds");
        assertEquals(0, copy.createdAt().getNano() % 1_000_000,
                "copyOf should be truncated to milliseconds");
    }

    @Test
    void explicitCreatedAtIsPreserved() {
        // A caller-supplied instant is used as-is; only the generated default is truncated.
        final var explicit = Instant.parse("2025-09-10T10:37:31.123456789Z");
        final var checkpoint = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .createdAt(explicit)
                .build();

        assertEquals(explicit, checkpoint.createdAt());
    }

    @Test
    void updateStatePreservesCreatedAt() {
        final var checkpoint = Checkpoint.builder()
                .id("id")
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of("prop1", "value1"))
                .createdAt(FIXED_DATE)
                .build();

        final var updated = checkpoint.updateState(
                Map.of("prop2", "value2"),
                AgentState.Schema.emptySchema());

        assertEquals(FIXED_DATE, updated.createdAt());
        assertEquals(checkpoint.id(), updated.id());
        assertEquals("next", updated.nextNodeId());
    }

    @Test
    void copyOfRefreshesCreatedAt() {
        final var checkpoint = Checkpoint.builder()
                .id("id")
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of("prop1", "value1"))
                .createdAt(FIXED_DATE)
                .build();

        final var copy = Checkpoint.copyOf(checkpoint);

        assertNotEquals(checkpoint.id(), copy.id());
        assertFalse(copy.createdAt().isBefore(FIXED_DATE), "copied checkpoint must carry a fresh creation date");
    }

}
