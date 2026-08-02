package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeAction;
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
import java.util.Optional;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

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

    private AsyncNodeAction<AgentState> makeNode( String node ) {
        return state ->
            completedFuture( Map.of( "%s:attr".formatted(node), "%s:value".formatted(node)));

    }
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSQLiteSaver("released.db")
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        final var agent1 = makeNode("agent_1");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", agent1)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

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

        final var agent1 = makeNode("agent_1");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", agent1)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile(compileConfig);

        var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

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
    public void testCheckpointWithInterruption(StateSerializerEnum param) throws Exception {

        final var agent1 = makeNode( "agent_1" );
        final var agent2 = makeNode( "agent_2" );

        final var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", agent1)
                .addNode("agent_2", agent2)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", END);

        var compileConfig = CompileConfig.builder()
                .interruptBefore("agent_2")
                .build();

        final var threadId = switch( param ){
            case JSON -> "json-thread";
            case BINARY -> "binary-thread";
        };

        var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        { // STEP 1
            var saver = buildSQLiteSaverWithExistingDatasource("interruption.db")
                    .createTables(true)
                    .stateSerializer(param.stateSerializer)
                    .build();

            var workflow = graph.compile(CompileConfig.builder(compileConfig)
                                            .checkpointSaver(saver)
                                            .build());

            workflow.stream(GraphInput.noArgs(), runnableConfig).toCompletableFuture()
                    .thenApply(GraphResult::from)
                    .thenAccept(result -> {
                        assertTrue(result.isInterruptionMetadata());

                        final var im = result.asInterruptionMetadata();

                        assertEquals(1, im.state().data().size());

                        Optional<String> value = im.state().value("agent_1:attr");
                        assertTrue(value.isPresent());
                        assertEquals("agent_1:value", value.get());
                    })
                    .join();
        }

        { // STEP 2

            var saver = buildSQLiteSaverWithExistingDatasource("interruption.db")
                    .stateSerializer(param.stateSerializer)
                    .build();

            var workflow = graph.compile(CompileConfig.builder(compileConfig)
                    .checkpointSaver(saver)
                    .build());

            try {
                workflow.stream(GraphInput.resume(), runnableConfig).toCompletableFuture()
                        .thenApply(GraphResult::from)
                        .thenAccept(result -> {
                            assertTrue(result.isCheckpointSaverTag());

                            final var im = result.asCheckpointSaverTag()
                                    .checkpoints()
                                    .stream()
                                    .findFirst();
                            assertTrue(im.isPresent());

                            final var state = new AgentState(im.get().getState());
                            assertEquals(2, state.data().size());

                            Optional<String> value = state.value("agent_1:attr");
                            assertTrue(value.isPresent());
                            assertEquals("agent_1:value", value.get());
                            value = state.value("agent_2:attr");
                            assertTrue(value.isPresent());
                            assertEquals("agent_2:value", value.get());
                        })
                        .join();
            }
            catch (Exception e) {
                saver.release(runnableConfig);
            }
        }
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
