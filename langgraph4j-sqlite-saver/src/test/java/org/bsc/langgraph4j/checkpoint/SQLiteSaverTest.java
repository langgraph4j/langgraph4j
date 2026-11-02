package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.*;

public class SQLiteSaverTest extends AbstractCheckpointSaverTest {


    @TempDir
    static Path tempDir;

    static SQLiteDataSource ds;

    @BeforeAll
    static void setUp() {
        log.info("tempDir: {}", tempDir);

        tempDir = Path.of("target");

        ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:".concat(tempDir.resolve("SQLiteSaverTest.db").toString()));
    }

    static SQLiteSaver.Builder buildSQLiteSaverWithExistingDS() {
        return SQLiteSaver.builder()
                .datasource(ds);
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return buildSQLiteSaverWithExistingDS()
                .stateSerializer(stateSerializer)
                .createTables(true)
                .build();
    }

    /**
     * The saved-at time must come from the storage, not from the clock of whoever
     * reads the checkpoint. The two are only distinguishable when the same rows are
     * loaded twice: a column that is read back yields the same value both times,
     * while a fallback to {@code Instant.now()} yields a different one on every load
     * and makes the order of the history depend on how fast it is read.
     */
    @Test
    void savedAtIsReadBackFromStorage() throws Exception {

        final var threadId = "thread-savedAtIsReadBackFromStorage";

        final var saver = buildCheckpointSaver( StateSerializerEnum.JSON.stateSerializer, threadId );

        final var graph = new StateGraph<>(State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                .addNode("agent_1", CustomNodeAction.of("agent_1"))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        final var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        final var workflow = graph.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

        final var first  = new ArrayList<>( saver.list(runnableConfig) );
        final var second = new ArrayList<>( saver.list(runnableConfig) );

        assertFalse(first.isEmpty());
        assertEquals(first.size(), second.size());

        for (int i = 0; i < first.size(); i++) {
            assertNotNull(first.get(i).savedAt(), "the saved_at column must be read back into the checkpoint");
            assertEquals(first.get(i).savedAt(), second.get(i).savedAt(),
                    "loading the same rows twice must not invent a new saved-at time");
        }

        for (int i = 0; i < first.size() - 1; i++) {
            assertFalse(first.get(i).savedAt().isBefore(first.get(i + 1).savedAt()),
                    "checkpoints are returned newest first");
        }
    }
}

