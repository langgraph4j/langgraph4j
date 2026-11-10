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
    void savedAtIsOptional() {
        // A saver that cannot pre-fill the time hands it over as null, which is kept
        // instead of being replaced by the generated default.
        final var checkpoint = new Checkpoint("id", Map.of(), "node", "next", null);

        assertFalse(checkpoint.hasSavedAt());
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

        assertFalse(checkpoint.hasSavedAt());
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
