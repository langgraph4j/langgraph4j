package org.bsc.langgraph4j.checkpoint;

import static java.lang.String.format;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * Low-level DynamoDB operations for the {@link DynamoDBSaver}.
 *
 * <h2>Key-naming conventions (single-table design)</h2>
 * <pre>
 *   PK                                     SK                Description
 *   ─────────────────────────────────────  ────────────────  ─────────────────────────────
 *   CHECKPOINT_{threadId}                  {checkpointId}    Checkpoint metadata item
 *   CHUNK_{threadId}#{checkpointId}        CHUNK             Serialized state payload
 *   RELEASED_{threadId}                    MARKER            Thread-released sentinel
 * </pre>
 *
 * <p>
 * All item attributes:
 * <ul>
 * <li>{@code PK} – partition key</li>
 * <li>{@code SK} – sort key</li>
 * <li>{@code checkpointId} – UUID string</li>
 * <li>{@code nodeId} – current node name</li>
 * <li>{@code nextNodeId} – next node name</li>
 * <li>{@code contentType} – serializer content-type string</li>
 * <li>{@code parentCheckpointId} – optional; checkpoint ID of the parent (lineage)</li>
 * <li>{@code ref_loc} – storage location: {@code "DYNAMODB"} or {@code "S3"} (future)</li>
 * <li>{@code ref_key} – reference key for payload retrieval</li>
 * <li>{@code payload} – binary; only present in CHUNK items</li>
 * <li>{@code ttl} – optional epoch-second number for DynamoDB TTL</li>
 * </ul>
 */
