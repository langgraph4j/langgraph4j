package org.bsc.langgraph4j.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.CPMap;
import com.hazelcast.map.IMap;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * <p>{@code HazelcastSaver} persists LangGraph4j workflow checkpoints in a Hazelcast
 * distributed map, so workflow state survives process restarts and can be shared
 * across the members of a Hazelcast cluster.</p>
 *
 * <p><b>Storage model.</b> All checkpoints of a single thread are stored as one map entry:
 * the key is the {@code threadId} and the value is the JSON-encoded, time-ordered list of
 * that thread's checkpoints (most recent first). This mirrors the in-memory model used by
 * {@link AbstractCheckpointSaver}, which hands this saver the full checkpoint list to persist.</p>
 *
 * <p><b>Write amplification.</b> Because a thread's checkpoints live in a single value, each
 * appended checkpoint re-serializes and rewrites the entire list for that thread (an {@code O(n)}
 * write for the {@code n}-th checkpoint). This is fine for typical workflows (tens of checkpoints,
 * modest state); for long-lived threads with large state, prune history (release threads you no
 * longer need) and mind {@link CPMap} per-map size limits.</p>
 *
 * <p><b>Map type (CE vs. EE).</b> Two map types are supported, selected with
 * {@link Builder#mapType(MapType)}:</p>
 * <ul>
 *   <li><b>{@link IMap} (default, Community Edition).</b> AP, backup-replicated. Survives the
 *       loss of one member with the default backup count, but is not linearizable. Runs on the
 *       free {@code com.hazelcast:hazelcast} jar.</li>
 *   <li><b>{@link CPMap} (Enterprise).</b> Linearizable, Raft-backed (CP Subsystem) &mdash; an
 *       acknowledged checkpoint is never lost while a CP majority is available, which is the
 *       stronger guarantee you usually want for checkpointing. Requires the
 *       {@code com.hazelcast:hazelcast-enterprise} jar and an Enterprise license at runtime.</li>
 * </ul>
 *
 * <p><b>Topology.</b> The saver is topology-agnostic: it works with any {@link HazelcastInstance},
 * whether an embedded cluster member ({@code Hazelcast.newHazelcastInstance(...)}) or a thin client
 * connected to a remote cluster ({@code HazelcastClient.newHazelcastClient(...)}). The caller owns
 * the lifecycle of the supplied instance.</p>
 *
 * <p><b>Usage (embedded member, IMap / CE):</b></p>
 * <pre>{@code
 * HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());
 *
 * var saver = HazelcastSaver.builder()
 *         .hazelcastInstance(hz)
 *         .build();
 * }</pre>
 *
 * <p><b>Usage (client to remote cluster, CPMap / EE):</b></p>
 * <pre>{@code
 * ClientConfig cfg = new ClientConfig();
 * cfg.getNetworkConfig().addAddress("hazelcast-host:5701");
 * HazelcastInstance hz = HazelcastClient.newHazelcastClient(cfg);
 *
 * var saver = HazelcastSaver.builder()
 *         .hazelcastInstance(hz)
 *         .mapType(HazelcastSaver.MapType.CP_MAP)   // linearizable CP map (Enterprise)
 *         .mapName("agentCheckpoints")
 *         .build();
 * }</pre>
 */
public class HazelcastSaver extends AbstractCheckpointSaver {

    /** Default name of the Hazelcast map holding the checkpoints. */
    public static final String DEFAULT_MAP_NAME = "langgraph4j-checkpoints";

    /**
     * The Hazelcast distributed map type used to store the checkpoints.
     */
    public enum MapType {
        /**
         * {@link IMap}: AP, backup-replicated. Survives the loss of one member with the default
         * backup count, but is not linearizable. Available on the open-source Community Edition.
         */
        I_MAP,
        /**
         * {@link CPMap}: linearizable, Raft-backed (CP Subsystem). An acknowledged checkpoint is
         * never lost while a CP majority is available. Requires Hazelcast Enterprise (license + an
         * enabled CP Subsystem) at runtime.
         */
        CP_MAP
    }

    private static final String SCHEMA_VERSION = "1";

    private final CheckpointStore store;
    private final ObjectMapper objectMapper;
    private final StateSerializer<? extends AgentState> stateSerializer;

