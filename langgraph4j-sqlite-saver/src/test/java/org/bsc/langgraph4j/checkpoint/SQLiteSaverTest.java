package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

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
}

