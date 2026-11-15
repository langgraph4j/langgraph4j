package org.bsc.langgraph4j.checkpoint;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Base class of the DynamoDB V2 savers ({@link DynamoDBSaverV2}, {@link DynamoDBSaverV2Dashboard}).
 *
 * <p>Holds the configuration shared by both, the state serialization and the read path:
 * loading the checkpoints of the live execution, release tags ({@link #tag}) and thread deletion.
 * See {@link DynamoDBRepositoryV2} for the table layout.
 */
public abstract class AbstractDynamoDBSaverV2 extends AbstractCheckpointSaver implements LG4JLoggable {

    /**
     * Builder options shared by the V2 savers.
     *
     * @param <B> concrete builder type
     */
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {

        protected String tableName;
        protected StateSerializer<? extends AgentState> stateSerializer;
        protected DynamoDbClient dynamoDbClient;
        protected Long ttlSeconds;
        protected boolean createTableIfNotExists = false;
        protected boolean dropTableFirst = false;
        protected String s3Bucket;
        protected String s3KeyPrefix;
        protected S3Client s3Client;

        protected AbstractBuilder() {}

        /** Name of the DynamoDB table. Required. */
        public B tableName(String tableName) {
            this.tableName = tableName;
            return (B) this;
        }

        /** Serializer of the agent state. Required. */
        public <State extends AgentState> B stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return (B) this;
        }

        /** Fully configured DynamoDB client, owned by the caller. Required. */
        public B dynamoDbClient(DynamoDbClient client) {
            this.dynamoDbClient = client;
            return (B) this;
        }

        /**
         * Optional retention in seconds: every item carries a {@code ttl} attribute (epoch seconds).
         * Enable TTL on the table's {@code ttl} attribute separately.
         */
        public B ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return (B) this;
        }

        /** Create the table (with its {@code GSI1} index) on build if it does not exist. */
        public B createTableIfNotExists(boolean create) {
            this.createTableIfNotExists = create;
            return (B) this;
        }

        /** Delete and recreate the table on build. Implies {@code createTableIfNotExists}. For tests. */
        public B dropTableFirst(boolean drop) {
            this.dropTableFirst = drop;
            return (B) this;
        }

        /** S3 bucket for payloads larger than 350 KiB. Requires {@link #s3Client}. */
        public B s3Bucket(String s3Bucket) {
            this.s3Bucket = s3Bucket;
            return (B) this;
        }

        /** Optional prefix of the S3 object keys. */
        public B s3KeyPrefix(String s3KeyPrefix) {
            this.s3KeyPrefix = s3KeyPrefix;
            return (B) this;
        }

        /** S3 client, owned by the caller. Required when {@link #s3Bucket} is set. */
        public B s3Client(S3Client s3Client) {
            this.s3Client = s3Client;
            return (B) this;
        }

        protected void validate() {
            requireNonNull(tableName, "'tableName' must not be null");
            requireNonNull(stateSerializer, "'stateSerializer' must not be null");
            requireNonNull(dynamoDbClient, "'dynamoDbClient' must not be null");
            if (s3Bucket != null) {
                requireNonNull(s3Client, "'s3Client' must not be null when 's3Bucket' is configured");
            }
            if (ttlSeconds != null && ttlSeconds <= 0) {
                throw new IllegalArgumentException("'ttlSeconds' must be positive");
            }
        }
    }

    /**
     * Checkpoints of the live execution of a thread.
     *
     * <p>{@link AbstractCheckpointSaver} passes the list returned by {@link #loadCheckpoints} to the
     * insert, update and release hooks while holding its lock, so the {@code ACTIVE} item read on
     * load is reused by those operations without reading DynamoDB again.
     */
    static final class ThreadCheckpoints extends LinkedList<Checkpoint> {
        @Serial
        private static final long serialVersionUID = 1L;

        /** The {@code ACTIVE} item, or {@code null} if the thread has no live execution. */
        final transient DynamoDBRepositoryV2.@Nullable ThreadItem active;

        ThreadCheckpoints(DynamoDBRepositoryV2.@Nullable ThreadItem active) {
            this.active = active;
        }
    }

    protected final DynamoDbClient client;
    protected final StateSerializer<? extends AgentState> stateSerializer;
    final DynamoDBRepositoryV2 repository;

    /**
     * @param builder configuration
     * @param initTable whether the builder's table creation options are applied
     *                  ({@code false} for read-only savers such as the dashboard)
     */
    protected AbstractDynamoDBSaverV2(AbstractBuilder<?> builder, boolean initTable) {
        builder.validate();
        this.client = builder.dynamoDbClient;
        this.stateSerializer = builder.stateSerializer;

        final StorageStrategy storageStrategy = new StorageStrategy(
                builder.dynamoDbClient,
                builder.tableName,
                builder.s3Client,
                builder.s3Bucket,
                builder.s3KeyPrefix,
                builder.ttlSeconds);

        this.repository = new DynamoDBRepositoryV2(
                builder.dynamoDbClient,
                builder.tableName,
                builder.ttlSeconds,
                storageStrategy);

        if (initTable) {
            if (builder.dropTableFirst) {
                repository.dropTable();
            }
            if (builder.dropTableFirst || builder.createTableIfNotExists) {
                repository.createTableIfNotExists();
            }
        }
    }

    // ─── Serialization ───────────────────────────────────────────────────────────

    protected byte[] encodeState(Map<String, Object> data) throws IOException {
        return stateSerializer.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
    }

    protected Map<String, Object> decodeState(byte[] payload, String contentType)
            throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(format(
                    "Content type used to store state '%s' differs from the deserializer's content type '%s'",
                    contentType, stateSerializer.contentType()));
        }
        return stateSerializer.readDataFromString(new String(payload, StandardCharsets.UTF_8)).data();
    }

    // ─── Read path ───────────────────────────────────────────────────────────────

    /** Loads the checkpoints of the live execution: only the {@code ACTIVE} item is visible. */
    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final String threadName = threadId(config);
        final var active = repository.findActiveThread(threadName).orElse(null);

        final ThreadCheckpoints checkpoints = new ThreadCheckpoints(active);
        if (active != null) {
            checkpoints.addAll(loadCheckpoints(threadName, active.threadUid()));
        }
        log.debug("Loaded {} checkpoint(s) for thread '{}'", checkpoints.size(), threadName);
        return checkpoints;
    }

    private LinkedList<Checkpoint> loadCheckpoints(String threadName, String threadUid) {
        final LinkedList<Checkpoint> result = new LinkedList<>();
        for (var record : repository.loadCheckpointRecords(threadUid)) {
            try {
                result.add(Checkpoint.builder()
                        .id(record.checkpointId())
                        .nodeId(record.nodeId())
                        .nextNodeId(record.nextNodeId())
                        .state(decodeState(record.payload(), record.contentType()))
                        .savedAt(new Date(record.savedAt()))
                        .build());
            } catch (Exception e) {
                log.error("Failed to deserialize checkpoint '{}' of thread '{}' – skipping",
                        record.checkpointId(), threadName, e);
            }
        }
        return result;
    }

    /**
     * Returns a released execution of the thread with its checkpoints.
     *
     * @param version the release version, or {@code null} for the latest release
     */
    @Override
    public Optional<Tag> tag(RunnableConfig config, @Nullable Integer version) throws Exception {
        final String threadName = threadId(config);
        return repository.findTag(threadName, version)
                .map(tag -> new Tag(threadName, tag.version(), loadCheckpoints(threadName, tag.threadUid())));
    }

    /**
     * Permanently deletes the thread: its live execution, every release tag, and all their
     * checkpoints and payloads (DynamoDB or S3). Calling it for a missing thread is safe.
     *
     * <p>Specific to the DynamoDB savers; not part of the {@link BaseCheckpointSaver} contract.
     */
    public void deleteThread(String threadName) {
        repository.deleteThread(threadName);
    }

    /** The underlying DynamoDB client, for advanced use-cases or testing. */
    public DynamoDbClient getDynamoDbClient() {
        return client;
    }
}