    private HazelcastSaver(Builder builder) {
        Objects.requireNonNull(builder.hazelcastInstance, "hazelcastInstance cannot be null");
        final String mapName = (builder.mapName == null || builder.mapName.isBlank())
                ? DEFAULT_MAP_NAME : builder.mapName;

        // CPMap is an Enterprise feature: its API is in the CE jar but it throws at runtime
        // unless a Hazelcast Enterprise license is present and the CP Subsystem is enabled.
        this.store = switch (builder.mapType) {
            case CP_MAP -> new CPMapStore(builder.hazelcastInstance.getCPSubsystem().getMap(mapName));
            case I_MAP -> new IMapStore(builder.hazelcastInstance.getMap(mapName));
        };

        this.objectMapper = new ObjectMapper();
        this.stateSerializer = builder.stateSerializer;
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // AbstractCheckpointSaver
    // -------------------------------------------------------------------------

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        return decode(store.get(threadId(config)));
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        store.put(threadId(config), encode(checkpoints));
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        store.put(threadId(config), encode(checkpoints));
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final String threadId = threadId(config);
        store.remove(threadId);
        return new Tag(threadId, checkpoints);
    }

    // -------------------------------------------------------------------------
    // Encoding / decoding
    // -------------------------------------------------------------------------

    private enum StateEncoding {
        SERIALIZER_BYTES("serializer-bytes"),
        PLAIN_TEXT_UTF8("plain-text-utf8");

        private final String value;

        StateEncoding(String value) {
            this.value = value;
        }

        static Optional<StateEncoding> fromValue(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            for (StateEncoding encoding : values()) {
                if (encoding.value.equals(value)) {
                    return Optional.of(encoding);
                }
            }
            return Optional.empty();
        }
    }

    private record EncodedState(String payload, String contentType, String encoding) {}

    private String encode(LinkedList<Checkpoint> checkpoints) throws IOException {
        final List<Map<String, Object>> encoded = new ArrayList<>(checkpoints.size());
        for (Checkpoint checkpoint : checkpoints) {
            final EncodedState state = encodeState(checkpoint.getState());
            final Map<String, Object> entry = getObjectMap(checkpoint, state);
            encoded.add(entry);
        }

        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("checkpoints", encoded);
        return objectMapper.writeValueAsString(root);
    }

    private static Map<String, Object> getObjectMap(Checkpoint checkpoint, EncodedState state) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", checkpoint.getId());
        entry.put("nodeId", checkpoint.getNodeId());
        entry.put("nextNodeId", checkpoint.getNextNodeId());
        entry.put("state", state.payload());
        if (state.contentType() != null) {
            entry.put("contentType", state.contentType());
        }
        if (state.encoding() != null) {
            entry.put("encoding", state.encoding());
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    private LinkedList<Checkpoint> decode(String value) throws IOException, ClassNotFoundException {
        final LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        if (value == null || value.isBlank()) {
            return checkpoints;
        }

        final Map<String, Object> root = objectMapper.readValue(value, Map.class);
        final List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("checkpoints");
        if (entries == null) {
            return checkpoints;
        }

        for (Map<String, Object> entry : entries) {
            final String payload = (String) entry.get("state");
            final String contentType = (String) entry.get("contentType");
            final String encoding = (String) entry.get("encoding");
            final Map<String, Object> state = decodeState(payload, contentType, encoding);

            checkpoints.add(Checkpoint.builder()
                    .id((String) entry.get("id"))
                    .nodeId((String) entry.get("nodeId"))
                    .nextNodeId((String) entry.get("nextNodeId"))
                    .state(state)
                    .build());
        }
        return checkpoints;
    }

    private EncodedState encodeState(Map<String, Object> data) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");

        if (stateSerializer == null) {
            return new EncodedState(objectMapper.writeValueAsString(data), null, null);
        }

        if (stateSerializer instanceof PlainTextStateSerializer<?> serializer) {
            final var bytes = serializer.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
            return new EncodedState(Base64.getEncoder().encodeToString(bytes),
                    stateSerializer.contentType(), StateEncoding.PLAIN_TEXT_UTF8.value);
        }

        final var bytes = stateSerializer.dataToBytes(data);
        return new EncodedState(Base64.getEncoder().encodeToString(bytes),
                stateSerializer.contentType(), StateEncoding.SERIALIZER_BYTES.value);
    }

