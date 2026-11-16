package org.bsc.langgraph4j.checkpoint;

import static java.lang.String.format;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * Low-level DynamoDB operations for {@link DynamoDBSaverV2} and {@link DynamoDBSaverV2Dashboard}.
 *
 * <h2>Key-naming conventions (single-table design)</h2>
 * <pre>
 *   PK                                  SK               Description
 *   ──────────────────────────────────  ───────────────  ─────────────────────────────────────
 *   THREAD_{threadName}                 ACTIVE           Live execution of the thread (at most one)
 *   THREAD_{threadName}                 V#{version}      Release tag: an archived execution
 *   CHECKPOINT_{threadUid}              {checkpointId}   Checkpoint metadata of one execution
 *   CHUNK_{threadUid}#{checkpointId}    CHUNK            Serialized state payload (or S3 object)
 * </pre>
 *
 * <p>Every execution of a thread gets a new random {@code threadUid} when its {@code ACTIVE} item is
 * created. Releasing the thread atomically deletes {@code ACTIVE} and writes the next {@code V#} tag,
 * which keeps the {@code threadUid}: the checkpoints never move, and the next execution of the same
 * thread name starts with a new {@code threadUid}.
 *
 * <h2>Dashboard index</h2>
 * <p>The {@value #GSI1} global secondary index ({@code GSI1PK} + {@code GSI1SK}) is <em>sparse</em>:
 * only {@code ACTIVE} items ({@code GSI1PK = THREAD}) and tags ({@code GSI1PK = TAG}) carry its key,
 * so listing threads or tags never reads checkpoints or payloads.
 */
class DynamoDBRepositoryV2 {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBRepositoryV2.class);

    /** Maximum number of items per {@code batchWriteItem} request. */
    private static final int BATCH_WRITE_MAX_ITEMS = 25;

    /** Maximum retry rounds for unprocessed items in batch operations. */
    private static final int BATCH_RETRY_MAX_ROUNDS = 3;

    /** Backoff delay in milliseconds between batch retry rounds. */
    private static final long BATCH_RETRY_BACKOFF_MS = 100;

    // ─── Key and attribute names ─────────────────────────────────────────────────
    static final String PK = "PK";
    static final String SK = "SK";

    static final String ACTIVE_SK = "ACTIVE";
    static final String TAG_SK_PREFIX = "V#";
    static final String CHUNK_SK = "CHUNK";

    static final String GSI1 = "GSI1";
    static final String GSI1_PK = "GSI1PK";
    static final String GSI1_SK = "GSI1SK";
    static final String GSI1_THREAD = "THREAD";
    static final String GSI1_TAG = "TAG";

    static final String THREAD_UID = "threadUid";
    static final String THREAD_NAME = "threadName";
    static final String SEQ = "seq";
    static final String IS_INTERRUPTED = "isInterrupted";
    static final String IS_ERROR = "isError";
    static final String MESSAGE = "message";
    static final String VERSION = "version";
    static final String CREATED_AT = "createdAt";
    static final String RELEASED_AT = "releasedAt";
    static final String TTL = "ttl";

    // ─── PK/SK generators ───────────────────────────────────────────────────────
    static String threadPK(String threadName) {
        return "THREAD_" + threadName;
    }

    /** Sort key of a release tag; zero padded so tags sort by version. */
    static String tagSK(int version) {
        return format("%s%010d", TAG_SK_PREFIX, version);
    }

    static int versionOf(String tagSK) {
        return Integer.parseInt(tagSK.substring(TAG_SK_PREFIX.length()));
    }

    static String checkpointPK(String threadUid) {
        return "CHECKPOINT_" + threadUid;
    }

    static String chunkPK(String threadUid, String checkpointId) {
        return format("CHUNK_%s#%s", threadUid, checkpointId);
    }

    static String s3Key(String threadUid, String checkpointId) {
        return threadUid + "/checkpoints/" + checkpointId;
    }

    /** GSI sort key of an active thread: newest first when queried descending. */
    static String threadGsiSK(long createdAt, String threadName) {
        return format("%013d#%s", createdAt, threadName);
    }

    /** GSI sort key of a tag: newest release first when queried descending. */
    static String tagGsiSK(long releasedAt, String threadName, int version) {
        return format("%013d#%s#%010d", releasedAt, threadName, version);
    }

    // ─── Records ─────────────────────────────────────────────────────────────────

    /** The {@code ACTIVE} item of a thread. */
    record ThreadItem(String threadName,
                      String threadUid,
                      long seq,
                      boolean isInterrupted,
                      @Nullable String message,
                      long createdAt) {

        static ThreadItem of(Map<String, AttributeValue> item) {
            return new ThreadItem(
                    item.get(THREAD_NAME).s(),
                    item.get(THREAD_UID).s(),
                    longOf(item, SEQ, 0L),
                    boolOf(item, IS_INTERRUPTED),
                    stringOf(item, MESSAGE),
                    longOf(item, CREATED_AT, 0L));
        }
    }

    /** A {@code V#} release tag of a thread. */
    record TagItem(String threadName,
                   int version,
                   String threadUid,
                   boolean isError,
                   @Nullable String message,
                   long createdAt,
                   long releasedAt) {

        static TagItem of(Map<String, AttributeValue> item) {
            return new TagItem(
                    item.get(THREAD_NAME).s(),
                    (int) longOf(item, VERSION, 0L),
                    item.get(THREAD_UID).s(),
                    boolOf(item, IS_ERROR),
                    stringOf(item, MESSAGE),
                    longOf(item, CREATED_AT, 0L),
                    longOf(item, RELEASED_AT, 0L));
        }
    }

    /** A checkpoint as loaded from DynamoDB, with its payload. */
    record CheckpointRecord(String checkpointId,
                            String nodeId,
                            String nextNodeId,
                            byte[] payload,
                            String contentType,
                            long savedAt,
                            long seq,
                            @Nullable String parentCheckpointId,
                            String refLoc,
                            String refKey) {
    }

    /** Result of a release attempt. */
    enum ReleaseOutcome {
        /** The tag was written and the {@code ACTIVE} item deleted. */
        RELEASED,
        /** Another release took this version first: compute the next one and retry. */
        VERSION_TAKEN,
        /** The {@code ACTIVE} item is gone or belongs to another execution: already released. */
        ALREADY_RELEASED
    }

    // ─── Fields ─────────────────────────────────────────────────────────────────
    private final DynamoDbClient client;
    private final String tableName;
    private final @Nullable Long ttlSeconds;
    private final StorageStrategy storageStrategy;

    DynamoDBRepositoryV2(DynamoDbClient client, String tableName, @Nullable Long ttlSeconds,
                         StorageStrategy storageStrategy) {
        this.client = client;
        this.tableName = tableName;
        this.ttlSeconds = ttlSeconds;
        this.storageStrategy = storageStrategy;
    }

    // ─── Table lifecycle ─────────────────────────────────────────────────────────

    /**
     * Creates the table with {@code PK} (HASH) + {@code SK} (RANGE), the sparse {@value #GSI1}
     * index and {@code PAY_PER_REQUEST} billing. No-ops if the table already exists.
     */
    void createTableIfNotExists() {
        try {
            client.createTable(r -> r
                    .tableName(tableName)
                    .keySchema(
                            keyElement(PK, KeyType.HASH),
                            keyElement(SK, KeyType.RANGE))
                    .attributeDefinitions(
                            stringAttribute(PK),
                            stringAttribute(SK),
                            stringAttribute(GSI1_PK),
                            stringAttribute(GSI1_SK))
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName(GSI1)
                            .keySchema(
                                    keyElement(GSI1_PK, KeyType.HASH),
                                    keyElement(GSI1_SK, KeyType.RANGE))
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST));

            log.info("Created DynamoDB table '{}' with index '{}'", tableName, GSI1);
            client.waiter().waitUntilTableExists(r -> r.tableName(tableName));
        } catch (ResourceInUseException e) {
            log.debug("DynamoDB table '{}' already exists, skipping creation", tableName);
        }
    }

    /** Deletes the table and waits for the deletion. No-ops if the table does not exist. */
    void dropTable() {
        try {
            client.deleteTable(r -> r.tableName(tableName));
            log.info("Deleted DynamoDB table '{}'", tableName);
            client.waiter().waitUntilTableNotExists(r -> r.tableName(tableName));
        } catch (ResourceNotFoundException e) {
            log.debug("DynamoDB table '{}' not found – nothing to drop", tableName);
        }
    }

    // ─── Thread operations ───────────────────────────────────────────────────────

    /** Reads the {@code ACTIVE} item of the thread with a strongly consistent read. */
    Optional<ThreadItem> findActiveThread(String threadName) {
        GetItemResponse response = client.getItem(r -> r
                .tableName(tableName)
                .key(key(threadPK(threadName), ACTIVE_SK))
                .consistentRead(true));
        return response.hasItem() && !response.item().isEmpty()
                ? Optional.of(ThreadItem.of(response.item()))
                : Optional.empty();
    }

    /**
     * Gets the {@code ACTIVE} item of the thread, creating it if absent, and reserves the next
     * checkpoint sequence number on it, in one atomic {@code UpdateItem}.
     *
     * <p>A new checkpoint means the execution moved on, so a pending interruption is cleared.
     * The {@code ttl} is refreshed, so a live thread does not expire while it is used.
     *
     * @param threadName logical thread name
     * @param newThreadUid uid assigned only if the {@code ACTIVE} item does not exist yet
     * @return the item after the update, holding the reserved {@code seq}
     */
    ThreadItem claimThread(String threadName, String newThreadUid) {
        final long now = Instant.now().toEpochMilli();

        final Map<String, String> names = new HashMap<>();
        names.put("#uid", THREAD_UID);
        names.put("#name", THREAD_NAME);
        names.put("#created", CREATED_AT);
        names.put("#gpk", GSI1_PK);
        names.put("#gsk", GSI1_SK);
        names.put("#int", IS_INTERRUPTED);
        names.put("#msg", MESSAGE);
        names.put("#seq", SEQ);

        final Map<String, AttributeValue> values = new HashMap<>();
        values.put(":uid", s(newThreadUid));
        values.put(":name", s(threadName));
        values.put(":now", n(now));
        values.put(":gpk", s(GSI1_THREAD));
        values.put(":gsk", s(threadGsiSK(now, threadName)));
        values.put(":false", bool(false));
        values.put(":one", n(1L));

        final StringBuilder set = new StringBuilder("SET #uid = if_not_exists(#uid, :uid), #name = :name, ")
                .append("#created = if_not_exists(#created, :now), #gpk = :gpk, ")
                .append("#gsk = if_not_exists(#gsk, :gsk), #int = :false");
        if (ttlSeconds != null) {
            names.put("#ttl", TTL);
            values.put(":ttl", n(Instant.now().getEpochSecond() + ttlSeconds));
            set.append(", #ttl = :ttl");
        }
        final String expression = set + " ADD #seq :one REMOVE #msg";

        UpdateItemResponse response = client.updateItem(r -> r
                .tableName(tableName)
                .key(key(threadPK(threadName), ACTIVE_SK))
                .updateExpression(expression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW));

        return ThreadItem.of(response.attributes());
    }

    /**
     * Marks the {@code ACTIVE} item as interrupted with the given reason. Does nothing if the
     * thread has no {@code ACTIVE} item.
     */
    void interruptThread(String threadName, String reason) {
        try {
            client.updateItem(r -> r
                    .tableName(tableName)
                    .key(key(threadPK(threadName), ACTIVE_SK))
                    .updateExpression("SET #int = :true, #msg = :reason")
                    .conditionExpression("attribute_exists(#pk)")
                    .expressionAttributeNames(Map.of("#int", IS_INTERRUPTED, "#msg", MESSAGE, "#pk", PK))
                    .expressionAttributeValues(Map.of(":true", bool(true), ":reason", s(reason))));
            log.debug("Thread '{}' interrupted: {}", threadName, reason);
        } catch (ConditionalCheckFailedException e) {
            log.debug("no active thread '{}' to interrupt", threadName);
        }
    }

    // ─── Tag operations ──────────────────────────────────────────────────────────

    /** Returns the highest release version of the thread, if it was ever released. */
    OptionalInt lastTagVersion(String threadName) {
        return lastTag(threadName)
                .map(tag -> OptionalInt.of(tag.version()))
                .orElseGet(OptionalInt::empty);
    }

    /**
     * Returns a release tag of the thread.
     *
     * @param version the version to read, or {@code null} for the latest one
     */
    Optional<TagItem> findTag(String threadName, @Nullable Integer version) {
        if (version == null) {
            return lastTag(threadName);
        }
        GetItemResponse response = client.getItem(r -> r
                .tableName(tableName)
                .key(key(threadPK(threadName), tagSK(version)))
                .consistentRead(true));
        return response.hasItem() && !response.item().isEmpty()
                ? Optional.of(TagItem.of(response.item()))
                : Optional.empty();
    }

    private Optional<TagItem> lastTag(String threadName) {
        QueryResponse response = client.query(r -> r
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk AND begins_with(#sk, :prefix)")
                .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                .expressionAttributeValues(Map.of(":pk", s(threadPK(threadName)), ":prefix", s(TAG_SK_PREFIX)))
                .scanIndexForward(false)
                .limit(1)
                .consistentRead(true));
        return response.items().stream().findFirst().map(TagItem::of);
    }

    /**
     * Archives the given execution in one transaction: deletes its {@code ACTIVE} item (only if it
     * still belongs to {@code active.threadUid()}) and writes the {@code V#version} tag (only if
     * that version is still free).
     */
    ReleaseOutcome releaseThread(ThreadItem active, int version, boolean isError, @Nullable String message) {
        final long now = Instant.now().toEpochMilli();
        final String pk = threadPK(active.threadName());

        final Map<String, AttributeValue> tag = new HashMap<>(key(pk, tagSK(version)));
        tag.put(THREAD_UID, s(active.threadUid()));
        tag.put(THREAD_NAME, s(active.threadName()));
        tag.put(VERSION, n(version));
        tag.put(IS_ERROR, bool(isError));
        tag.put(CREATED_AT, n(active.createdAt()));
        tag.put(RELEASED_AT, n(now));
        tag.put(GSI1_PK, s(GSI1_TAG));
        tag.put(GSI1_SK, s(tagGsiSK(now, active.threadName(), version)));
        if (message != null) {
            tag.put(MESSAGE, s(message));
        }
        if (ttlSeconds != null) {
            tag.put(TTL, n(Instant.now().getEpochSecond() + ttlSeconds));
        }

        final TransactWriteItem deleteActive = TransactWriteItem.builder()
                .delete(Delete.builder()
                        .tableName(tableName)
                        .key(key(pk, ACTIVE_SK))
                        .conditionExpression("#uid = :uid")
                        .expressionAttributeNames(Map.of("#uid", THREAD_UID))
                        .expressionAttributeValues(Map.of(":uid", s(active.threadUid())))
                        .build())
                .build();

        final TransactWriteItem putTag = TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(tag)
                        .conditionExpression("attribute_not_exists(#pk)")
                        .expressionAttributeNames(Map.of("#pk", PK))
                        .build())
                .build();

        try {
            client.transactWriteItems(r -> r.transactItems(deleteActive, putTag));
            log.info("Released thread '{}' (uid {}) as version {}{}", active.threadName(), active.threadUid(), version,
                    isError ? " with error: " + message : "");
            return ReleaseOutcome.RELEASED;
        } catch (TransactionCanceledException e) {
            final List<CancellationReason> reasons = e.hasCancellationReasons() ? e.cancellationReasons() : List.of();
            log.debug("release of thread '{}' as version {} cancelled: {}", active.threadName(), version, reasons);
            if (isConditionFailure(reasons, 0) || !isStillActive(active)) {
                // the ACTIVE item is gone or belongs to a newer execution
                return ReleaseOutcome.ALREADY_RELEASED;
            }
            // version taken by a concurrent release, or a transient transaction conflict: retry
            return ReleaseOutcome.VERSION_TAKEN;
        }
    }

    /** Whether the {@code ACTIVE} item of the thread still belongs to the given execution. */
    private boolean isStillActive(ThreadItem active) {
        return findActiveThread(active.threadName())
                .map(current -> current.threadUid().equals(active.threadUid()))
                .orElse(false);
    }

    private static boolean isConditionFailure(List<CancellationReason> reasons, int index) {
        return reasons.size() > index && "ConditionalCheckFailed".equals(reasons.get(index).code());
    }

    // ─── Checkpoint operations ───────────────────────────────────────────────────

    /**
     * Persists the payload (DynamoDB chunk or S3) and the metadata item of a checkpoint.
     *
     * @param allowOverwrite if {@code false}, an existing checkpoint with the same id is kept
     */
    void putCheckpoint(String threadUid,
                       String checkpointId,
                       String nodeId,
                       String nextNodeId,
                       byte[] payload,
                       String contentType,
                       @Nullable String parentCheckpointId,
                       long seq,
                       boolean allowOverwrite) {

        final String chunkKey = chunkPK(threadUid, checkpointId);
        final String s3Key = s3Key(threadUid, checkpointId);

        final String refLoc = storageStrategy.storeData(chunkKey, s3Key, payload, allowOverwrite);
        final String refKey = "S3".equals(refLoc) ? s3Key : chunkKey;

        final Map<String, AttributeValue> item = new HashMap<>(key(checkpointPK(threadUid), checkpointId));
        item.put("checkpointId", s(checkpointId));
        item.put("nodeId", s(nodeId));
        item.put("nextNodeId", s(nextNodeId));
        item.put("contentType", s(contentType));
        item.put("savedAt", n(Instant.now().toEpochMilli()));
        item.put(SEQ, n(seq));
        item.put("ref_loc", s(refLoc));
        item.put("ref_key", s(refKey));
        if (parentCheckpointId != null) {
            item.put("parentCheckpointId", s(parentCheckpointId));
        }
        if (ttlSeconds != null) {
            item.put(TTL, n(Instant.now().getEpochSecond() + ttlSeconds));
        }

        final PutItemRequest.Builder request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item);
        if (!allowOverwrite) {
            request.conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", PK));
        }

        try {
            client.putItem(request.build());
        } catch (ConditionalCheckFailedException e) {
            if (allowOverwrite) {
                throw e;
            }
            log.debug("Checkpoint metadata already exists, skipping: checkpointId={}", checkpointId);
            return;
        }
        log.debug("Stored checkpoint '{}' (uid {}, seq {}, ref_loc={})", checkpointId, threadUid, seq, refLoc);
    }

    /** Loads all checkpoints of one execution with their payloads, newest ({@code seq}) first. */
    List<CheckpointRecord> loadCheckpointRecords(String threadUid) {
        final List<CheckpointRecord> records = new ArrayList<>();

        for (Map<String, AttributeValue> item : queryPartition(checkpointPK(threadUid), null)) {
            final String checkpointId = item.get("checkpointId").s();
            final String refLoc = stringOf(item, "ref_loc") != null ? item.get("ref_loc").s() : "DYNAMODB";
            final String refKey = stringOf(item, "ref_key") != null
                    ? item.get("ref_key").s()
                    : chunkPK(threadUid, checkpointId);

            final byte[] payload = storageStrategy.retrieveData(refKey, refLoc);
            if (payload == null) {
                log.warn("Payload not found for checkpoint '{}' (uid {}, ref_loc={}, ref_key={}) – skipping",
                        checkpointId, threadUid, refLoc, refKey);
                continue;
            }

            records.add(new CheckpointRecord(
                    checkpointId,
                    item.get("nodeId").s(),
                    item.get("nextNodeId").s(),
                    payload,
                    item.get("contentType").s(),
                    longOf(item, "savedAt", 0L),
                    longOf(item, SEQ, 0L),
                    stringOf(item, "parentCheckpointId"),
                    refLoc,
                    refKey));
        }

        records.sort(Comparator.comparingLong(CheckpointRecord::seq)
                .thenComparingLong(CheckpointRecord::savedAt)
                .reversed());
        return records;
    }

    /**
     * Deletes the metadata item and the DynamoDB chunk of a checkpoint.
     *
     * @return the {@code seq} of the deleted checkpoint, if it existed
     */
    OptionalLong deleteCheckpoint(String threadUid, String checkpointId) {
        DeleteItemResponse response = client.deleteItem(r -> r
                .tableName(tableName)
                .key(key(checkpointPK(threadUid), checkpointId))
                .returnValues(ReturnValue.ALL_OLD));

        client.deleteItem(r -> r
                .tableName(tableName)
                .key(key(chunkPK(threadUid, checkpointId), CHUNK_SK)));

        if (response.hasAttributes() && response.attributes().containsKey(SEQ)) {
            return OptionalLong.of(longOf(response.attributes(), SEQ, 0L));
        }
        return OptionalLong.empty();
    }

    // ─── Dashboard queries ───────────────────────────────────────────────────────

    /** All active threads, newest first, read from the sparse {@value #GSI1} index. */
    List<ThreadItem> selectAllThreads() {
        return queryIndex(GSI1_THREAD).stream().map(ThreadItem::of).toList();
    }

    /** All release tags, newest release first, read from the sparse {@value #GSI1} index. */
    List<TagItem> selectAllTags() {
        return queryIndex(GSI1_TAG).stream().map(TagItem::of).toList();
    }

    private List<Map<String, AttributeValue>> queryIndex(String gsiPk) {
        final QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI1)
                .keyConditionExpression("#gpk = :gpk")
                .expressionAttributeNames(Map.of("#gpk", GSI1_PK))
                .expressionAttributeValues(Map.of(":gpk", s(gsiPk)))
                .scanIndexForward(false)
                .build();
        return paginate(query);
    }

    // ─── Hard delete ─────────────────────────────────────────────────────────────

    /**
     * Permanently deletes the thread: its {@code ACTIVE} item, every release tag, and the
     * checkpoints and payloads of every execution. Calling it for a missing thread is a no-op.
     */
    void deleteThread(String threadName) {
        final List<Map<String, AttributeValue>> threadItems = queryPartition(threadPK(threadName), null);

        final Set<String> threadUids = new LinkedHashSet<>();
        final List<Map<String, AttributeValue>> keysToDelete = new ArrayList<>();
        final List<String[]> payloadRefs = new ArrayList<>();  // [refKey, refLoc]

        for (Map<String, AttributeValue> item : threadItems) {
            threadUids.add(item.get(THREAD_UID).s());
        }
        for (String threadUid : threadUids) {
            final String pk = checkpointPK(threadUid);
            for (Map<String, AttributeValue> item : queryPartition(pk, "#sk, ref_loc, ref_key")) {
                final String checkpointId = item.get(SK).s();
                keysToDelete.add(key(pk, checkpointId));
                payloadRefs.add(new String[]{
                        stringOf(item, "ref_key") != null ? item.get("ref_key").s() : chunkPK(threadUid, checkpointId),
                        stringOf(item, "ref_loc") != null ? item.get("ref_loc").s() : "DYNAMODB"
                });
            }
        }
        final int checkpointCount = keysToDelete.size();

        // thread items last: they are the only way to find the checkpoints again
        for (Map<String, AttributeValue> item : threadItems) {
            keysToDelete.add(key(item.get(PK).s(), item.get(SK).s()));
        }

        batchDeleteItems(keysToDelete);
        storageStrategy.batchDeleteData(payloadRefs);

        log.info("Deleted thread '{}': {} checkpoint(s) across {} execution(s)",
                threadName, checkpointCount, threadUids.size());
    }

    // ─── Query helpers ───────────────────────────────────────────────────────────

    private List<Map<String, AttributeValue>> queryPartition(String pk, @Nullable String projection) {
        final QueryRequest.Builder query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeValues(Map.of(":pk", s(pk)))
                .consistentRead(true);
        if (projection != null) {
            query.projectionExpression(projection)
                    .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK));
        } else {
            query.expressionAttributeNames(Map.of("#pk", PK));
        }
        return paginate(query.build());
    }

    private List<Map<String, AttributeValue>> paginate(QueryRequest query) {
        final List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            final QueryRequest.Builder request = query.toBuilder();
            if (exclusiveStartKey != null) {
                request.exclusiveStartKey(exclusiveStartKey);
            }
            final QueryResponse response = client.query(request.build());
            items.addAll(response.items());
            exclusiveStartKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey()
                    : null;
        } while (exclusiveStartKey != null);
        return items;
    }

    // ─── Batch delete ────────────────────────────────────────────────────────────

    private void batchDeleteItems(List<Map<String, AttributeValue>> keys) {
        for (int i = 0; i < keys.size(); i += BATCH_WRITE_MAX_ITEMS) {
            final List<WriteRequest> deletes = keys.subList(i, Math.min(i + BATCH_WRITE_MAX_ITEMS, keys.size()))
                    .stream()
                    .map(k -> WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(k).build()).build())
                    .toList();

            Map<String, List<WriteRequest>> requestItems = Map.of(tableName, deletes);
            int round = 0;
            while (!requestItems.isEmpty() && round <= BATCH_RETRY_MAX_ROUNDS) {
                final BatchWriteItemResponse response = client.batchWriteItem(
                        BatchWriteItemRequest.builder().requestItems(requestItems).build());
                if (!response.hasUnprocessedItems() || response.unprocessedItems().isEmpty()) {
                    requestItems = Map.of();
                    break;
                }
                requestItems = response.unprocessedItems();
                round++;
                if (round <= BATCH_RETRY_MAX_ROUNDS) {
                    sleepForBackoff(round);
                }
            }
            if (!requestItems.isEmpty()) {
                log.warn("Batch delete: {} item(s) still unprocessed after {} retries",
                        requestItems.values().stream().mapToInt(List::size).sum(), BATCH_RETRY_MAX_ROUNDS);
            }
        }
    }

    private static void sleepForBackoff(int round) {
        try {
            Thread.sleep(BATCH_RETRY_BACKOFF_MS * (1L << (round - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during batch delete backoff", e);
        }
    }

    // ─── AttributeValue helpers ──────────────────────────────────────────────────

    private static Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of(PK, s(pk), SK, s(sk));
    }

    private static KeySchemaElement keyElement(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static AttributeDefinition stringAttribute(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    private static AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    private static @Nullable String stringOf(Map<String, AttributeValue> item, String name) {
        final AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    private static long longOf(Map<String, AttributeValue> item, String name, long defaultValue) {
        final AttributeValue value = item.get(name);
        return value == null || value.n() == null ? defaultValue : Long.parseLong(value.n());
    }

    private static boolean boolOf(Map<String, AttributeValue> item, String name) {
        final AttributeValue value = item.get(name);
        return value != null && Boolean.TRUE.equals(value.bool());
    }
}
