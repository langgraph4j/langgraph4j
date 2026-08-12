package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.utils.SqlResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

public class SQLiteSaverV2Test implements LG4JTestUtil, LG4JLoggable {

    static class MyJacksonStateSerializer extends JacksonStateSerializer<SimpleMState> {

        public MyJacksonStateSerializer(AgentStateFactory<SimpleMState> stateFactory) {
            super(stateFactory);
        }
    }

    public enum StateSerializerEnum {
        BINARY(new ObjectStreamStateSerializer<>(SimpleMState::new)),
        JSON(new MyJacksonStateSerializer(SimpleMState::new));

        final StateSerializer<SimpleMState> stateSerializer;

        StateSerializerEnum(StateSerializer<SimpleMState> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    @TempDir
    static Path tempDir;

    static SQLiteDataSource ds;

    @BeforeAll
    static void setUp() {
        log.info("tempDir: {}", tempDir);

        tempDir = Path.of( "target");

        ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:".concat(tempDir.resolve("SQLiteSaverV2Test.db").toString()));
    }

    static SQLiteSaverV2.Builder buildSQLiteSaverWithExistingDS() {
        return SQLiteSaverV2.builder()
                .datasource(ds);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSQLiteSaverWithExistingDS()
                .dropTablesFirst(true)
                .stateSerializer(param.stateSerializer)
                .build();

        final var agent1 = CustomAction.of("agent_1");

        var graph = new StateGraph<>(SimpleMState.SCHEMA, SimpleMState::new)
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
        final var threadId = "testCheckpointWithNotReleasedThread";

        var saver = buildSQLiteSaverWithExistingDS()
                .stateSerializer(param.stateSerializer)
                .createTables(true)
                .build();

        final var agent1 = CustomAction.of("agent_1");

        var graph = new StateGraph<>(SimpleMState.SCHEMA, param.stateSerializer)
                .addNode("agent_1", agent1)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        var workflow = graph.compile(compileConfig);

        try {
            var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

            assertTrue(result.isPresent());

            var history = workflow.getStateHistory(runnableConfig);

            assertFalse(history.isEmpty());
            assertEquals(2, history.size());

            var lastSnapshot = workflow.lastStateOf(runnableConfig);

            assertTrue(lastSnapshot.isPresent());
            assertEquals("agent_1", lastSnapshot.get().node());
            assertEquals(END, lastSnapshot.get().next());

            var updatedConfig = workflow.updateState(lastSnapshot.get().config(), Map.of("update", "update test"));

            var updatedSnapshot = workflow.stateOf(updatedConfig);
            assertTrue(updatedSnapshot.isPresent());
            assertEquals("agent_1", updatedSnapshot.get().node());
            assertTrue(updatedSnapshot.get().state().value("update").isPresent());
            assertEquals("update test", updatedSnapshot.get().state().value("update").get());
            assertEquals(END, updatedSnapshot.get().next());

            saver = buildSQLiteSaverWithExistingDS()
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
        catch (Exception e) {
            saver.releaseOnError(runnableConfig, e);
        }

    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCheckpointWithInterruption(StateSerializerEnum param) throws Exception {

        final var agent1 = CustomAction.of("agent_1");
        final var agent2 = CustomAction.of("agent_2");

        final var graph = new StateGraph<>(SimpleMState.SCHEMA, param.stateSerializer)
                .addNode("agent_1", agent1)
                .addNode("agent_2", agent2)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", END);

        var compileConfig = CompileConfig.builder()
                .interruptBefore("agent_2")
                .build();

        final var threadId = switch( param ){
            case JSON -> "json-thread-testCheckpointWithInterruption";
            case BINARY -> "binary-thread-testCheckpointWithInterruption";
        };

        var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        { // STEP 1
            var saver = buildSQLiteSaverWithExistingDS()
                    .createTables(true)
                    .stateSerializer(param.stateSerializer)
                    .build();

            var workflow = graph.compile(CompileConfig.builder(compileConfig)
                                            .checkpointSaver(saver)
                                            .build());

            try {
                workflow.stream(GraphInput.noArgs(), runnableConfig).toCompletableFuture()
                        .thenApply(GraphResult::from)
                        .thenAccept(result -> {
                            assertTrue(result.isInterruptionMetadata());

                            final InterruptionMetadata<SimpleMState> im = result.asInterruptionMetadata();

                            assertEquals(1, im.state().messages().size());

                            final var value = im.state().lastMessage();
                            assertTrue(value.isPresent());
                            assertEquals("agent_1", value.get());
                        })
                        .join();
            }
            catch (Exception e) {
                saver.releaseOnError(runnableConfig, e);
            }
        }

        { // STEP 2

            var saver = buildSQLiteSaverWithExistingDS()
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

                            final var state = new SimpleMState(im.get().getState());
                            assertEquals(2, state.messages().size());

                            Optional<String> value = state.lastMinus(1);
                            assertTrue(value.isPresent());
                            assertEquals("agent_1", value.get());
                            value = state.lastMessage();
                            assertTrue(value.isPresent());
                            assertEquals("agent_2", value.get());
                        })
                        .join();
            }
            catch (Exception e) {
                saver.releaseOnError(runnableConfig, e);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public void testCreateTablesIsIdempotent(StateSerializerEnum param) throws Exception {

        SQLiteSaverV2.builder()
                .databasePath(tempDir.resolve("idempotent.db").toString())
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();

        SQLiteSaverV2.builder()
                .databasePath(tempDir.resolve("idempotent.db").toString())
                .createTables(true)
                .stateSerializer(param.stateSerializer)
                .build();
    }

    @Test
    void testLoadCommandsFromResource() throws Exception {

        var sqlCommandResource = new SqlResource.Commands("db/v1.9__commands.sql");

        String cmd = sqlCommandResource.get("sqlDropTables");

        assertNotNull(cmd);
        assertEquals("""
                        DROP TABLE IF EXISTS LG4JCheckpoint;
                        DROP TABLE IF EXISTS LG4JThread;
                        DROP TABLE IF EXISTS LG4JThreadTag;

                        """,cmd);

        cmd = sqlCommandResource.get("sqlReleaseThread_insertTag");

        assertNotNull(cmd);
        assertEquals("""
INSERT INTO LG4JThreadTag (
thread_id,
thread_name,
released_version,
parent_thread_id,
is_released,
is_error,
message,
created_at
)
SELECT
t.thread_id,
t.thread_name,
COALESCE(
(
SELECT MAX(tag.released_version)
FROM LG4JThreadTag AS tag
WHERE tag.thread_name = t.thread_name
),
0
) + 1,
t.parent_thread_id,
1,
?,
?,
t.created_at
FROM LG4JThread AS t
WHERE t.thread_name = ?
RETURNING thread_id;

                """,cmd);

    }


}
