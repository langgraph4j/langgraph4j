package org.bsc.langgraph4j.checkpoint;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DynamoDBRepository} key generation functions.
 *
 * <p>These tests verify the single-table design key patterns without requiring
 * a DynamoDB instance.
 */
class DynamoDBRepositoryTest {

    @Test
    void testCheckpointPK() {
        assertEquals("CHECKPOINT_t1", DynamoDBRepository.checkpointPK("t1"));
        assertEquals("CHECKPOINT_thread-abc-123", DynamoDBRepository.checkpointPK("thread-abc-123"));
    }

    @Test
    void testCheckpointPKWithGeneration() {
        // generation 0 keeps the original key format
        assertEquals("CHECKPOINT_t1", DynamoDBRepository.checkpointPK("t1", 0));
        assertEquals("CHECKPOINT_t1#1", DynamoDBRepository.checkpointPK("t1", 1));
        assertEquals("CHECKPOINT_t1#42", DynamoDBRepository.checkpointPK("t1", 42));
        assertEquals("CHECKPOINT_t1#" + Long.MAX_VALUE, DynamoDBRepository.checkpointPK("t1", Long.MAX_VALUE));
    }

    @Test
    void testGenerationOf() {
        final var pk = AttributeValue.builder().s("RELEASED_t1").build();
        final var sk = AttributeValue.builder().s("MARKER").build();

        // no marker: never released
        assertEquals(0L, DynamoDBRepository.generationOf(null));
        // legacy marker without the 'generation' attribute
        assertEquals(1L, DynamoDBRepository.generationOf(Map.of("PK", pk, "SK", sk)));
        // current marker
        assertEquals(7L, DynamoDBRepository.generationOf(Map.of("PK", pk, "SK", sk,
                DynamoDBRepository.GENERATION_ATTRIBUTE, AttributeValue.builder().n("7").build())));
    }

    @Test
    void testCheckpointSK() {
        assertEquals("cp-001", DynamoDBRepository.checkpointSK("cp-001"));
    }

    @Test
    void testChunkPK() {
        assertEquals("CHUNK_t1#cp1", DynamoDBRepository.chunkPK("t1", "cp1"));
        assertEquals("CHUNK_thread-abc#checkpoint-xyz",
                DynamoDBRepository.chunkPK("thread-abc", "checkpoint-xyz"));
    }

    @Test
    void testChunkSK() {
        assertEquals("CHUNK", DynamoDBRepository.chunkSK());
    }

    @Test
    void testReleasedPK() {
        assertEquals("RELEASED_t1", DynamoDBRepository.releasedPK("t1"));
    }

    @Test
    void testReleasedSK() {
        assertEquals("MARKER", DynamoDBRepository.releasedSK());
    }

    @Test
    void testCheckpointRecordWithParentAndRefFields() {
        var record = new DynamoDBRepository.CheckpointRecord(
                "thread-1", "cp-123", "node_a", "node_b",
                new byte[]{1, 2, 3}, "application/octet-stream", 1000L,
                "cp-parent", "DYNAMODB", "CHUNK_thread-1#cp-123"
        );

        assertEquals("thread-1", record.threadId());
        assertEquals("cp-123", record.checkpointId());
        assertEquals("cp-parent", record.parentCheckpointId());
        assertEquals("DYNAMODB", record.refLoc());
        assertEquals("CHUNK_thread-1#cp-123", record.refKey());
    }

    @Test
    void testCheckpointRecordNullableParent() {
        var record = new DynamoDBRepository.CheckpointRecord(
                "thread-1", "cp-001", "start", "__end__",
                new byte[0], "text/plain", 500L,
                null, "DYNAMODB", "CHUNK_thread-1#cp-001"
        );

        assertNull(record.parentCheckpointId());
        assertEquals("DYNAMODB", record.refLoc());
    }
}
