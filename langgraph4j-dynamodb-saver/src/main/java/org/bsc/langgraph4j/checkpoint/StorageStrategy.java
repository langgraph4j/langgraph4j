package org.bsc.langgraph4j.checkpoint;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * Determines and executes storage strategy (DynamoDB vs S3) based on data size.
 *
 * <p>The 350KB threshold provides a safety margin below DynamoDB's 400KB item
 * size limit. When S3 is configured and the payload exceeds the threshold, data
 * is automatically offloaded to S3. Otherwise it stays in the DynamoDB chunk
 * table.
 *
 * <p>This class closely follows the Python reference implementation at
 * {@code langchain-aws/checkpoint/dynamodb/storage_strategy.py}.
 */
class StorageStrategy {

    private static final Logger log = LoggerFactory.getLogger(StorageStrategy.class);

    /** Payloads larger than 350KB are offloaded to S3 (50KB safety margin). */
    static final int S3_OFFLOAD_THRESHOLD = 350 * 1024;

    /** Maximum number of items per DynamoDB {@code batchWriteItem} request. */
    private static final int BATCH_WRITE_MAX_ITEMS = 25;

    /** Maximum number of objects per S3 {@code deleteObjects} request. */
    private static final int S3_DELETE_MAX_OBJECTS = 1000;

    /** Maximum retry rounds for unprocessed DynamoDB batch items. */
    private static final int BATCH_RETRY_MAX_ROUNDS = 3;

    /** Backoff delay in milliseconds between batch retry rounds. */
    private static final long BATCH_RETRY_BACKOFF_MS = 100;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final S3Client s3Client;
    private final String s3Bucket;
    private final String s3KeyPrefix;
    private final Long ttlSeconds;
    private final boolean s3Enabled;

    /**
     * Creates a new storage strategy.
     *
     * @param dynamoDbClient DynamoDB client for chunk table operations
     * @param tableName name of the DynamoDB table for payload chunks
     * @param s3Client optional S3 client for large data offloading (nullable)
     * @param s3Bucket optional S3 bucket name (nullable)
     * @param s3KeyPrefix optional prefix prepended to all S3 keys (nullable)
     * @param ttlSeconds optional TTL in seconds for automatic cleanup (nullable)
     */
    StorageStrategy(DynamoDbClient dynamoDbClient,
                    String tableName,
                    S3Client s3Client,
                    String s3Bucket,
                    String s3KeyPrefix,
                    Long ttlSeconds) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.s3Client = s3Client;
        this.s3Bucket = s3Bucket;
        this.s3KeyPrefix = s3KeyPrefix != null ? s3KeyPrefix.replaceAll("^/+|/+$", "") : null;
        this.ttlSeconds = ttlSeconds;
        this.s3Enabled = s3Client != null && s3Bucket != null;

