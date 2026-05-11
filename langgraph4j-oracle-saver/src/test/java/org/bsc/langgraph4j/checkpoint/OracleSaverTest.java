package org.bsc.langgraph4j.checkpoint;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.datasource.OracleDataSource;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class OracleSaverTest {

    protected static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23.7-slim-faststart";
    protected static OracleDataSource DATA_SOURCE;
    protected static OracleDataSource SYSDBA_DATA_SOURCE;

    protected static OracleContainer oracleContainer;

    @BeforeAll
    public static void setup() throws IOException {
        try {
            DATA_SOURCE = new oracle.jdbc.datasource.impl.OracleDataSource();
            SYSDBA_DATA_SOURCE = new oracle.jdbc.datasource.impl.OracleDataSource();
            String urlFromEnv = System.getenv("ORACLE_JDBC_URL");

            if (urlFromEnv == null) {
                // The Ryuk component is relied upon to stop this container.
                oracleContainer = new OracleContainer(ORACLE_IMAGE_NAME)
                        .withStartupTimeout(Duration.ofSeconds(600))
                        .withConnectTimeoutSeconds(600)
                        .withDatabaseName("pdb1")
                        .withUsername("testuser")
                        .withPassword("testpwd");
                oracleContainer.start();

                initDataSource(
                        DATA_SOURCE,
                        oracleContainer.getJdbcUrl(),
                        oracleContainer.getUsername(),
                        oracleContainer.getPassword());
                initDataSource(SYSDBA_DATA_SOURCE, oracleContainer.getJdbcUrl(), "sys", oracleContainer.getPassword());

            } else {
                initDataSource(
                        DATA_SOURCE,
                        urlFromEnv,
                        System.getenv("ORACLE_JDBC_USER"),
                        System.getenv("ORACLE_JDBC_PASSWORD"));
                initDataSource(
                        SYSDBA_DATA_SOURCE,
                        urlFromEnv,
                        System.getenv("ORACLE_JDBC_USER"),
                        System.getenv("ORACLE_JDBC_PASSWORD"));
            }
            SYSDBA_DATA_SOURCE.setConnectionProperty(OracleConnection.CONNECTION_PROPERTY_INTERNAL_LOGON, "SYSDBA");

        } catch (SQLException sqlException) {
            throw new AssertionError(sqlException);
        }

    }

    @AfterAll
    public static void tearDown() {
        if (oracleContainer != null) {
            oracleContainer.close();
        }
    }

    static void initDataSource(OracleDataSource dataSource, String url, String username, String password)
            throws SQLException {
        dataSource.setURL(url + "?oracle.jdbc.provider.json=jackson-json-provider");
        dataSource.setUser(username);
        dataSource.setPassword(password);
    }

    @Test
    public void testCheckpointWithReleasedThread() throws Exception {

        var saver = OracleSaver.builder()
                .dataSource(DATA_SOURCE)
                .build();

        NodeAction<AgentState> agent_1 = state ->
             Map.of("agent_1:prop1", "agent_1:test");


        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true)
                .build();

        var runnableConfig = RunnableConfig.builder()
                .build();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke( GraphInput.args(inputs), runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertTrue(history.isEmpty());

    }

    @Test
    public void testCheckpointWithNotReleasedThread() throws Exception {
        var saver = OracleSaver.builder()
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .dataSource(DATA_SOURCE)
                .build();

        NodeAction<AgentState> agent_1 = state ->
            Map.of("agent_1:prop1", "agent_1:test");


        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.empty();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke( GraphInput.args(inputs), runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        var lastSnapshot = workflow.lastStateOf(runnableConfig);

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());

        // UPDATE STATE
        final var updatedConfig = workflow.updateState(lastSnapshot.get().config(), Map.of("update", "update test"));

        var updatedSnapshot = workflow.stateOf(updatedConfig);
        assertTrue(updatedSnapshot.isPresent());
        assertEquals("agent_1", updatedSnapshot.get().node());
        assertTrue(updatedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", updatedSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        // test checkpoints reloading from database
        saver = OracleSaver.builder()
                .dataSource(DATA_SOURCE)
                .build();

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        runnableConfig = RunnableConfig.empty();
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

}
