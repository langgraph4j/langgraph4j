package org.bsc.langgraph4j.checkpoint;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.CPMap;
import com.hazelcast.map.IMap;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.CheckpointListSerializer;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Objects;

/**
 * <p>{@code HazelcastSaver} persists LangGraph4j workflow checkpoints in a Hazelcast
 * distributed map, so workflow state survives process restarts and can be shared
 * across the members of a Hazelcast cluster.</p>
 *
 * <p><b>Storage model.</b> All checkpoints of a single thread are stored as one map entry:
 * the key is the {@code threadId} and the value is the serialized, time-ordered list of that
 * thread's checkpoints (most recent first). Serialization reuses the framework's checkpoint-list
 * serializers: a {@link JacksonCheckpointListSerializer} (JSON, stored as the map value directly)
 * when a {@link JacksonStateSerializer} is configured, otherwise a {@link CheckpointListSerializer}
 * (binary, stored Base64-encoded).</p>
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
 *         .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
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
 *         .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
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

    private final CheckpointStore store;
    private final Serializer<LinkedList<Checkpoint>> checkpointsSerializer;

    private HazelcastSaver(Builder builder) {
        Objects.requireNonNull(builder.hazelcastInstance, "hazelcastInstance cannot be null");
        final var stateSerializer = Objects.requireNonNull(builder.stateSerializer, "stateSerializer cannot be null");
        final String mapName = (builder.mapName == null || builder.mapName.isBlank())
                ? DEFAULT_MAP_NAME : builder.mapName;

        // Reuse the framework's checkpoint-list serializers:
        // JSON when the state serializer is Jackson-based, binary otherwise.
        this.checkpointsSerializer = (stateSerializer instanceof JacksonStateSerializer<? extends AgentState> jsonStateSerializer)
                ? new JacksonCheckpointListSerializer(jsonStateSerializer)
                : new CheckpointListSerializer(stateSerializer);

        // CPMap is an Enterprise feature: its API is in the CE jar but it throws at runtime
        // unless a Hazelcast Enterprise license is present and the CP Subsystem is enabled.
        this.store = switch (builder.mapType) {
            case CP_MAP -> new CPMapStore(builder.hazelcastInstance.getCPSubsystem().getMap(mapName));
            case I_MAP -> new IMapStore(builder.hazelcastInstance.getMap(mapName));
        };
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
    // Encoding / decoding (whole checkpoint list <-> String map value)
    // -------------------------------------------------------------------------

    private String encode(LinkedList<Checkpoint> checkpoints) throws IOException {
        if (checkpointsSerializer instanceof JacksonCheckpointListSerializer jsonSerializer) {
            return jsonSerializer.writeDataAsString(checkpoints);
        } else {
            return Base64.getEncoder().encodeToString(checkpointsSerializer.objectToBytes(checkpoints));
        }
    }

    private LinkedList<Checkpoint> decode(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedList<>();
        }
        try {
            if (checkpointsSerializer instanceof JacksonCheckpointListSerializer jsonSerializer) {
                return jsonSerializer.readDataFromString(value);
            } else {
                return checkpointsSerializer.bytesToObject(Base64.getDecoder().decode(value));
            }
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Failed to decode stored checkpoints. A Hazelcast map entry must be read with the same "
                            + "StateSerializer kind it was written with (JSON vs. binary); verify the saver "
                            + "uses a matching serializer.", e);
        }
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
     * A {@link HazelcastInstance} and a {@link StateSerializer} are required; the instance may
     * represent an embedded member or a client.
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
         * Sets the state serializer used to encode/decode the checkpoints. Required.
         * <p>
         * A {@link JacksonStateSerializer} stores checkpoints as JSON; any other
         * {@link StateSerializer} stores them as Base64-encoded binary.
         *
         * @param stateSerializer the state serializer; must not be {@code null}
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
         * @throws NullPointerException if {@code hazelcastInstance} or {@code stateSerializer} was not set
         */
        public HazelcastSaver build() {
            return new HazelcastSaver(this);
        }
    }
}