        // Configure S3 lifecycle policy if TTL is set
        if (this.s3Enabled && this.ttlSeconds != null && this.ttlSeconds > 0) {
            ensureS3LifecyclePolicy();
        }
    }

    // ─── Threshold logic ────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the data should be offloaded to S3.
     *
     * @param data serialized payload bytes
     * @return true if data exceeds threshold and S3 is configured
     */
    boolean shouldOffloadToS3(byte[] data) {
        if (!s3Enabled) {
            return false;
        }
        if (data.length > S3_OFFLOAD_THRESHOLD) {
            log.debug("Data size {}KB exceeds threshold {}KB - will offload to S3",
                    data.length / 1024, S3_OFFLOAD_THRESHOLD / 1024);
            return true;
        }
        log.debug("Data size {}KB below threshold {}KB - will store in DynamoDB",
                data.length / 1024, S3_OFFLOAD_THRESHOLD / 1024);
        return false;
    }

    // ─── Store operations ───────────────────────────────────────────────────────

    /**
     * Stores data using the appropriate backend based on size.
     *
     * @param chunkKey PK for DynamoDB chunk table
     * @param s3Key S3 key to use if offloading to S3
     * @param data serialized payload bytes
     * @param allowOverwrite if false, insert-only (skip if exists)
     * @return storage location: {@code "DYNAMODB"} or {@code "S3"}
     */
    String storeData(String chunkKey, String s3Key, byte[] data, boolean allowOverwrite) {
        if (shouldOffloadToS3(data)) {
            storeToS3(s3Key, data, allowOverwrite);
            log.debug("Stored {}KB to S3: {}", data.length / 1024, s3Key);
            return "S3";
        }

        storeToDynamoDB(chunkKey, data, allowOverwrite);
        log.debug("Stored {}KB to DynamoDB chunk table: {}", data.length / 1024, chunkKey);
        return "DYNAMODB";
    }

    // ─── Retrieve operations ────────────────────────────────────────────────────

    /**
     * Retrieves data from the appropriate storage backend.
     *
     * @param refKey reference key (chunk table PK or S3 key)
     * @param refLocation storage location ({@code "DYNAMODB"} or {@code "S3"})
     * @return the raw bytes, or {@code null} if not found
     */
    byte[] retrieveData(String refKey, String refLocation) {
        if ("DYNAMODB".equals(refLocation)) {
            return retrieveFromDynamoDB(refKey);
        }
        if ("S3".equals(refLocation)) {
            return retrieveFromS3(refKey);
        }
        log.error("Invalid storage location: {}", refLocation);
        return null;
    }

    // ─── Batch delete operations ────────────────────────────────────────────────

    /**
     * Deletes multiple data items using batch operations, grouped by location.
     *
     * @param items list of (refKey, refLocation) pairs to delete
     * @return map with "failed" key containing list of failed ref keys
     */
    Map<String, List<String>> batchDeleteData(List<String[]> items) {
        if (items.isEmpty()) {
            return Map.of("failed", List.of());
        }

        // Group items by storage location
        List<String> dynamoDbKeys = new ArrayList<>();
        List<String> s3Keys = new ArrayList<>();

        for (String[] item : items) {
            String refKey = item[0];
            String refLocation = item[1];
            if ("DYNAMODB".equals(refLocation)) {
                dynamoDbKeys.add(refKey);
            } else if ("S3".equals(refLocation)) {
                s3Keys.add(refKey);
            } else {
                log.warn("Invalid storage location: {}", refLocation);
            }
        }

        List<String> failedKeys = new ArrayList<>();

        // Batch delete from DynamoDB
        if (!dynamoDbKeys.isEmpty()) {
            failedKeys.addAll(batchDeleteFromDynamoDB(dynamoDbKeys));
        }

        // Batch delete from S3
        if (!s3Keys.isEmpty()) {
            failedKeys.addAll(batchDeleteFromS3(s3Keys));
        }

        if (!failedKeys.isEmpty()) {
            log.warn("Failed to delete {} item(s): {}", failedKeys.size(), failedKeys);
        }

        return Map.of("failed", failedKeys);
    }

    // ─── DynamoDB storage operations ────────────────────────────────────────────

    private void storeToDynamoDB(String chunkKey, byte[] data, boolean allowOverwrite) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(chunkKey));
        item.put("SK", s("CHUNK"));
        item.put("payload", b(data));

        if (ttlSeconds != null && ttlSeconds > 0) {
            item.put("ttl", n(Instant.now().getEpochSecond() + ttlSeconds));
        }

        PutItemRequest.Builder reqBuilder = PutItemRequest.builder()
                .tableName(tableName)
                .item(item);

        if (!allowOverwrite) {
            reqBuilder.conditionExpression("attribute_not_exists(PK)");
        }

        try {
            dynamoDbClient.putItem(reqBuilder.build());
        } catch (ConditionalCheckFailedException e) {
            if (!allowOverwrite) {
                log.debug("Chunk already exists, skipping: PK={}", chunkKey);
                return;
            }
            throw e;
        }
    }

    private byte[] retrieveFromDynamoDB(String chunkKey) {
        GetItemResponse response = dynamoDbClient.getItem(r -> r
                .tableName(tableName)
                .key(Map.of(
                        "PK", s(chunkKey),
                        "SK", s("CHUNK")
                ))
                .projectionExpression("payload")
        );

        if (!response.hasItem() || !response.item().containsKey("payload")) {
            log.debug("Item not found in chunk table: PK={}", chunkKey);
            return null;
        }

        return response.item().get("payload").b().asByteArray();
    }

    private List<String> batchDeleteFromDynamoDB(List<String> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> failedKeys = new ArrayList<>();

        for (int i = 0; i < chunkKeys.size(); i += BATCH_WRITE_MAX_ITEMS) {
            int end = Math.min(i + BATCH_WRITE_MAX_ITEMS, chunkKeys.size());
            List<String> batch = chunkKeys.subList(i, end);

            List<WriteRequest> deleteRequests = batch.stream()
                    .map(key -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                    .key(Map.of("PK", s(key), "SK", s("CHUNK")))
                                    .build())
                            .build())
                    .toList();

            Map<String, List<WriteRequest>> requestItems = Map.of(tableName, deleteRequests);

            boolean success = processBatchWithRetry(requestItems);
            if (!success) {
                failedKeys.addAll(batch);
            }
        }

        return failedKeys;
    }

    private boolean processBatchWithRetry(Map<String, List<WriteRequest>> initialRequestItems) {
        Map<String, List<WriteRequest>> requestItems = initialRequestItems;
        int retryRound = 0;

        try {
            while (!requestItems.isEmpty() && retryRound <= BATCH_RETRY_MAX_ROUNDS) {
                BatchWriteItemRequest request = BatchWriteItemRequest.builder()
                        .requestItems(requestItems)
                        .build();

                BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(request);

                if (!response.hasUnprocessedItems() || response.unprocessedItems().isEmpty()) {
                    return true;
                }

                requestItems = response.unprocessedItems();
                retryRound++;

                if (retryRound <= BATCH_RETRY_MAX_ROUNDS) {
                    sleepForBackoff(retryRound);
                }
            }
        } catch (Exception e) {
            log.error("Error during DynamoDB batch delete", e);
            return false;
        }

        return requestItems.isEmpty();
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

    // ─── S3 storage operations ──────────────────────────────────────────────────

    private String prefixedS3Key(String s3Key) {
        if (s3KeyPrefix != null) {
            return s3KeyPrefix + "/" + s3Key;
        }
        return s3Key;
    }

    private void storeToS3(String s3Key, byte[] data, boolean allowOverwrite) {
        if (!s3Enabled) {
            String msg = "S3 is not configured but offloading was attempted";
            throw new IllegalStateException(msg);
        }

        String prefixedKey = prefixedS3Key(s3Key);

        PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(prefixedKey);

        // Conditional write: only put if object doesn't exist
        if (!allowOverwrite) {
            reqBuilder.ifNoneMatch("*");
        }

        // Add TTL tag for lifecycle policy expiration
        if (ttlSeconds != null && ttlSeconds > 0) {
            int expirationDays = Math.max(1, (int) Math.ceil((double) ttlSeconds / 86400));
            reqBuilder.tagging(Tagging.builder()
                    .tagSet(Tag.builder()
                            .key("ttl-days")
                            .value(String.valueOf(expirationDays))
                            .build())
                    .build());
        }

        try {
            s3Client.putObject(reqBuilder.build(), RequestBody.fromBytes(data));
            log.debug("Stored to S3: bucket={}, key={}, size={}B", s3Bucket, prefixedKey, data.length);
        } catch (S3Exception e) {
            // PreconditionFailed means object already exists (conditional write)
            if ("PreconditionFailed".equals(e.awsErrorDetails().errorCode()) && !allowOverwrite) {
                log.debug("S3 object already exists, skipping: key={}", prefixedKey);
                return;
            }
            throw e;
        }
    }

    private byte[] retrieveFromS3(String s3Key) {
        if (!s3Enabled) {
            log.error("S3 is not configured but retrieval was attempted");
            return null;
        }

        String prefixedKey = prefixedS3Key(s3Key);

        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(prefixedKey)
                    .build());
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            log.debug("S3 object not found: bucket={}, key={}", s3Bucket, prefixedKey);
            return null;
        } catch (IOException e) {
            String msg = "Failed to read S3 object: " + prefixedKey;
            throw new RuntimeException(msg, e);
        }
    }

    private List<String> batchDeleteFromS3(List<String> s3Keys) {
        if (!s3Enabled) {
            log.warn("S3 is not configured but batch delete was attempted");
            return new ArrayList<>(s3Keys);
        }

        if (s3Keys == null || s3Keys.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> failedKeys = new ArrayList<>();

        for (int i = 0; i < s3Keys.size(); i += S3_DELETE_MAX_OBJECTS) {
            int end = Math.min(i + S3_DELETE_MAX_OBJECTS, s3Keys.size());
            List<String> batch = s3Keys.subList(i, end);

            List<ObjectIdentifier> objects = batch.stream()
                    .map(key -> ObjectIdentifier.builder().key(prefixedS3Key(key)).build())
                    .toList();

            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(s3Bucket)
                        .delete(Delete.builder().objects(objects).build())
                        .build());

                if (response.hasErrors()) {
                    for (var error : response.errors()) {
                        failedKeys.add(error.key());
                        log.warn("Failed to delete S3 object: key={}, code={}", error.key(), error.code());
                    }
                }
            } catch (S3Exception e) {
                log.error("Error during S3 batch delete", e);
                failedKeys.addAll(batch);
            }
        }

        return failedKeys;
    }

    // ─── S3 Lifecycle Policy ────────────────────────────────────────────────────

    /**
     * Configures an S3 lifecycle rule for TTL-based expiration if one does not
     * already exist.
     */
    private void ensureS3LifecyclePolicy() {
        if (!s3Enabled || ttlSeconds == null || ttlSeconds <= 0 || s3Client == null) {
            return;
        }

        try {
            int expirationDays = Math.max(1, (int) Math.ceil((double) ttlSeconds / 86400));
            String idPrefix = s3KeyPrefix != null ? s3KeyPrefix.replace("/", "-") : "root";
            String ruleId = idPrefix + "-ttl-expiration-" + expirationDays + "d";

            // Check existing rules
            List<LifecycleRule> existingRules;
            try {
                var response = s3Client.getBucketLifecycleConfiguration(r -> r.bucket(s3Bucket));
                existingRules = new ArrayList<>(response.rules());

                // Skip if rule already exists
                if (existingRules.stream().anyMatch(r -> ruleId.equals(r.id()))) {
                    log.debug("S3 lifecycle rule '{}' exists in {}", ruleId, s3Bucket);
                    return;
                }
            } catch (S3Exception e) {
                if ("NoSuchLifecycleConfiguration".equals(e.awsErrorDetails().errorCode())) {
                    existingRules = new ArrayList<>();
                } else {
                    throw e;
                }
            }

            // Build lifecycle filter
            LifecycleRuleFilter filter;
            if (s3KeyPrefix != null) {
                filter = LifecycleRuleFilter.builder()
                        .and(a -> a
                                .prefix(s3KeyPrefix + "/")
                                .tags(Tag.builder()
                                        .key("ttl-days")
                                        .value(String.valueOf(expirationDays))
                                        .build()))
                        .build();
            } else {
                filter = LifecycleRuleFilter.builder()
                        .tag(Tag.builder()
                                .key("ttl-days")
                                .value(String.valueOf(expirationDays))
                                .build())
                        .build();
            }

            // Add new rule
            existingRules.add(LifecycleRule.builder()
                    .id(ruleId)
                    .status("Enabled")
                    .filter(filter)
                    .expiration(LifecycleExpiration.builder()
                            .days(expirationDays)
                            .build())
                    .build());

            final List<LifecycleRule> finalRules = existingRules;
            s3Client.putBucketLifecycleConfiguration(r -> r
                    .bucket(s3Bucket)
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                            .rules(finalRules)
                            .build()));

            log.info("Added S3 lifecycle rule '{}' to {}: expire after {} days",
                    ruleId, s3Bucket, expirationDays);

        } catch (S3Exception e) {
            log.warn("Failed to configure S3 lifecycle: {}",
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.getMessage());
        }
    }

    // ─── AttributeValue helpers ─────────────────────────────────────────────────

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
