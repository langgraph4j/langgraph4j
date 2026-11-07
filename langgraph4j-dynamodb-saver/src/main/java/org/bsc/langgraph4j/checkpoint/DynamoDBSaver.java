package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.StdStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Amazon DynamoDB-backed checkpoint saver for LangGraph4j.
 *
 * <p>Implements the {@link AbstractCheckpointSaver} contract and stores all checkpoints
 * in a single DynamoDB table using a composite primary key ({@code PK} + {@code SK}).
 * Checkpoint state is serialized via the supplied {@link StdStateSerializer} and stored as
 * a separate "chunk" item to keep the metadata item well within DynamoDB's 400 KB limit.
 *
 * <h2>Table layout</h2>
 * <pre>
 *  PK                                    SK                   Purpose
 *  ─────────────────────────────────     ─────────────────    ─────────────────────────────
 *  CHECKPOINT_{threadId}                 {checkpointId}       Checkpoint metadata (generation 0)
 *  CHECKPOINT_{threadId}#{generation}    {checkpointId}       Checkpoint metadata (generation &gt; 0)
 *  CHUNK_{threadId}#{checkpointId}       CHUNK                Serialized state payload
 *  RELEASED_{threadId}                   MARKER               Current generation of the thread
 * </pre>
 *
 * <p>Releasing a thread archives its checkpoints by starting a new <em>generation</em>,
 * so the same thread id can be reused for a fresh execution (see {@link DynamoDBRepository}).
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
public class DynamoDBSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBSaver.class);

    // ─── Builder ────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link DynamoDBSaver}.
     */
    public static class Builder {

        private String tableName;
        private StateSerializer<? extends AgentState> stateSerializer;
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

    // ─── Constructor ─────────────────────────────────────────────────────────────

    private DynamoDBSaver(Builder builder) {
        this.client = builder.dynamoDbClient;
        this.stateSerializer = builder.stateSerializer;

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
        return stateSerializer.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
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
        return stateSerializer.readDataFromString(new String(payload, StandardCharsets.UTF_8)).data();
    }

    // ─── AbstractCheckpointSaver template methods ────────────────────────────────

    /**
     * Checkpoints of one generation of a thread.
     *
     * <p>{@link AbstractCheckpointSaver} passes the list returned by {@link #loadCheckpoints}
     * to {@link #insertedCheckpoint}, {@link #updatedCheckpoint} and {@link #releaseCheckpoints}
     * while holding its lock, so the generation read on load is reused by those operations
     * without reading DynamoDB again.
     */
    static final class GenerationCheckpoints extends LinkedList<Checkpoint> {
        @Serial
        private static final long serialVersionUID = 1L;

        final long generation;

        GenerationCheckpoints(long generation) {
            this.generation = generation;
        }
    }

    /**
     * Returns the generation the given checkpoints were loaded from, reading it
     * from DynamoDB only if the list was not produced by {@link #loadCheckpoints}.
     */
    private long generationOf(String threadId, LinkedList<Checkpoint> checkpoints) {
        return (checkpoints instanceof GenerationCheckpoints loaded)
                ? loaded.generation
                : repository.currentGeneration(threadId);
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final String threadId = threadId(config);

        // Only the current generation is visible: released generations stay archived
        final long generation = repository.currentGeneration(threadId);

        final LinkedList<Checkpoint> checkpoints = new GenerationCheckpoints(generation);
        final List<DynamoDBRepository.CheckpointRecord> records = repository.loadCheckpointRecords(threadId, generation);

        for (DynamoDBRepository.CheckpointRecord record : records) {
            try {
                Map<String, Object> state = decodeState(record.payload(), record.contentType());
                Checkpoint checkpoint = Checkpoint.builder()
                    .id(record.checkpointId())
                    .nodeId(record.nodeId())
                    .nextNodeId(record.nextNodeId())
                    .state(state)
                    .savedAt( new Date(record.savedAt()))
                    .build();
                checkpoints.add(checkpoint);
            } catch (Exception e) {
                log.error("Failed to deserialize checkpoint '{}' for thread '{}' – skipping",
                    record.checkpointId(), threadId, e);
            }
        }

        log.debug("Loaded {} checkpoint(s) for thread '{}' (generation {})", checkpoints.size(), threadId, generation);
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config,
                                      LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) throws Exception {
        final String threadId = threadId(config);
        final long generation = generationOf(threadId, checkpoints);
        final String parentCheckpointId = config.checkPointId().orElse(null);
        log.debug("Inserting checkpoint '{}' for thread '{}' (generation {}, parent='{}')",
                checkpoint.id(), threadId, generation, parentCheckpointId);

        final byte[] payload = encodeState(checkpoint.state());
        repository.putCheckpoint(
            threadId,
            generation,
            checkpoint.id(),
            checkpoint.nodeId(),
            checkpoint.nextNodeId(),
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
        final long generation = generationOf(threadId, checkpoints);

        // Delete the old checkpoint (identified by the config's checkpointId) then insert new
        config.checkPointId().ifPresent(oldId -> {
            log.debug("Deleting old checkpoint '{}' before update, thread '{}'", oldId, threadId);
            repository.deleteCheckpoint(threadId, generation, oldId);
        });

        final String parentCheckpointId = config.checkPointId().orElse(null);
        log.debug("Updating checkpoint '{}' for thread '{}' (generation {}, parent='{}')",
                checkpoint.id(), threadId, generation, parentCheckpointId);
        final byte[] payload = encodeState(checkpoint.state());
        repository.putCheckpoint(
            threadId,
            generation,
            checkpoint.id(),
            checkpoint.nodeId(),
            checkpoint.nextNodeId(),
            payload,
            stateSerializer.contentType(),
            parentCheckpointId,
            true  // update path: allow overwrite
        );
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final String threadId = threadId(config);
        final long generation = generationOf(threadId, checkpoints);
        log.debug("Releasing thread '{}' (generation {})", threadId, generation);
        // archive the current generation: the next execution of this thread id starts empty
        repository.startNextGeneration(threadId, generation);
        return new Tag(threadId, checkpoints);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, exception.getMessage());
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
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
