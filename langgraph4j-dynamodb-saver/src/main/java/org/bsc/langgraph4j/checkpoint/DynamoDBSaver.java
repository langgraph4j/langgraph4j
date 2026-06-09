package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Amazon DynamoDB-backed checkpoint saver for LangGraph4j.
 *
 * <p>Implements the {@link AbstractCheckpointSaver} contract and stores all checkpoints
 * in a single DynamoDB table using a composite primary key ({@code PK} + {@code SK}).
 * Checkpoint state is serialized via the supplied {@link StateSerializer} and stored as
 * a separate "chunk" item to keep the metadata item well within DynamoDB's 400 KB limit.
 *
 * <h2>Table layout</h2>
 * <pre>
 *  PK                                    SK                   Purpose
 *  ─────────────────────────────────     ─────────────────    ─────────────────────────────
 *  CHECKPOINT_{threadId}                 {checkpointId}       Checkpoint metadata
 *  CHUNK_{threadId}#{checkpointId}       CHUNK                Serialized state payload
 *  RELEASED_{threadId}                   MARKER               Thread-released sentinel
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DynamoDbClient client = DynamoDbClient.builder()
 *     .region(Region.US_EAST_1)
 *     .build();
 * 
 * DynamoDBSaver saver = DynamoDBSaver.builder()
 *     .tableName("lg4j-checkpoints")
 *     .dynamoDbClient(client)
 *     .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
 *     .createTableIfNotExists(true)
 *     .build();
 * }</pre>
 *
 * <p>For local development / testing, point to a local DynamoDB endpoint:
 * <pre>{@code
 * DynamoDbClient localClient = DynamoDbClient.builder()
 *     .endpointOverride(URI.create("http://localhost:8000"))
 *     .region(Region.US_EAST_1)
 *     .build();
 *
 * DynamoDBSaver saver = DynamoDBSaver.builder()
 *     .tableName("lg4j-checkpoints")
 *     .dynamoDbClient(localClient)
 *     .stateSerializer(serializer)
 *     .createTableIfNotExists(true)
 *     .dropTableFirst(true)
 *     .build();
 * }</pre>
 */
