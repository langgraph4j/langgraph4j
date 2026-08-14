package org.bsc.langgraph4j.checkpoint;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HazelcastSaver} backed by an {@link com.hazelcast.map.IMap}.
 * <p>
 * A single embedded Hazelcast member is started with cluster join disabled, so the tests
 * run on the open-source Community Edition jar without Docker, a license, or network access.
 * The IMap path is topology-transparent, so an embedded member exercises the same code a
 * client would. The CPMap (Enterprise) path is covered separately by
 * {@code HazelcastCPMapSaverITest}.
 */
public class HazelcastSaverTest extends AbstractCheckpointSaverTest {

    static HazelcastInstance hazelcastInstance;

    @BeforeAll
    public static void setup() {
        Config config = new Config();
        config.setClusterName("langgraph4j-test-" + UUID.randomUUID());
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        assertTrue(hazelcastInstance.getLifecycleService().isRunning());
    }

    @AfterAll
    public static void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    /** Each saver gets its own map so tests are isolated from one another. */
    protected HazelcastSaver createSaver() {
        return createSaver("checkpoints-%s".formatted(UUID.randomUUID()), StateSerializerEnum.BINARY);
    }

    protected HazelcastSaver createSaver(String mapName, StateSerializerEnum param) {
        return HazelcastSaver.builder()
                .hazelcastInstance(hazelcastInstance)
                .mapName(mapName)
                .stateSerializer(param.stateSerializer)
                .build();
    }


    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {

        return HazelcastSaver.builder()
                .hazelcastInstance(hazelcastInstance)
                .mapName("checkpoints-%s".formatted( (threadId != null) ? threadId : UUID.randomUUID()))
                .stateSerializer(stateSerializer)
                .build();
    }


    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithStateSerializer(StateSerializerEnum param) throws Exception {
        var mapName = "checkpoints-" + UUID.randomUUID();
        var saver = createSaver(mapName, param);

        NodeAction<AgentState> agent_1 = state -> Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        workflow.invoke(GraphInput.args(Map.of("input", "test-serializer")), runnableConfig);

        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty());

        // Reload with a fresh saver using the same serializer and map
        var saver2 = createSaver(mapName, param);
        var workflow2 = graph.compile(CompileConfig.builder()
                .checkpointSaver(saver2)
                .releaseThread(false)
                .build());

        var history2 = workflow2.getStateHistory(runnableConfig);
        assertFalse(history2.isEmpty());
        assertEquals(history.size(), history2.size());

        // Assert the state VALUE survived the serialize -> Hazelcast -> deserialize round-trip
        var reloaded = workflow2.lastStateOf(runnableConfig);
        assertTrue(reloaded.isPresent());
        assertEquals("agent_1:test", reloaded.get().state().value("agent_1:prop1").orElse(null));

        saver2.release(runnableConfig);
    }
}
