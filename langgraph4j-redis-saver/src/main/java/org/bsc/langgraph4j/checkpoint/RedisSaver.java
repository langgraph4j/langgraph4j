package org.bsc.langgraph4j.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.redisson.Redisson;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.config.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * <p>RedisSaver is an extension of MemorySaver that enables persistent,
 * high-performance storage of workflow state in Redis.</p>
 * <p>
 * <b>Redis Data Structures Used:</b>
 * <ul>
 *   <li>Hash: stores thread metadata</li>
 *   <li>String: stores active thread lookup by name</li>
 *   <li>Hash: stores checkpoint data</li>
 *   <li>Sorted Set: stores ordered checkpoints by timestamp</li>
 * </ul>
 * </p>
 * <p>
 * <b>Two configuration modes:</b>
 * </p>
 * <ol>
 *   <li><b>Direct configuration:</b> Provide host, port, username, password, database</li>
 *   <li><b>Inject RedissonClient:</b> Reuse an existing RedissonClient from your project</li>
 * </ol>
 * <p>
 * <b>Usage Example (Direct Configuration):</b>
 * <pre>
 * var saver = RedisSaver.builder()
 *         .host("localhost")
 *         .port(6379)
 *         .password("your-password")
 *         .database(0)
 *         .build();
 * </pre>
 * </p>
 * <p>
 * <b>Usage Example (Inject RedissonClient):</b>
 * <pre>
 * Config config = new Config();
 * config.useSingleServer().setAddress("redis://localhost:6379");
 * RedissonClient redissonClient = Redisson.create(config);
 *
 * var saver = RedisSaver.builder()
 *         .redissonClient(redissonClient)
 *         .build();
 * </pre>
 * </p>
 */