    private Map<String, Object> decodeState(String payload, String contentType, String encodingValue)
            throws IOException, ClassNotFoundException {

        if (stateSerializer == null) {
            return decodeJsonState(payload);
        }

        if (contentType != null && !Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(String.format(
                    "Content Type used for stored state '%s' is different from one '%s' used to deserialize it",
                    contentType, stateSerializer.contentType()));
        }

        final var bytes = Base64.getDecoder().decode(payload);
        final var encoding = StateEncoding.fromValue(encodingValue);

        if (encoding.isPresent()) {
            return switch (encoding.get()) {
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
    private Map<String, Object> decodeJsonState(String payload) throws IOException {
        return objectMapper.readValue(payload, Map.class);
    }

    private Map<String, Object> decodePlainTextBytes(byte[] bytes) throws IOException {
        if (!(stateSerializer instanceof PlainTextStateSerializer<?> serializer)) {
            throw new IllegalStateException(
                    "Stored state was encoded as plain text, but configured stateSerializer is not a PlainTextStateSerializer");
        }
        return serializer.readDataFromString(new String(bytes, StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Backing-store adapter (IMap and CPMap share no common super-interface)
    // -------------------------------------------------------------------------

    private interface CheckpointStore {
        String get(String key);

        void put(String key, String value);

        void remove(String key);
    }

    private record IMapStore(IMap<String, String> map) implements CheckpointStore {
        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String value) {
            map.set(key, value);
        }

        @Override
        public void remove(String key) {
            map.delete(key);
        }
    }

    private record CPMapStore(CPMap<String, String> map) implements CheckpointStore {
        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String value) {
            map.set(key, value);
        }

        @Override
        public void remove(String key) {
            map.delete(key);
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Builder for {@link HazelcastSaver}.
     * <p>
     * A {@link HazelcastInstance} is required; it may represent an embedded member or a client.
     */
    public static class Builder {
        private HazelcastInstance hazelcastInstance;
        private String mapName = DEFAULT_MAP_NAME;
        private MapType mapType = MapType.I_MAP;
        private StateSerializer<? extends AgentState> stateSerializer;

        /**
         * Sets the {@link HazelcastInstance} used to obtain the backing map. Required.
         * <p>
         * Pass either an embedded member ({@code Hazelcast.newHazelcastInstance(...)}) or a thin
         * client ({@code HazelcastClient.newHazelcastClient(...)}); the saver does not distinguish.
         *
         * @param hazelcastInstance the Hazelcast instance; must not be {@code null}
         * @return this builder
         */
        public Builder hazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        /**
         * Sets the name of the Hazelcast map that holds the checkpoints.
         *
         * @param mapName the map name (default {@link #DEFAULT_MAP_NAME})
         * @return this builder
         */
        public Builder mapName(String mapName) {
            this.mapName = mapName;
            return this;
        }

        /**
         * Selects the Hazelcast map type. Defaults to {@link MapType#I_MAP}.
         * <p>
         * {@link MapType#I_MAP} uses an {@link IMap} (AP, available on the open-source Community
         * Edition). {@link MapType#CP_MAP} uses a linearizable {@link CPMap} from the CP Subsystem,
         * which requires Hazelcast Enterprise (license + CP Subsystem enabled) at runtime.
         *
         * @param mapType the Hazelcast map type; must not be {@code null}
         * @return this builder
         */
        public Builder mapType(MapType mapType) {
            this.mapType = Objects.requireNonNull(mapType, "mapType cannot be null");
            return this;
        }

        /**
         * Sets the state serializer used to encode/decode the {@code state} of each checkpoint.
         * <p>
         * When unset, the state map is stored as plain JSON via Jackson.
         *
         * @param stateSerializer the state serializer (optional)
         * @return this builder
         */
        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Builds the {@link HazelcastSaver}.
         *
         * @return a new {@link HazelcastSaver}
         * @throws NullPointerException if {@code hazelcastInstance} was not set
         */
        public HazelcastSaver build() {
            return new HazelcastSaver(this);
        }
    }
}
