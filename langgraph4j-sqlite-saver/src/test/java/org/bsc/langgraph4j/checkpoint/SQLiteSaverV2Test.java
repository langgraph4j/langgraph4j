package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SQLiteSaverV2Test extends AbstractCheckpointSaverTest {

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

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return buildSQLiteSaverWithExistingDS()
                .stateSerializer(stateSerializer)
                .createTables(true)
                .build();
    }


    @Test
    void testLoadCommandsFromResource() throws Exception {

        var sqlCommandResource = new SqlResource.Commands("db/v2.0__commands.sql");

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
