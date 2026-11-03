package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Map;

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

        var sqlCommandResource = SqlResource.Commands.load("db/v2.0__commands.sql");

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

    @Test
    void everyConnectionTheSaverBorrowsIsClosed() throws Exception {
        var ds$ = new TrackingDataSource(ds);
        BaseCheckpointSaver saver = SQLiteSaverV2.builder()
                .datasource(ds$)
                .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
                .createTables(true)
                .build();
        assertEquals(0, ds$.openConnections(), "after build");

        var config = RunnableConfig.builder().threadId("connection-leak-test").build();

        saver.list(config);
        assertEquals(0, ds$.openConnections(), "after list");

        saver.get(config);
        assertEquals(0, ds$.openConnections(), "after get");

        var checkpoint = Checkpoint.builder()
                .state(Map.of("step", 1))
                .nodeId("node")
                .nextNodeId("next")
                .build();
        config = saver.put(config, checkpoint);
        assertEquals(0, ds$.openConnections(), "after put");

        assertTrue(saver.get(config).isPresent());
        assertEquals(0, ds$.openConnections(), "after get of a stored checkpoint");

        saver.release(config);
        assertEquals(0, ds$.openConnections(), "after release");
    }

}
