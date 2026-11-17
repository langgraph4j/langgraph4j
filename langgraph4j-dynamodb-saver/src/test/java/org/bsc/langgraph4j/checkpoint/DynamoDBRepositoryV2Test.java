package org.bsc.langgraph4j.checkpoint;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Unit tests for the {@link DynamoDBRepositoryV2} key patterns and item mapping.
 * They do not require a DynamoDB instance.
 */
class DynamoDBRepositoryV2Test {

    @Test
    void testThreadPK() {
        assertEquals("THREAD_t1", DynamoDBRepositoryV2.threadPK("t1"));
        assertEquals("THREAD_$default", DynamoDBRepositoryV2.threadPK(BaseCheckpointSaver.THREAD_ID_DEFAULT));
    }

    @Test
    void testTagSK() {
        assertEquals("V#0000000001", DynamoDBRepositoryV2.tagSK(1));
        assertEquals("V#0000000042", DynamoDBRepositoryV2.tagSK(42));
        assertEquals("V#" + Integer.MAX_VALUE, DynamoDBRepositoryV2.tagSK(Integer.MAX_VALUE));
        assertEquals(42, DynamoDBRepositoryV2.versionOf("V#0000000042"));
    }

    @Test
    void testTagSKSortsByVersion() {
        // DynamoDB sorts string keys lexicographically: zero padding keeps numeric order
        assertTrue(DynamoDBRepositoryV2.tagSK(9).compareTo(DynamoDBRepositoryV2.tagSK(10)) < 0);
        assertTrue(DynamoDBRepositoryV2.tagSK(99).compareTo(DynamoDBRepositoryV2.tagSK(100)) < 0);
        // ACTIVE never matches the tag prefix
        assertFalse(DynamoDBRepositoryV2.ACTIVE_SK.startsWith(DynamoDBRepositoryV2.TAG_SK_PREFIX));
    }

    @Test
    void testCheckpointAndChunkKeys() {
        assertEquals("CHECKPOINT_u1", DynamoDBRepositoryV2.checkpointPK("u1"));
        assertEquals("CHUNK_u1#cp1", DynamoDBRepositoryV2.chunkPK("u1", "cp1"));
        assertEquals("u1/checkpoints/cp1", DynamoDBRepositoryV2.s3Key("u1", "cp1"));
    }

    @Test
    void testGsiSortKeysSortByTime() {
        assertEquals("0000000001000#t1", DynamoDBRepositoryV2.threadGsiSK(1000L, "t1"));
        assertEquals("0000000001000#t1#0000000003", DynamoDBRepositoryV2.tagGsiSK(1000L, "t1", 3));

        // later timestamps sort after earlier ones, even with more digits
        assertTrue(DynamoDBRepositoryV2.threadGsiSK(999L, "z")
                .compareTo(DynamoDBRepositoryV2.threadGsiSK(1000L, "a")) < 0);
    }

    // ─── GSI sort keys ───────────────────────────────────────────────────────────
    //
    // DynamoDB sorts a string sort key character by character (lexicographically, by UTF-8 bytes).
    // The dashboard queries GSI1 with ScanIndexForward=false, so the "largest" string comes first.

    @Test
    void testCurrentTimeFitsTheTimestampPadding() {
        // epoch millis have 13 digits until the year 2286: every key has the same width today
        final String now = DynamoDBRepositoryV2.threadGsiSK(System.currentTimeMillis(), "t1");
        assertEquals(13, now.indexOf('#'));
    }

    @Test
    void testThreadGsiSKWithoutPaddingWouldMisorder() {
        // why the padding matters: as plain strings "999" > "1000", padded they compare correctly
        assertTrue("999#t".compareTo("1000#t") > 0);
        assertTrue(DynamoDBRepositoryV2.threadGsiSK(999L, "t")
                .compareTo(DynamoDBRepositoryV2.threadGsiSK(1000L, "t")) < 0);
    }

    @Test
    void testThreadGsiSKSameMillisecondTieBreaksOnName() {
        final String a = DynamoDBRepositoryV2.threadGsiSK(5000L, "thread-a");
        final String b = DynamoDBRepositoryV2.threadGsiSK(5000L, "thread-b");

        assertNotEquals(a, b);
        assertTrue(a.compareTo(b) < 0);
    }

    @Test
    void testThreadGsiSKTimeWinsOverName() {
        // an older thread named "zzz" still sorts before a newer thread named "aaa"
        assertTrue(DynamoDBRepositoryV2.threadGsiSK(1000L, "zzz")
                .compareTo(DynamoDBRepositoryV2.threadGsiSK(1001L, "aaa")) < 0);
    }

