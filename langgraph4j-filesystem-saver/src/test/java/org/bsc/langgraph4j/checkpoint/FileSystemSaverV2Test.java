/*
 * Exercises file-backed checkpoint persistence and release for supported state serializers.
 * The test owns a temporary directory and does not depend on a shared checkpoint store.
 */
package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that both JSON and binary state serializers survive a file system round trip.
 * Each invocation receives its own directory, so releases cannot affect another test.
 */
class FileSystemSaverV2Test {

    /** Serializer formats supported by the saver envelope. */
    enum StateSerializerEnum {
        /** Java object stream payload stored as text inside the JSON envelope. */
        BINARY(new ObjectStreamStateSerializer<>(AgentState::new)),
        /** JSON payload stored as a nested value inside the JSON envelope. */
        JSON(new JacksonStateSerializer<>(AgentState::new));

        /** Serializer used by this parameterized test invocation. */
        final StateSerializer<AgentState> stateSerializer;

        /** Associates the format with the serializer used to persist state. */
        StateSerializerEnum(StateSerializer<AgentState> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    /** Isolated checkpoint directory supplied by JUnit for each test invocation. */
    @TempDir
    Path targetFolder;

    /**
     * Persists a checkpoint, releases it, and loads the released version from a new saver.
     *
     * @param format state serialization format to exercise; never null
     * @throws Exception if persistence or deserialization fails
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void serializeTest(StateSerializerEnum format) throws Exception {
        final var config = RunnableConfig.builder().threadId("test-1").build();
        final var saver = new FileSystemSaverV2(targetFolder, format.stateSerializer);
        final var checkpoint = Checkpoint.builder()
                .id("cp1")
                .nodeId("node1")
                .nextNodeId("node2")
                .state(new AgentState(Map.of()))
                .build();

        // Reopen the saver to verify that reads use files rather than in-memory state.
        saver.put(config, checkpoint);
        final var reloaded = new FileSystemSaverV2(targetFolder, format.stateSerializer);
        assertEquals("cp1", reloaded.get(config).orElseThrow().id());

        // Release moves the active file into a numbered archive that tag() can reload.
        final var released = reloaded.release(config);
        assertEquals(1, released.version().orElseThrow());
        assertEquals("cp1", released.lastCheckpoint().orElseThrow().id());
        assertFalse(Files.exists(targetFolder.resolve("thread-test-1.json")));
        assertTrue(Files.exists(targetFolder.resolve("released/thread-test-1-v1.json")));
        assertEquals("cp1", reloaded.tag(config, 1).orElseThrow().lastCheckpoint().orElseThrow().id());
    }
}