public class DynamoDBSaver extends AbstractCheckpointSaver {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBSaver.class);

    // ─── Builder ────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link DynamoDBSaver}.
     */
    public static class Builder {

        private String tableName;
        private StateSerializer<? extends AgentState> stateSerializer;
        private boolean plainTextStateSerializerLegacyMode = false;
        private Long ttlSeconds;
        private boolean createTableIfNotExists = false;
        private boolean dropTableFirst = false;
        /** Inject a fully configured client (required). */
        private DynamoDbClient dynamoDbClient;
        private String s3Bucket;
        private String s3KeyPrefix;
        /** Inject a pre-configured S3 client (required if s3Bucket is set). */
        private S3Client s3Client;

        Builder() {}

        /** Name of the DynamoDB table. Required. */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * The state serializer used to convert {@code Map<String,Object>} state to/from bytes.
         * Required.
         */
        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Enables compatibility mode for {@link PlainTextStateSerializer}-based payloads.
         * When {@code true}, the JSON payload is stored as a serialized Java String (binary).
         * Ignored if the serializer is not a {@link PlainTextStateSerializer}.
         */
        public Builder plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return this;
        }

        /**
         * Optional time-to-live in seconds. When set, every item written to DynamoDB
         * will carry a {@code ttl} attribute (epoch seconds) so the table's TTL feature
         * can automatically expire old checkpoints.
         */
        public Builder ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        /**
         * When {@code true}, the table is created (via {@code CreateTable}) if it does not
         * already exist. Automatically implies {@code createTableIfNotExists = true} when
         * {@link #dropTableFirst(boolean)} is also set.
         */
        public Builder createTableIfNotExists(boolean create) {
            this.createTableIfNotExists = create;
            return this;
        }

        /**
         * When {@code true}, the table is deleted and recreated on startup.
         * Implies {@link #createTableIfNotExists(boolean) createTableIfNotExists = true}.
         * Useful for tests.
         */
        public Builder dropTableFirst(boolean drop) {
            this.dropTableFirst = drop;
            return this;
        }

        /**
         * Inject a fully configured {@link DynamoDbClient}. Required.
         */
        public Builder dynamoDbClient(DynamoDbClient client) {
            this.dynamoDbClient = client;
            return this;
        }

        /**
         * S3 bucket name for large-payload offloading. When set,
         * payloads exceeding 350KB are automatically stored
         * in S3 instead of DynamoDB.
         */
        public Builder s3Bucket(String s3Bucket) {
            this.s3Bucket = s3Bucket;
            return this;
        }

        /**
         * Optional prefix prepended to all S3 keys. Useful for bucket-level
         * isolation between environments or applications.
         */
        public Builder s3KeyPrefix(String s3KeyPrefix) {
            this.s3KeyPrefix = s3KeyPrefix;
            return this;
        }

        /**
         * Inject a pre-configured {@link S3Client}. Required if an s3Bucket is configured.
         */
        public Builder s3Client(S3Client s3Client) {
            this.s3Client = s3Client;
            return this;
        }

        /** Build the {@link DynamoDBSaver}. */
        public DynamoDBSaver build() {
            requireNonNull(tableName, "'tableName' must not be null");
            requireNonNull(stateSerializer, "'stateSerializer' must not be null");
            requireNonNull(dynamoDbClient, "'dynamoDbClient' must not be null");

            if (s3Bucket != null) {
                requireNonNull(s3Client, "'s3Client' must not be null when 's3Bucket' is configured");
            }

            // dropTableFirst implies createTableIfNotExists
            if (dropTableFirst) {
                createTableIfNotExists = true;
            }

            return new DynamoDBSaver(this);
        }
    }

    /** Factory method. */
    public static Builder builder() {
        return new Builder();
    }

    // ─── Fields ─────────────────────────────────────────────────────────────────

    private final DynamoDbClient client;
    private final DynamoDBRepository repository;
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final boolean plainTextStateSerializerLegacyMode;

    // ─── Constructor ─────────────────────────────────────────────────────────────

    private DynamoDBSaver(Builder builder) {
        this.client = builder.dynamoDbClient;
        this.stateSerializer = builder.stateSerializer;
        this.plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;

        // Create StorageStrategy for payload routing (DynamoDB vs S3)
        StorageStrategy storageStrategy = new StorageStrategy(
            this.client,
            builder.tableName,
            builder.s3Client,
            builder.s3Bucket,
            builder.s3KeyPrefix,
            builder.ttlSeconds
        );

        this.repository = new DynamoDBRepository(
            this.client,
            builder.tableName,
            builder.ttlSeconds,
            storageStrategy
        );

        // Table initialization
        if (builder.dropTableFirst) {
            log.debug("Dropping DynamoDB table '{}'", builder.tableName);
            repository.dropTable();
        }
        if (builder.createTableIfNotExists) {
            log.debug("Creating DynamoDB table '{}' (if not exists)", builder.tableName);
            repository.createTableIfNotExists();
        }
    }

    // ─── Serialization Helpers ───────────────────────────────────────────────────

    /**
     * Serialize the agent state map to Base64-encoded bytes (same pattern as PostgresSaver).
     */
    private byte[] encodeState(Map<String, Object> data) throws IOException {
        final byte[] binaryData;
        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            binaryData = ser.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
        } else {
            binaryData = stateSerializer.dataToBytes(data);
        }
        return binaryData;
    }

    /**
     * Deserialize state bytes back to a {@code Map<String,Object>}.
     */
    private Map<String, Object> decodeState(byte[] payload, String contentType)
            throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(format(
                "Content type used to store state '%s' differs from the deserializer's content type '%s'",
                contentType, stateSerializer.contentType()
            ));
        }
        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            return ser.readDataFromString(new String(payload, StandardCharsets.UTF_8));
        }
        return stateSerializer.dataFromBytes(payload);
    }

    // ─── AbstractCheckpointSaver template methods ────────────────────────────────

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final String threadId = threadId(config);

        // If this thread has been released, return empty (same semantics as PostgresSaver)
        if (repository.isThreadReleased(threadId)) {
            log.debug("Thread '{}' is released – returning empty checkpoint list", threadId);
            return new LinkedList<>();
        }

        final LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        final List<DynamoDBRepository.CheckpointRecord> records = repository.loadCheckpointRecords(threadId);

        for (DynamoDBRepository.CheckpointRecord record : records) {
            try {
                Map<String, Object> state = decodeState(record.payload(), record.contentType());
                Checkpoint checkpoint = Checkpoint.builder()
                    .id(record.checkpointId())
                    .nodeId(record.nodeId())
                    .nextNodeId(record.nextNodeId())
                    .state(state)
                    .build();
                checkpoints.add(checkpoint);
            } catch (Exception e) {
                log.error("Failed to deserialize checkpoint '{}' for thread '{}' – skipping",
                    record.checkpointId(), threadId, e);
            }
        }

        log.debug("Loaded {} checkpoint(s) for thread '{}'", checkpoints.size(), threadId);
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config,
                                      LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) throws Exception {
        final String threadId = threadId(config);
        final String parentCheckpointId = config.checkPointId().orElse(null);
        log.debug("Inserting checkpoint '{}' for thread '{}' (parent='{}')",
                checkpoint.getId(), threadId, parentCheckpointId);

        final byte[] payload = encodeState(checkpoint.getState());
        repository.putCheckpoint(
            threadId,
            checkpoint.getId(),
            checkpoint.getNodeId(),
            checkpoint.getNextNodeId(),
            payload,
            stateSerializer.contentType(),
            parentCheckpointId,
            false  // insert-only: conditional write
        );
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {
        final String threadId = threadId(config);

        // Delete the old checkpoint (identified by the config's checkpointId) then insert new
        config.checkPointId().ifPresent(oldId -> {
            log.debug("Deleting old checkpoint '{}' before update, thread '{}'", oldId, threadId);
            repository.deleteCheckpoint(threadId, oldId);
        });

        final String parentCheckpointId = config.checkPointId().orElse(null);
        log.debug("Updating checkpoint '{}' for thread '{}' (parent='{}')",
                checkpoint.getId(), threadId, parentCheckpointId);
        final byte[] payload = encodeState(checkpoint.getState());
        repository.putCheckpoint(
            threadId,
            checkpoint.getId(),
            checkpoint.getNodeId(),
            checkpoint.getNextNodeId(),
            payload,
            stateSerializer.contentType(),
            parentCheckpointId,
            true  // update path: allow overwrite
        );
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final String threadId = threadId(config);
        log.debug("Releasing thread '{}'", threadId);
        repository.markThreadReleased(threadId);
        return new Tag(threadId, checkpoints);
    }

    /**
     * Deletes all checkpoints and associated data for the given thread.
     *
     * <p>This is a <em>hard delete</em> that permanently removes all checkpoint
     * metadata items, payload chunk items, and the released sentinel (if present)
     * from DynamoDB. The operation is idempotent: calling it on a non-existent
     * or already-deleted thread is a no-op.
     *
     * <p>Note: this method is specific to {@link DynamoDBSaver} and is not part
     * of the {@link BaseCheckpointSaver} contract.
     *
     * @param threadId the thread identifier whose data should be deleted
     */
    public void deleteThread(String threadId) {
        repository.deleteThread(threadId);
    }

    /**
     * Returns the underlying {@link DynamoDbClient} for advanced use-cases or testing.
     */
    public DynamoDbClient getDynamoDbClient() {
        return client;
    }
}
