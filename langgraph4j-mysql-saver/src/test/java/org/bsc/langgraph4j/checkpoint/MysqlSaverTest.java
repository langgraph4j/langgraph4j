package org.bsc.langgraph4j.checkpoint;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

public class MysqlSaverTest {

    protected static final String MYSQL_IMAGE_NAME = "mysql:8.0";
    protected static MysqlDataSource DATA_SOURCE;

    protected static MySQLContainer<?> mysqlContainer;

    @BeforeAll
    public static void setup() {
        try {
            DATA_SOURCE = new MysqlDataSource();
            String urlFromEnv = System.getenv("MYSQL_JDBC_URL");

            if (urlFromEnv == null) {
                @SuppressWarnings("resource")
                MySQLContainer<?> container = new MySQLContainer<>(MYSQL_IMAGE_NAME)
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpwd");
                container.start();
                mysqlContainer = container;

                initDataSource(
                        DATA_SOURCE,
                        mysqlContainer.getJdbcUrl(),
                        mysqlContainer.getUsername(),
                        mysqlContainer.getPassword());

            } else {
                initDataSource(
                        DATA_SOURCE,
                        urlFromEnv,
                        System.getenv("MYSQL_JDBC_USER"),
                        System.getenv("MYSQL_JDBC_PASSWORD"));
            }

        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

    }

    @AfterAll
    public static void tearDown() {
        if (mysqlContainer != null) {
            mysqlContainer.close();
        }
    }

    static void initDataSource(MysqlDataSource dataSource, String url, String username, String password) {
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
    }

    @Test
    public void testCheckpointWithReleasedThread() throws Exception {

        var saver = MysqlSaver.builder()
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
        var saver = MysqlSaver.builder()
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
        saver = MysqlSaver.builder()
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
        // lastSnapshot = workflow.lastStateOf( runnableConfig );

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());
        assertTrue(lastSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", lastSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        saver.release(runnableConfig);

    }

}