public class RedisSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    // Hash field names
    private static final String THREAD_ID_FIELD = "thread_id";
    private static final String THREAD_NAME_FIELD = "thread_name";
    private static final String IS_RELEASED_FIELD = "is_released";
    private static final String CREATED_AT_FIELD = "created_at";

    private static final String CHECKPOINT_ID_FIELD = "checkpoint_id";
    private static final String THREAD_ID_REF_FIELD = "thread_id";
    private static final String NODE_ID_FIELD = "node_id";
    private static final String NEXT_NODE_ID_FIELD = "next_node_id";
    private static final String STATE_DATA_FIELD = "state_data";
    private static final String STATE_CONTENT_TYPE_FIELD = "state_content_type";
    private static final String STATE_ENCODING_FIELD = "state_encoding";
    private static final String STATE_SCHEMA_VERSION_FIELD = "schema_version";
    private static final String SAVED_AT_FIELD = "saved_at";
    private static final String CURRENT_STATE_SCHEMA_VERSION = "1";

    // Configuration
    private final RedissonClient redissonClient;
    private final KeyNamingStrategy keyNamingStrategy;
    private final ObjectMapper objectMapper;
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final long ttl;
    private final TimeUnit ttlUnit;

    private enum StateEncoding {
        SERIALIZER_BYTES("serializer-bytes"),
        PLAIN_TEXT_UTF8("plain-text-utf8");

        private final String redisValue;

        StateEncoding(String redisValue) {
            this.redisValue = redisValue;
        }

        static Optional<StateEncoding> fromRedisValue(String redisValue) {
            if (redisValue == null || redisValue.isBlank()) {
                return Optional.empty();
            }
            for (StateEncoding encoding : values()) {
                if (encoding.redisValue.equals(redisValue)) {
                    return Optional.of(encoding);
                }
            }
            return Optional.empty();
        }
    }

    private record EncodedState(String payload, String contentType, String encodingValue) {}

    /**
     * A builder for RedisSaver.
     * <p>
     * Supports two configuration modes:
     * <ol>
     *   <li>Direct configuration: Set host, port, username, password, database</li>
     *   <li>Inject RedissonClient: Call {@code redissonClient()} to reuse an existing client</li>
     * </ol>
     * If {@code redissonClient()} is called, direct config options are ignored.
     * </p>
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String username = null;
        private String password = null;
        private int database = 0;
        private int connectionTimeout = 3000;
        private int retryInterval = 1500;
        private int retryAttempts = 3;
        private RedissonClient redissonClient = null;
        private KeyNamingStrategy keyNamingStrategy = null;
        private StateSerializer<? extends AgentState> stateSerializer;
        private long ttl = -1;
        private TimeUnit ttlUnit = TimeUnit.MINUTES;

        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Sets the Redis host.
         *
         * @param host the Redis host (default: "localhost")
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Redis port.
         *
         * @param port the Redis port (default: 6379)
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the Redis username (for Redis 6+ ACL).
         *
         * @param username the Redis username (optional)
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the Redis password.
         *
         * @param password the Redis password (optional)
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the Redis database index.
         *
         * @param database the database index (default: 0)
         * @return this builder
         */
        public Builder database(int database) {
            this.database = database;
            return this;
        }

        /**
         * Sets the connection timeout in milliseconds.
         *
         * @param connectionTimeout the connection timeout (default: 3000)
         * @return this builder
         */
        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Sets the retry interval in milliseconds.
         *
         * @param retryInterval the retry interval (default: 1500)
         * @return this builder
         */
        public Builder retryInterval(int retryInterval) {
            this.retryInterval = retryInterval;
            return this;
        }

        /**
         * Sets the number of retry attempts.
         *
         * @param retryAttempts the retry attempts (default: 3)
         * @return this builder
         */
        public Builder retryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
            return this;
        }

        /**
         * Sets a custom key naming strategy.
         *
         * @param keyNamingStrategy the custom key naming strategy (optional)
         * @return this builder
         */
        public Builder keyNamingStrategy(KeyNamingStrategy keyNamingStrategy) {
            this.keyNamingStrategy = keyNamingStrategy;
            return this;
        }

        /**
         * Sets the time-to-live (TTL) for stored keys.
         * <p>
         * By default, TTL is -1 (never expire). Setting a TTL will automatically
         * expire keys after the specified time, which can be useful for automatic cleanup.
         * </p>
         * <p>
         * Note: TTL is applied to checkpoint keys and thread name lookup keys.
         * The thread hash and checkpoints sorted set are not directly TTL'd to maintain
         * consistency during the thread lifecycle.
         * </p>
         *
         * @param ttl time to live value, use -1 for no expiration (default)
         * @param ttlUnit time unit for the ttl (default: MINUTES)
         * @return this builder
         */
        public Builder ttl(long ttl, TimeUnit ttlUnit) {
            this.ttl = ttl;
            this.ttlUnit = ttlUnit;
            return this;
        }

        /**
         * Sets the RedissonClient to reuse.
         * <p>
         * If this method is called, direct configuration options (host, port, etc.) are ignored.
         * The caller is responsible for managing the lifecycle of the provided client.
         * </p>
         *
         * @param redissonClient the RedissonClient to reuse (required)
         * @return this builder
         */
        public Builder redissonClient(RedissonClient redissonClient) {
            this.redissonClient = redissonClient;
            return this;
        }

        /**
         * Creates a new instance of RedisSaver.
         *
         * @return the new instance of RedisSaver
         */
        public RedisSaver build() {
            if (redissonClient == null) {
                // Create new client with direct config
                Config config = new Config();
                config.useSingleServer()
                        .setAddress("redis://" + host + ":" + port)
                        .setDatabase(database)
                        .setConnectionPoolSize(10)
                        .setConnectionMinimumIdleSize(2)
                        .setConnectTimeout(connectionTimeout)
                        .setRetryInterval(retryInterval)
                        .setRetryAttempts(retryAttempts);

                if (password != null && !password.isEmpty()) {
                    if (username != null && !username.isEmpty()) {
                        config.useSingleServer().setUsername(username);
                    }
                    config.useSingleServer().setPassword(password);
                }

                redissonClient = Redisson.create(config);
            }

            return new RedisSaver(this);
        }
    }

    /**
     * Creates an instance of a builder that allows to configure and create a new
     * instance of RedisSaver.
     *
     * @return a new instance of the builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Private constructor used by the builder.
     *
     * @param builder     Builder instance
     */
    private RedisSaver(Builder builder) {
        this.redissonClient = Objects.requireNonNull(builder.redissonClient, "redissonClient cannot be null");
        this.keyNamingStrategy = builder.keyNamingStrategy != null ? builder.keyNamingStrategy : new DefaultKeyNamingStrategy();
        this.stateSerializer = builder.stateSerializer;
        this.objectMapper = (builder.stateSerializer == null) ? new ObjectMapper() : null;
        this.ttl = builder.ttl;
        this.ttlUnit = builder.ttlUnit;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();
        final var threadName = threadId(config);
        final var threadNameKey = keyNamingStrategy.threadNameKey(threadName);

        // Get active thread ID by name
        final var threadId = redissonClient.<String>getBucket(threadNameKey, StringCodec.INSTANCE).get();

        if (threadId == null) {
            return checkpoints; // No active thread
        }

        // Check if thread is released
        final var threadKey = keyNamingStrategy.threadKey(threadId);
        RMap<String, String> threadMap = redissonClient.getMap(threadKey, StringCodec.INSTANCE);
        final var isReleased = threadMap.get(IS_RELEASED_FIELD);

        if ("1".equals(isReleased)) {
            return checkpoints; // Thread is released
        }

        // Get checkpoint IDs from sorted set (ordered by timestamp descending)
        final var checkpointsKey = keyNamingStrategy.checkpointsKey(threadId);
        RScoredSortedSet<String> checkpointsSet = redissonClient.getScoredSortedSet(checkpointsKey, StringCodec.INSTANCE);

        // Get checkpoints in descending order by score (timestamp)
        Collection<ScoredEntry<String>> scoredEntries = checkpointsSet.entryRangeReversed(0, -1);

        if (scoredEntries != null) {
            for (ScoredEntry<String> entry : scoredEntries) {
                String checkpointId = entry.getValue();
                String checkpointKey = keyNamingStrategy.checkpointKey(checkpointId);
                RMap<String, String> checkpointMap = redissonClient.getMap(checkpointKey, StringCodec.INSTANCE);

                if (!checkpointMap.isExists()) {
                    continue; // Checkpoint was deleted
                }

                String statePayload = checkpointMap.get(STATE_DATA_FIELD);
                if (statePayload == null) {
                    continue;
                }

                String stateContentType = checkpointMap.get(STATE_CONTENT_TYPE_FIELD);
                String stateEncoding = checkpointMap.get(STATE_ENCODING_FIELD);
                Map<String, Object> state = decodeState(statePayload, stateContentType, stateEncoding);

                Checkpoint checkpoint = Checkpoint.builder()
                        .id(checkpointMap.get(CHECKPOINT_ID_FIELD))
                        .nodeId(checkpointMap.get(NODE_ID_FIELD))
                        .nextNodeId(checkpointMap.get(NEXT_NODE_ID_FIELD))
                        .state(state)
                        .build();
                checkpoints.add(checkpoint);
            }
        }

        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        final String threadName = config.threadId().orElse(THREAD_ID_DEFAULT);
        final long timestamp = Instant.now().toEpochMilli();
        final String threadNameKey = keyNamingStrategy.threadNameKey(threadName);

        // Use batch for atomic operations
        RBatch batch = redissonClient.createBatch();

        // Get or create thread ID
        String threadId = redissonClient.<String>getBucket(threadNameKey, StringCodec.INSTANCE).get();

        if (threadId == null) {
            threadId = UUID.randomUUID().toString();
            // Create thread hash
            String threadKey = keyNamingStrategy.threadKey(threadId);
            batch.getMap(threadKey, StringCodec.INSTANCE).fastPutAsync(THREAD_ID_FIELD, threadId);
            batch.getMap(threadKey, StringCodec.INSTANCE).fastPutAsync(THREAD_NAME_FIELD, threadName);
            batch.getMap(threadKey, StringCodec.INSTANCE).fastPutAsync(IS_RELEASED_FIELD, "0");
            batch.getMap(threadKey, StringCodec.INSTANCE).fastPutAsync(CREATED_AT_FIELD, String.valueOf(timestamp));
            // Set active thread name key
            batch.getBucket(threadNameKey, StringCodec.INSTANCE).setAsync(threadId);
        }

        // Insert checkpoint
        String checkpointId = checkpoint.getId();
        String checkpointKey = keyNamingStrategy.checkpointKey(checkpointId);
        EncodedState encodedState = encodeState(checkpoint.getState());

        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(CHECKPOINT_ID_FIELD, checkpointId);
        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(THREAD_ID_REF_FIELD, threadId);
        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(NODE_ID_FIELD, checkpoint.getNodeId() != null ? checkpoint.getNodeId() : "");
        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(NEXT_NODE_ID_FIELD, checkpoint.getNextNodeId() != null ? checkpoint.getNextNodeId() : "");
        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_DATA_FIELD, encodedState.payload());
        if (encodedState.contentType() != null) {
            batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_CONTENT_TYPE_FIELD, encodedState.contentType());
        }
        if (encodedState.encodingValue() != null) {
            batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_ENCODING_FIELD, encodedState.encodingValue());
            batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_SCHEMA_VERSION_FIELD, CURRENT_STATE_SCHEMA_VERSION);
        }
        batch.getMap(checkpointKey, StringCodec.INSTANCE).fastPutAsync(SAVED_AT_FIELD, String.valueOf(timestamp));

        // Add to sorted set with timestamp as score
        String checkpointsKey = keyNamingStrategy.checkpointsKey(threadId);
        batch.getScoredSortedSet(checkpointsKey, StringCodec.INSTANCE).addAsync(timestamp, checkpointId);

        // Execute batch atomically
        batch.execute();

        // Set TTL after batch execution (if configured)
        if (ttl >= 0) {
            long ttlMillis = ttlUnit.toMillis(ttl);
            redissonClient.getBucket(threadNameKey, StringCodec.INSTANCE).expire( Duration.ofMillis(ttlMillis) );
            redissonClient.getBucket(checkpointKey, StringCodec.INSTANCE).expire( Duration.ofMillis(ttlMillis) );
            // Note: checkpointsKey (Sorted Set) TTL is set on first creation or can be refreshed
            // We don't set it here to avoid resetting on every checkpoint insert
        }
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        if (config.checkPointId().isPresent()) {
            final String threadName = config.threadId().orElse(THREAD_ID_DEFAULT);
            final long timestamp = Instant.now().toEpochMilli();
            final String oldCheckpointId = config.checkPointId().get();
            final String newCheckpointId = checkpoint.getId();
            final String threadNameKey = keyNamingStrategy.threadNameKey(threadName);

            String threadId = redissonClient.<String>getBucket(threadNameKey, StringCodec.INSTANCE).get();

            if (threadId == null) {
                throw new IllegalStateException("Thread not found: " + threadName);
            }

            // Use batch for atomic operations
            RBatch batch = redissonClient.createBatch();

            // Delete old checkpoint
            String oldCheckpointKey = keyNamingStrategy.checkpointKey(oldCheckpointId);
            batch.getBucket(oldCheckpointKey, StringCodec.INSTANCE).deleteAsync();

            // Remove from sorted set
            String checkpointsKey = keyNamingStrategy.checkpointsKey(threadId);
            batch.getScoredSortedSet(checkpointsKey, StringCodec.INSTANCE).removeAsync(oldCheckpointId);

            // Insert new checkpoint
            String newCheckpointKey = keyNamingStrategy.checkpointKey(newCheckpointId);
            EncodedState encodedState = encodeState(checkpoint.getState());

            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(CHECKPOINT_ID_FIELD, newCheckpointId);
            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(THREAD_ID_REF_FIELD, threadId);
            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(NODE_ID_FIELD, checkpoint.getNodeId() != null ? checkpoint.getNodeId() : "");
            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(NEXT_NODE_ID_FIELD, checkpoint.getNextNodeId() != null ? checkpoint.getNextNodeId() : "");
            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_DATA_FIELD, encodedState.payload());
            if (encodedState.contentType() != null) {
                batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_CONTENT_TYPE_FIELD, encodedState.contentType());
            }
            if (encodedState.encodingValue() != null) {
                batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_ENCODING_FIELD, encodedState.encodingValue());
                batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(STATE_SCHEMA_VERSION_FIELD, CURRENT_STATE_SCHEMA_VERSION);
            }
            batch.getMap(newCheckpointKey, StringCodec.INSTANCE).fastPutAsync(SAVED_AT_FIELD, String.valueOf(timestamp));

            // Add to sorted set with new timestamp
            batch.getScoredSortedSet(checkpointsKey, StringCodec.INSTANCE).addAsync(timestamp, newCheckpointId);

            // Execute batch atomically
            batch.execute();
        } else {
            insertedCheckpoint(config, checkpoints, checkpoint);
        }
    }


    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var threadName = threadId(config);
        final var threadNameKey = keyNamingStrategy.threadNameKey(threadName);

        final var threadId = redissonClient.<String>getBucket(threadNameKey, StringCodec.INSTANCE).get();

        if (threadId != null) {

            // Use batch for atomic operations
            final var batch = redissonClient.createBatch();

            // Mark thread as released
            final var threadKey = keyNamingStrategy.threadKey(threadId);
            batch.getMap(threadKey, StringCodec.INSTANCE).fastPutAsync(IS_RELEASED_FIELD, "1");

            // Remove active thread lookup
            batch.getBucket(threadNameKey, StringCodec.INSTANCE).deleteAsync();

            // Execute batch atomically
            batch.execute();
        }

        return new Tag( threadName, checkpoints);
    }


    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
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
     * Cleanup all data for a specific thread.
     * <p>
     * This method deletes all checkpoints and metadata for the given thread ID.
     * </p>
     *
     * @param threadId the thread ID to cleanup
     */
    public void cleanupThread(String threadId) {
        String threadKey = keyNamingStrategy.threadKey(threadId);
        String checkpointsKey = keyNamingStrategy.checkpointsKey(threadId);

        // Get all checkpoint IDs from sorted set
        RScoredSortedSet<String> checkpointsSet = redissonClient.getScoredSortedSet(checkpointsKey, StringCodec.INSTANCE);
        Iterable<String> checkpointIds = checkpointsSet.readAll();

        // Delete all checkpoint hashes
        for (String checkpointId : checkpointIds) {
            String checkpointKey = keyNamingStrategy.checkpointKey(checkpointId);
            redissonClient.getBucket(checkpointKey, StringCodec.INSTANCE).delete();
        }

        // Delete checkpoints sorted set
        redissonClient.getBucket(checkpointsKey, StringCodec.INSTANCE).delete();

        // Delete thread hash
        redissonClient.getBucket(threadKey, StringCodec.INSTANCE).delete();
    }

    /**
     * Cleanup all langgraph4j keys in Redis.
     * <p>
     * <b>Warning:</b> This deletes all data managed by this saver. Use with caution.
     * </p>
     */
    public void cleanupAll() {
        String prefix = keyNamingStrategy.keyPrefix();
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(prefix + "*");
        for (String key : keys) {
            redissonClient.getBucket(key, StringCodec.INSTANCE).delete();
        }
    }

    /**
     * Removes the cached checkpoints associated with the given thread identifier from the in-memory cache.
     *
     * @param threadId the thread identifier whose cached checkpoints must be cleared
     * @return the checkpoints removed from the cache, or an empty collection if no cached checkpoints exist
     * @deprecated this method do nothing because currently this saver don't use cache anymore
     */
    @Deprecated(forRemoval = true)
    public Collection<Checkpoint> clearCheckpointsCache( String threadId ) {
        return List.of();
    }

    private EncodedState encodeState(Map<String, Object> data) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");

        if (stateSerializer == null) {
            return new EncodedState(objectMapper.writeValueAsString(data), null, null);
        }

        if (stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            var bytes = ser.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
            return new EncodedState(Base64.getEncoder().encodeToString(bytes), stateSerializer.contentType(), StateEncoding.PLAIN_TEXT_UTF8.redisValue);
        }

        var bytes = stateSerializer.dataToBytes(data);
        return new EncodedState(Base64.getEncoder().encodeToString(bytes), stateSerializer.contentType(), StateEncoding.SERIALIZER_BYTES.redisValue);
    }

    private Map<String, Object> decodeState(String statePayload,
                                            String stateContentType,
                                            String stateEncodingValue) throws IOException, ClassNotFoundException {

        if (stateSerializer == null) {
            return decodeLegacyJsonState(statePayload);
        }

        String resolvedContentType = stateContentType;

        if (resolvedContentType == null || resolvedContentType.isBlank()) {
            // Backward compatibility with RedisSaver's original JSON state format.
            try {
                return decodeLegacyJsonState(statePayload);
            }
            catch (IOException ignored) {
                resolvedContentType = stateSerializer.contentType();
            }
        }

        if (!Objects.equals(resolvedContentType, stateSerializer.contentType())) {
            throw new IllegalStateException(
                    String.format("Content Type used for stored state '%s' is different from one '%s' used to deserialize it",
                            resolvedContentType,
                            stateSerializer.contentType()));
        }

        var bytes = Base64.getDecoder().decode(statePayload);
        var stateEncoding = StateEncoding.fromRedisValue(stateEncodingValue);

        if (stateEncoding.isPresent()) {
            return switch (stateEncoding.get()) {
                case SERIALIZER_BYTES -> stateSerializer.dataFromBytes(bytes);
                case PLAIN_TEXT_UTF8 -> decodePlainTextBytes(bytes);
            };
        }

        if (stateSerializer instanceof PlainTextStateSerializer<?>) {
            return decodePlainTextBytes(bytes);
        }

        return stateSerializer.dataFromBytes(bytes);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeLegacyJsonState(String statePayload) throws IOException {
        return objectMapper.readValue(statePayload, Map.class);
    }

    private Map<String, Object> decodePlainTextBytes(byte[] bytes) throws IOException {
        if (!(stateSerializer instanceof PlainTextStateSerializer<?> serializer)) {
            throw new IllegalStateException(
                    "Stored state was encoded as plain text, but configured stateSerializer is not a PlainTextStateSerializer");
        }
        return serializer.readDataFromString(new String(bytes, StandardCharsets.UTF_8));
    }

}
