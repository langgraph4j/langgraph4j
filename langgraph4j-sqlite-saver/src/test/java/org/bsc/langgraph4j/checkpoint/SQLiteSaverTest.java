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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SQLiteSaverTest {

    @TempDir
    Path tempDir;

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

    SQLiteSaver.Builder buildSQLiteSaver(String databaseName) {
        return SQLiteSaver.builder()
                .databasePath(tempDir.resolve(databaseName).toString());
    }

    SQLiteSaver.Builder buildSQLiteSaverWithExistingDatasource(String databaseName) {
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve(databaseName));

        return SQLiteSaver.builder()
                .datasource(ds);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSQLiteSaver("released.db")
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        NodeAction<AgentState> agent1 = state -> Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        var result = workflow.invoke(Map.of("input", "test1"), runnableConfig);

        assertTrue(result.isPresent());
        assertTrue(workflow.getStateHistory(runnableConfig).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithNotReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSQLiteSaverWithExistingDatasource("not-released.db")
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        NodeAction<AgentState> agent1 = state -> Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        var result = workflow.invoke(Map.of("input", "test1"), runnableConfig);

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
        assertEquals(END, updatedSnapshot.get().next());

        saver = buildSQLiteSaver("not-released.db")
                .stateSerializer(param.stateSerializer)
                .build();

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        workflow = graph.compile(compileConfig);

        history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        updatedSnapshot = workflow.stateOf(updatedConfig);

        assertTrue(updatedSnapshot.isPresent());
        assertEquals("agent_1", updatedSnapshot.get().node());
        assertTrue(updatedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", updatedSnapshot.get().state().value("update").get());
        assertEquals(END, updatedSnapshot.get().next());

        saver.release(runnableConfig);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCreateTablesIsIdempotent(StateSerializerEnum param) throws Exception {
        buildSQLiteSaver("idempotent.db")
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();

        buildSQLiteSaver("idempotent.db")
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();
    }
}