    @Test
    void testTagGsiSKVersionsOfOneThreadSortNumerically() {
        // same release millisecond and name: the padded version decides, 9 before 10
        final String v9 = DynamoDBRepositoryV2.tagGsiSK(7000L, "t1", 9);
        final String v10 = DynamoDBRepositoryV2.tagGsiSK(7000L, "t1", 10);

        assertTrue(v9.compareTo(v10) < 0);
    }

    @Test
    void testTagGsiSKNewestFirstWhenSortedDescending() {
        final List<String> keys = new ArrayList<>(List.of(
                DynamoDBRepositoryV2.tagGsiSK(1_000L, "t1", 1),
                DynamoDBRepositoryV2.tagGsiSK(3_000L, "t2", 1),
                DynamoDBRepositoryV2.tagGsiSK(2_000L, "t1", 2),
                DynamoDBRepositoryV2.tagGsiSK(3_000L, "t1", 3)));

        // what Query(ScanIndexForward=false) returns
        keys.sort(Comparator.reverseOrder());

        assertEquals(List.of(
                DynamoDBRepositoryV2.tagGsiSK(3_000L, "t2", 1),   // newest; same ms as t1 v3, "t2" > "t1"
                DynamoDBRepositoryV2.tagGsiSK(3_000L, "t1", 3),
                DynamoDBRepositoryV2.tagGsiSK(2_000L, "t1", 2),
                DynamoDBRepositoryV2.tagGsiSK(1_000L, "t1", 1)), keys);
    }

    @Test
    void testTagGsiSKSupportsTimeRangeQueries() {
        // a key condition "GSI1SK BETWEEN :from AND :to" selects releases in a time window:
        // the bounds only need the timestamp part
        final String from = String.format("%013d", 2_000L);
        final String to = String.format("%013d", 3_000L) + "#￿";

        final String before = DynamoDBRepositoryV2.tagGsiSK(1_999L, "t1", 1);
        final String inside = DynamoDBRepositoryV2.tagGsiSK(2_500L, "t1", 2);
        final String lastMs = DynamoDBRepositoryV2.tagGsiSK(3_000L, "zzz", 9);
        final String after = DynamoDBRepositoryV2.tagGsiSK(3_001L, "t1", 3);

        assertTrue(before.compareTo(from) < 0);
        assertTrue(inside.compareTo(from) >= 0 && inside.compareTo(to) <= 0);
        assertTrue(lastMs.compareTo(from) >= 0 && lastMs.compareTo(to) <= 0);
        assertTrue(after.compareTo(to) > 0);
    }

    @Test
    void testGsiSKWithSeparatorInThreadName() {
        // '#' inside a thread name is allowed: keys stay distinct, and the timestamp still sorts first
        final String plain = DynamoDBRepositoryV2.tagGsiSK(4_000L, "a", 1);
        final String withHash = DynamoDBRepositoryV2.tagGsiSK(4_000L, "a#b", 1);

        assertNotEquals(plain, withHash);
        assertTrue(withHash.startsWith("0000000004000#"));
        assertTrue(DynamoDBRepositoryV2.tagGsiSK(3_999L, "a#b", 1).compareTo(plain) < 0);
    }

    @Test
    void testThreadItemMapping() {
        final var item = Map.of(
                "PK", s("THREAD_t1"),
                "SK", s("ACTIVE"),
                DynamoDBRepositoryV2.THREAD_NAME, s("t1"),
                DynamoDBRepositoryV2.THREAD_UID, s("u1"),
                DynamoDBRepositoryV2.SEQ, n(3),
                DynamoDBRepositoryV2.IS_INTERRUPTED, AttributeValue.builder().bool(true).build(),
                DynamoDBRepositoryV2.MESSAGE, s("waiting"),
                DynamoDBRepositoryV2.CREATED_AT, n(1000));

        final var thread = DynamoDBRepositoryV2.ThreadItem.of(item);

        assertEquals("t1", thread.threadName());
        assertEquals("u1", thread.threadUid());
        assertEquals(3L, thread.seq());
        assertTrue(thread.isInterrupted());
        assertEquals("waiting", thread.message());
        assertEquals(1000L, thread.createdAt());
    }

    @Test
    void testTagItemMappingWithoutOptionalAttributes() {
        final var item = Map.of(
                "PK", s("THREAD_t1"),
                "SK", s("V#0000000002"),
                DynamoDBRepositoryV2.THREAD_NAME, s("t1"),
                DynamoDBRepositoryV2.THREAD_UID, s("u2"),
                DynamoDBRepositoryV2.VERSION, n(2));

        final var tag = DynamoDBRepositoryV2.TagItem.of(item);

        assertEquals(2, tag.version());
        assertEquals("u2", tag.threadUid());
        assertFalse(tag.isError());
        assertNull(tag.message());
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }
}