class DynamoDBRepository {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBRepository.class);

    /** Maximum number of items per {@code batchWriteItem} request. */
    private static final int BATCH_WRITE_MAX_ITEMS = 25;

    /** Maximum retry rounds for unprocessed items in batch operations. */
    private static final int BATCH_RETRY_MAX_ROUNDS = 3;

    /** Backoff delay in milliseconds between batch retry rounds. */
    private static final long BATCH_RETRY_BACKOFF_MS = 100;

    // ─── PK/SK generators ───────────────────────────────────────────────────────
    static String checkpointPK(String threadId) {
        return "CHECKPOINT_" + threadId;
    }

    static String checkpointSK(String checkpointId) {
        return checkpointId;
    }

    static String chunkPK(String threadId, String checkpointId) {
        return format("CHUNK_%s#%s", threadId, checkpointId);
    }

    static String chunkSK() {
        return "CHUNK";
    }

    static String releasedPK(String threadId) {
        return "RELEASED_" + threadId;
    }

    static String releasedSK() {
        return "MARKER";
    }

    // ─── Record type returned to DynamoDBSaver ──────────────────────────────────
    /**
     * Immutable view of a checkpoint as loaded from DynamoDB.
     *
     * @param threadId thread identifier
     * @param checkpointId UUID of this checkpoint
     * @param nodeId current graph node
     * @param nextNodeId next graph node
     * @param payload serialized state bytes
     * @param contentType serializer content-type string
     * @param savedAt epoch-millis timestamp when the checkpoint was persisted
     * @param parentCheckpointId checkpoint ID of the parent (nullable for the first checkpoint)
     * @param refLoc storage location of the payload ({@code "DYNAMODB"} or {@code "S3"})
     * @param refKey reference key used to retrieve the payload from its storage location
     */
    record CheckpointRecord(
            String threadId,
            String checkpointId,
            String nodeId,
            String nextNodeId,
            byte[] payload,
            String contentType,
            Long savedAt,
            String parentCheckpointId,
            String refLoc,
            String refKey
            ) {

    }

    // ─── Fields ─────────────────────────────────────────────────────────────────
    private final DynamoDbClient client;
    private final String tableName;
    private final Long ttlSeconds;
    private final StorageStrategy storageStrategy;

    // ─── Constructor ─────────────────────────────────────────────────────────────
    DynamoDBRepository(DynamoDbClient client, String tableName, Long ttlSeconds,
                       StorageStrategy storageStrategy) {
        this.client = client;
        this.tableName = tableName;
        this.ttlSeconds = ttlSeconds;
        this.storageStrategy = storageStrategy;
    }

    // ─── Table lifecycle ─────────────────────────────────────────────────────────
    /**
     * Creates the DynamoDB table with {@code PK} (HASH) + {@code SK} (RANGE)
     * composite key and {@code PAY_PER_REQUEST} billing. No-ops if the table
     * already exists.
     */
    void createTableIfNotExists() {
        try {
            client.createTable(r -> r
                    .tableName(tableName)
                    .keySchema(
                            KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()
                    )
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("PK")
                                    .attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("SK")
                                    .attributeType(ScalarAttributeType.S).build()
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
            );

            log.info("Created DynamoDB table '{}'", tableName);

            // Wait until the table is active
            client.waiter().waitUntilTableExists(r -> r.tableName(tableName));
            log.debug("DynamoDB table '{}' is now ACTIVE", tableName);

        } catch (ResourceInUseException e) {
            // Table already exists – no action needed
            log.debug("DynamoDB table '{}' already exists, skipping creation", tableName);
        }
    }

    /**
     * Deletes the table. Waits until the deletion is confirmed. No-ops if the
     * table does not exist.
     */
    void dropTable() {
        try {
            client.deleteTable(r -> r.tableName(tableName));
            log.info("Deleted DynamoDB table '{}'", tableName);
            client.waiter().waitUntilTableNotExists(r -> r.tableName(tableName));
        } catch (ResourceNotFoundException e) {
            log.debug("DynamoDB table '{}' not found – nothing to drop", tableName);
        }
    }

    // ─── Checkpoint operations ───────────────────────────────────────────────────
    /**
     * Persists a checkpoint metadata item and its associated payload chunk
     * item.
     *
     * @param threadId thread identifier
     * @param checkpointId UUID of the checkpoint
     * @param nodeId current graph node
     * @param nextNodeId next graph node
     * @param payload serialized state bytes
     * @param contentType serializer content-type string
     * @param parentCheckpointId checkpoint ID of the parent for lineage tracking (nullable)
     * @param allowOverwrite if false, insert-only (conditional write); if true, overwrite allowed
     */
    void putCheckpoint(String threadId,
            String checkpointId,
            String nodeId,
            String nextNodeId,
            byte[] payload,
            String contentType,
            String parentCheckpointId,
            boolean allowOverwrite) {

        String chunkKey = chunkPK(threadId, checkpointId);
        String s3Key = threadId + "/checkpoints/" + checkpointId;

        // ── 1. Store payload via StorageStrategy ──────────────────────────────
        String refLoc = storageStrategy.storeData(chunkKey, s3Key, payload, allowOverwrite);
        String refKey = "S3".equals(refLoc) ? s3Key : chunkKey;

        // ── 2. Metadata item ──────────────────────────────────────────────────
        Map<String, AttributeValue> metaItem = new HashMap<>();
        metaItem.put("PK", s(checkpointPK(threadId)));
        metaItem.put("SK", s(checkpointSK(checkpointId)));
        metaItem.put("checkpointId", s(checkpointId));
        metaItem.put("nodeId", s(nodeId));
        metaItem.put("nextNodeId", s(nextNodeId));
        metaItem.put("contentType", s(contentType));
        metaItem.put("savedAt", n(Instant.now().toEpochMilli()));
        metaItem.put("ref_loc", s(refLoc));
        metaItem.put("ref_key", s(refKey));

        if (parentCheckpointId != null) {
            metaItem.put("parentCheckpointId", s(parentCheckpointId));
        }

        if (ttlSeconds != null) {
            metaItem.put("ttl", n(Instant.now().getEpochSecond() + ttlSeconds));
        }

        PutItemRequest.Builder metaReqBuilder = PutItemRequest.builder()
                .tableName(tableName)
                .item(metaItem);

        if (!allowOverwrite) {
            metaReqBuilder.conditionExpression("attribute_not_exists(PK)");
        }

        try {
            client.putItem(metaReqBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            if (!allowOverwrite) {
                log.debug("Checkpoint metadata already exists, skipping: checkpointId={}", checkpointId);
                return;
            }
            throw e;
        }

        log.debug("Stored checkpoint metadata: threadId={}, checkpointId={}, parentCheckpointId={}, ref_loc={}",
                threadId, checkpointId, parentCheckpointId, refLoc);
    }

    /**
     * Loads all checkpoint records for the given thread, sorted newest-first.
     * Each record's payload is fetched from the associated chunk item.
     *
     * @param threadId thread identifier
     * @return list of records, ordered by checkpointId descending (UUIDs are
     * time-based when v7, but for lexicographic ordering we rely on DynamoDB's
     * SK scan-forward=false)
     */
    List<CheckpointRecord> loadCheckpointRecords(String threadId) {
        String pk = checkpointPK(threadId);

        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(":pk", s(pk)))
                .scanIndexForward(false) // newest SK first
                .build();

        List<CheckpointRecord> records = new ArrayList<>();

        // Paginate through all results
        String lastEvaluatedKey = null;
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder reqBuilder = query.toBuilder();
            if (exclusiveStartKey != null) {
                reqBuilder.exclusiveStartKey(exclusiveStartKey);
            }

            QueryResponse response = client.query(reqBuilder.build());

            for (Map<String, AttributeValue> item : response.items()) {
                String checkpointId = item.get("checkpointId").s();
                String nodeId = item.get("nodeId").s();
                String nextNodeId = item.get("nextNodeId").s();
                String contentType = item.get("contentType").s();
                Long savedAt = item.containsKey("savedAt") ? Long.parseLong(item.get("savedAt").n()) : 0L;

                // Read lineage + storage location with backward compat guards
                String parentCpId = item.containsKey("parentCheckpointId")
                        ? item.get("parentCheckpointId").s() : null;
                String refLoc = item.containsKey("ref_loc")
                        ? item.get("ref_loc").s() : "DYNAMODB";
                String refKey = item.containsKey("ref_key")
                        ? item.get("ref_key").s() : chunkPK(threadId, checkpointId);

                // Fetch payload from storage backend
                byte[] payload = storageStrategy.retrieveData(refKey, refLoc);
                if (payload == null) {
                    log.warn("Payload not found for checkpointId='{}', thread='{}', ref_loc='{}', ref_key='{}' – skipping",
                            checkpointId, threadId, refLoc, refKey);
                    continue;
                }

                records.add(new CheckpointRecord(
                        threadId, checkpointId, nodeId, nextNodeId,
                        payload, contentType, savedAt,
                        parentCpId, refLoc, refKey));
            }

            exclusiveStartKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;

        } while (exclusiveStartKey != null);

        log.debug("Loaded {} checkpoint record(s) for thread '{}'", records.size(), threadId);
        records.sort(Comparator.comparing(CheckpointRecord::savedAt).reversed());
        return records;
    }

    /**
     * Fetches the serialized payload for a single checkpoint using the
     * {@link StorageStrategy}.
     *
     * @param refKey the reference key for the payload
     * @param refLoc the storage location ({@code "DYNAMODB"} or {@code "S3"})
     * @return the raw bytes, or {@code null} if the item is not found
     */
    byte[] loadChunkPayload(String refKey, String refLoc) {
        return storageStrategy.retrieveData(refKey, refLoc);
    }

    /**
     * Deletes a single checkpoint: its metadata item. Payload cleanup is
     * handled by the {@link StorageStrategy} during {@code deleteThread}.
     */
    void deleteCheckpoint(String threadId, String checkpointId) {
        // Delete metadata
        client.deleteItem(r -> r
                .tableName(tableName)
                .key(Map.of(
                        "PK", s(checkpointPK(threadId)),
                        "SK", s(checkpointSK(checkpointId))
                ))
        );

        // Delete DynamoDB chunk (best effort — may not exist if S3)
        client.deleteItem(r -> r
                .tableName(tableName)
                .key(Map.of(
                        "PK", s(chunkPK(threadId, checkpointId)),
                        "SK", s(chunkSK())
                ))
        );

        log.debug("Deleted checkpoint '{}' for thread '{}'", checkpointId, threadId);
    }

    // ─── Thread release operations ───────────────────────────────────────────────
    /**
     * Writes a sentinel item that marks this thread as "released" (archived).
     * Subsequent calls to {@link #isThreadReleased(String)} will return
     * {@code true}.
     */
    void markThreadReleased(String threadId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(releasedPK(threadId)));
        item.put("SK", s(releasedSK()));

        if (ttlSeconds != null) {
            item.put("ttl", n(Instant.now().getEpochSecond() + ttlSeconds));
        }

        client.putItem(r -> r.tableName(tableName).item(item));
        log.info("Marked thread '{}' as released", threadId);
    }

    /**
     * Returns {@code true} if a released sentinel item exists for the given
     * thread.
     */
    boolean isThreadReleased(String threadId) {
        GetItemResponse response = client.getItem(r -> r
                .tableName(tableName)
                .key(Map.of(
                        "PK", s(releasedPK(threadId)),
                        "SK", s(releasedSK())
                ))
                .projectionExpression("PK")
        );
        return response.hasItem();
    }

    // ─── Hard delete operations ──────────────────────────────────────────────────
    /**
     * Deletes all items associated with a thread: checkpoint metadata items,
     * payload chunk items, and the released sentinel (if present).
     *
     * <p>This is a <em>hard delete</em> — all data for the thread is permanently
     * removed from DynamoDB. The operation is idempotent: calling it on a
     * non-existent or already-deleted thread is a no-op.
     *
     * @param threadId thread identifier to delete
     */
    void deleteThread(String threadId) {
        // ── 1. Query all checkpoint metadata items to collect checkpoint IDs + ref info ──
        String pk = checkpointPK(threadId);
        List<String> checkpointIds = new ArrayList<>();
        List<String[]> payloadRefs = new ArrayList<>();  // [refKey, refLoc]

        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(":pk", s(pk)))
                .projectionExpression("SK, ref_loc, ref_key")
                .build();

        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder reqBuilder = query.toBuilder();
            if (exclusiveStartKey != null) {
                reqBuilder.exclusiveStartKey(exclusiveStartKey);
            }
            QueryResponse response = client.query(reqBuilder.build());
            for (Map<String, AttributeValue> item : response.items()) {
                String cpId = item.get("SK").s();
                checkpointIds.add(cpId);

                // Collect ref info for S3 cleanup
                String refLoc = item.containsKey("ref_loc") ? item.get("ref_loc").s() : "DYNAMODB";
                String refKey = item.containsKey("ref_key") ? item.get("ref_key").s() : chunkPK(threadId, cpId);
                payloadRefs.add(new String[]{refKey, refLoc});
            }
            exclusiveStartKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (exclusiveStartKey != null);

        if (checkpointIds.isEmpty()) {
            log.debug("No checkpoints found for thread '{}' – nothing to delete", threadId);
            deleteReleasedMarker(threadId);
            return;
        }

        // ── 2. Build list of DynamoDB keys to delete (metadata items only) ───
        List<Map<String, AttributeValue>> keysToDelete = new ArrayList<>();

        for (String checkpointId : checkpointIds) {
            // Checkpoint metadata item
            keysToDelete.add(Map.of(
                    "PK", s(checkpointPK(threadId)),
                    "SK", s(checkpointSK(checkpointId))
            ));
        }

        // Released marker
        keysToDelete.add(Map.of(
                "PK", s(releasedPK(threadId)),
                "SK", s(releasedSK())
        ));

        // ── 3. Batch delete metadata items from DynamoDB ─────────────────────
        batchDeleteItems(keysToDelete);

        // ── 4. Delete payloads via StorageStrategy (handles both DynamoDB chunks + S3) ──
        storageStrategy.batchDeleteData(payloadRefs);

        log.info("Deleted thread '{}': {} checkpoint(s)",
                threadId, checkpointIds.size());
    }

    /**
     * Deletes the released sentinel marker for a thread, if it exists.
     *
     * @param threadId thread identifier
     */
    private void deleteReleasedMarker(String threadId) {
        client.deleteItem(r -> r
                .tableName(tableName)
                .key(Map.of(
                        "PK", s(releasedPK(threadId)),
                        "SK", s(releasedSK())
                ))
        );
    }

    // ─── Batch operations ────────────────────────────────────────────────────────
    /**
     * Batch-deletes items from DynamoDB using {@code batchWriteItem}.
     *
     * <p>Items are partitioned into batches of {@value #BATCH_WRITE_MAX_ITEMS}
     * (the DynamoDB maximum per request). Unprocessed items are retried up to
     * {@value #BATCH_RETRY_MAX_ROUNDS} times with a {@value #BATCH_RETRY_BACKOFF_MS}ms
     * backoff between rounds.
     *
     * @param keys list of composite keys ({@code PK} + {@code SK}) to delete
     */
    void batchDeleteItems(List<Map<String, AttributeValue>> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (int i = 0; i < keys.size(); i += BATCH_WRITE_MAX_ITEMS) {
            int end = Math.min(i + BATCH_WRITE_MAX_ITEMS, keys.size());
            List<Map<String, AttributeValue>> batch = keys.subList(i, end);

            List<WriteRequest> deleteRequests = batch.stream()
                    .map(key -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder().key(key).build())
                            .build())
                    .toList();

            Map<String, List<WriteRequest>> requestItems = Map.of(tableName, deleteRequests);
            processBatchWithRetry(requestItems, batch.size());
        }
    }

    private void processBatchWithRetry(Map<String, List<WriteRequest>> initialRequestItems, int batchSize) {
        Map<String, List<WriteRequest>> requestItems = initialRequestItems;
        int retryRound = 0;

        while (!requestItems.isEmpty() && retryRound <= BATCH_RETRY_MAX_ROUNDS) {
            BatchWriteItemRequest request = BatchWriteItemRequest.builder()
                    .requestItems(requestItems)
                    .build();

            BatchWriteItemResponse response = client.batchWriteItem(request);

            if (!response.hasUnprocessedItems() || response.unprocessedItems().isEmpty()) {
                log.debug("Batch deleted {} item(s) successfully", batchSize);
                return;
            }

            requestItems = response.unprocessedItems();
            retryRound++;

            int unprocessedCount = requestItems.values().stream().mapToInt(List::size).sum();
            log.debug("Batch delete: {} unprocessed item(s), retry round {}/{}",
                    unprocessedCount, retryRound, BATCH_RETRY_MAX_ROUNDS);

            if (retryRound <= BATCH_RETRY_MAX_ROUNDS) {
                sleepForBackoff(retryRound);
            }
        }

        int unprocessedCount = requestItems.values().stream().mapToInt(List::size).sum();
        log.warn("Batch delete: {} item(s) still unprocessed after {} retries",
                unprocessedCount, BATCH_RETRY_MAX_ROUNDS);
    }

    private void sleepForBackoff(int retryRound) {
        try {
            long backoffMs = BATCH_RETRY_BACKOFF_MS * (1L << (retryRound - 1));
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during batch delete backoff", e);
        }
    }

    // ─── AttributeValue helpers ──────────────────────────────────────────────────
    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    private static AttributeValue b(byte[] value) {
        return AttributeValue.builder().b(SdkBytes.fromByteArray(value)).build();
    }
}
