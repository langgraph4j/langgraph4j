package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.CockroachContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CockroachDBSaverTest {

    static class MyJacksonStateSerializer extends JacksonStateSerializer<AgentState> {

        public MyJacksonStateSerializer(AgentStateFactory<AgentState> stateFactory) {
            super(stateFactory);
        }
    }

    public enum StateSerializerEnum {
        BINARY(new ObjectStreamStateSerializer<>(AgentState::new)),
        JSON(new MyJacksonStateSerializer(AgentState::new));

        final StateSerializer<AgentState> stateSerializer;

        StateSerializerEnum(StateSerializer<AgentState> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CockroachDBSaverTest.class);

    static CockroachContainer cockroach = new CockroachContainer("cockroachdb/cockroach:latest-v25.2");

    @BeforeAll
    public static void init() throws IOException {
        try (var is = CockroachDBSaverTest.class.getResourceAsStream("/logging.properties")) {
            if (is != null) LogManager.getLogManager().readConfiguration(is);
        }
        cockroach.start();
    }

    @AfterAll
    public static void shutdown() {
        cockroach.stop();
    }

    CockroachDBSaver.Builder buildSaver() throws SQLException {
        return CockroachDBSaver.builder()
                .host(cockroach.getHost())
                .port(cockroach.getMappedPort(26257))
                .user(cockroach.getUsername())
                .password(cockroach.getPassword())
                .database(cockroach.getDatabaseName());
    }

    CockroachDBSaver.Builder buildSaverWithExistedDatasource() throws SQLException {
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(cockroach.getDatabaseName());
        ds.setUser(cockroach.getUsername());
        ds.setPassword(cockroach.getPassword());
        ds.setPortNumbers(new int[] {cockroach.getMappedPort(26257)});
        ds.setServerNames(new String[] {cockroach.getHost()});

        return CockroachDBSaver.builder().datasource(ds);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithReleasedThread(StateSerializerEnum param) throws Exception {

        var saver = buildSaver()
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        NodeAction<AgentState> agent_1 = state -> {
            log.info("agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke(inputs, runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertTrue(history.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithNotReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSaverWithExistedDatasource()
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        NodeAction<AgentState> agent_1 = state -> {
            log.info("agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke(inputs, runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        var lastSnapshot = workflow.lastStateOf(runnableConfig);

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());

        final var updatedConfig = workflow.updateState(lastSnapshot.get().config(), Map.of("update", "update test"));

        var updatedSnapshot = workflow.stateOf(updatedConfig);
        assertTrue(updatedSnapshot.isPresent());
        assertEquals("agent_1", updatedSnapshot.get().node());
        assertTrue(updatedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", updatedSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        // Reload from CockroachDB to confirm persistence
        saver = buildSaver()
                .stateSerializer(param.stateSerializer)
                .build();

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        runnableConfig = RunnableConfig.builder().build();
        workflow = graph.compile(compileConfig);

        history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        lastSnapshot = workflow.stateOf(updatedConfig);

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());
        assertTrue(lastSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", lastSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        saver.release(runnableConfig);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testRepeatedTableCreation(StateSerializerEnum param) throws SQLException {

        buildSaver()
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();

        buildSaver()
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();
    }
}
