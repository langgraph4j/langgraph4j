package org.bsc.langgraph4j.checkpoint;

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
    void savedAtDefaultsToNowOnConstructor() {
        // savedAt is truncated to milliseconds, so the lower bound is truncated too;
        // otherwise Instant.now()'s stray nanos would make savedAt look "before" it.
        final var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final var checkpoint = new Checkpoint("id", Map.of(), "node", "next");
        final var after = Instant.now();

        assertNotNull(checkpoint.savedAt());
        assertFalse(checkpoint.savedAt().isBefore(before), "savedAt should not be before the construction");
        assertFalse(checkpoint.savedAt().isAfter(after), "savedAt should not be after the construction");
    }

    @Test
    void savedAtDefaultsToNowOnBuilder() {
        // savedAt is truncated to milliseconds, so the lower bound is truncated too;
        // otherwise Instant.now()'s stray nanos would make savedAt look "before" it.
        final var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final var checkpoint = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .build();
        final var after = Instant.now();

        assertNotNull(checkpoint.savedAt());
        assertFalse(checkpoint.savedAt().isBefore(before), "savedAt should not be before the construction");
        assertFalse(checkpoint.savedAt().isAfter(after), "savedAt should not be after the construction");
    }

    @Test
    void savedAtIsOptional() {
        // A saver that cannot pre-fill the time hands it over as null, which is kept
        // instead of being replaced by the generated default.
        final var checkpoint = new Checkpoint("id", Map.of(), "node", "next", null);

        assertNull(checkpoint.savedAt());
        assertNull(checkpoint.createdAt(), "the deprecated alias should read through to savedAt");
    }

    @Test
    void savedAtAcceptsNullThroughTheBuilder() {
        // Same for the builder: null means "the storage did not give us a time" and
        // must not fall back to Instant.now().
        final var checkpoint = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .savedAt((Instant) null)
                .build();

        assertNull(checkpoint.savedAt());
    }

    @Test
    void deprecatedCreatedAtAliasSetsSavedAt() {
        // Savers written against 1.9.6/1.9.7 ( eg. MongoDBSaver ) still call createdAt.
        final var checkpoint = Checkpoint.builder()
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of())
                .createdAt(FIXED_DATE)
                .build();

        assertEquals(FIXED_DATE, checkpoint.savedAt());
    }

    @Test
    void savedAtDefaultsToMillisecondPrecision() {
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

        assertEquals(0, fromConstructor.savedAt().getNano() % 1_000_000,
                "constructor default should be truncated to milliseconds");
        assertEquals(0, fromBuilder.savedAt().getNano() % 1_000_000,
                "builder default should be truncated to milliseconds");
        assertEquals(0, copy.savedAt().getNano() % 1_000_000,
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
                .savedAt(explicit)
                .build();

        assertEquals(explicit, checkpoint.savedAt());
    }

    @Test
    void updateStatePreservesCreatedAt() {
        final var checkpoint = Checkpoint.builder()
                .id("id")
                .nodeId("node")
                .nextNodeId("next")
                .state(Map.of("prop1", "value1"))
                .savedAt(FIXED_DATE)
                .build();

        final var updated = checkpoint.updateState(Map.of("prop2", "value2"), null);

        assertEquals(FIXED_DATE, updated.savedAt());
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
                .savedAt(FIXED_DATE)
                .build();

        final var copy = Checkpoint.copyOf(checkpoint);

        assertNotEquals(checkpoint.id(), copy.id());
        assertFalse(copy.savedAt().isBefore(FIXED_DATE), "copied checkpoint must carry a fresh creation date");
    }

}
