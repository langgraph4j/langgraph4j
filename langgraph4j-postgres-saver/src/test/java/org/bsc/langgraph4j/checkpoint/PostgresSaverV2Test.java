package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.LogManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PostgresSaverV2Test extends AbstractCheckpointSaverTest {

    private static final String DATABASE_NAME = "lg4j-store";

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName(DATABASE_NAME)
                    .waitingFor(new CustomPostgreSQLWaitStrategy());

    @BeforeAll
    public static void init() throws IOException {
        try (var is = PostgresSaverV2Test.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        }

        postgres.start();
    }

    @AfterAll
    public static void shutdown() {
        postgres.stop();
    }

    PostgresSaverV2.Builder buildPostgresSaverWithExistedDatasource() throws SQLException {
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(DATABASE_NAME);
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setPortNumbers(new int[]{postgres.getFirstMappedPort()});
        ds.setServerNames(new String[]{postgres.getHost()});
        log.info("ds.url: '{}'",ds.getUrl());
        return PostgresSaverV2.builder()
                .datasource(ds);
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return buildPostgresSaverWithExistedDatasource()
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
                DROP TABLE IF EXISTS LG4JCheckpoint CASCADE;
                DROP TABLE IF EXISTS LG4JThread CASCADE;
                DROP TABLE IF EXISTS LG4JThreadTag CASCADE;
                
                """, cmd);

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
                    TRUE,
                    ?,
                    ?,
                    t.created_at
                FROM LG4JThread AS t
                WHERE t.thread_name = ?
                RETURNING thread_id;
                
                """, cmd);
    }
}
