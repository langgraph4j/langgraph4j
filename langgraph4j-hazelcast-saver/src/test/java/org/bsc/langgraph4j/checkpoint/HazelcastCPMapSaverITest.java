package org.bsc.langgraph4j.checkpoint;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link HazelcastSaver} backed by a {@link com.hazelcast.cp.CPMap}.
 * <p>
 * {@code CPMap} is a <b>Hazelcast Enterprise</b> feature: it requires the
 * {@code com.hazelcast:hazelcast-enterprise} jar, an Enterprise license, and an enabled CP
 * Subsystem (a 3-member CP group is formed in-JVM here, mirroring the production topology).
 * Because of those requirements this test:
 * <ul>
 *   <li>is named {@code *ITest} so the module's surefire config excludes it from {@code mvn test} / CI;</li>
 *   <li>{@linkplain org.junit.jupiter.api.Assumptions#assumeTrue(boolean) skips} when no license is
 *       supplied via the {@code HZ_LICENSEKEY} env var or {@code hazelcast.enterprise.license.key}
 *       system property.</li>
 * </ul>
 * To run it: provide a license and the Enterprise jar (and the Hazelcast Enterprise Maven repo),
 * then execute it explicitly, e.g. {@code mvn -pl langgraph4j-hazelcast-saver test -Dtest=HazelcastCPMapSaverITest}.
 */
public class HazelcastCPMapSaverITest {

    private static final int CP_MEMBER_COUNT = 3;
    private static final List<String> MEMBERS =
            List.of("127.0.0.1:5701", "127.0.0.1:5702", "127.0.0.1:5703");

    private static final List<HazelcastInstance> cluster = new ArrayList<>();

    private static String licenseKey() {
        String key = System.getProperty("hazelcast.enterprise.license.key");
        if (key == null || key.isBlank()) {
            key = System.getenv("HZ_LICENSEKEY");
        }
        return key;
    }

    @BeforeAll
    public static void startCluster() throws Exception {
        final String license = licenseKey();
        assumeTrue(license != null && !license.isBlank(),
                "Hazelcast Enterprise license not provided (set HZ_LICENSEKEY); skipping CPMap test");

        System.setProperty("java.net.preferIPv4Stack", "true");
        final String clusterName = "langgraph4j-cpmap-" + UUID.randomUUID();

        for (int i = 0; i < CP_MEMBER_COUNT; i++) {
            Config config = new Config();
            config.setClusterName(clusterName);
            config.setLicenseKey(license);
            config.getCPSubsystemConfig().setCPMemberCount(CP_MEMBER_COUNT);

            NetworkConfig network = config.getNetworkConfig();
            network.setPort(5701 + i).setPortAutoIncrement(false);
            JoinConfig join = network.getJoin();
            join.getMulticastConfig().setEnabled(false);
            join.getTcpIpConfig().setEnabled(true).setMembers(MEMBERS);

            cluster.add(Hazelcast.newHazelcastInstance(config));
        }

        cluster.get(0).getCPSubsystem()
                .getCPSubsystemManagementService()
                .awaitUntilDiscoveryCompleted(120, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void stopCluster() {
        cluster.forEach(HazelcastInstance::shutdown);
        cluster.clear();
    }

    @Test
    public void testCheckpointPersistedAndReloadedViaCPMap() throws Exception {
        final var member = cluster.get(0);
        final var mapName = "cp-checkpoints-" + UUID.randomUUID();

        var saver = HazelcastSaver.builder()
                .hazelcastInstance(member)
                .mapType(HazelcastSaver.MapType.CP_MAP)
                .mapName(mapName)
                .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
                .build();

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

        var result = workflow.invoke(Map.of("input", "test1"), runnableConfig);
        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        // Reload from the CPMap with a fresh saver over the same map
        var saver2 = HazelcastSaver.builder()
                .hazelcastInstance(member)
                .mapType(HazelcastSaver.MapType.CP_MAP)
                .mapName(mapName)
                .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
                .build();

        var workflow2 = graph.compile(CompileConfig.builder()
                .checkpointSaver(saver2)
                .releaseThread(false)
                .build());

        var history2 = workflow2.getStateHistory(RunnableConfig.builder().build());
        assertFalse(history2.isEmpty());
        assertEquals(history.size(), history2.size());

        saver2.release(RunnableConfig.builder().build());

        var historyAfterRelease = workflow2.getStateHistory(RunnableConfig.builder().build());
        assertTrue(historyAfterRelease.isEmpty());
    }
}
