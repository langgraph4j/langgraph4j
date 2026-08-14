package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.CockroachContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.LogManager;

public class CockroachDBSaverTest extends AbstractCheckpointSaverTest {


    static CockroachContainer cockroach = new CockroachContainer("cockroachdb/cockroach:latest-v25.2");

    @BeforeAll
    public static void init() throws IOException {
        try (var is = CockroachDBSaverTest.class.getResourceAsStream("/logging.properties")) {
            if (is != null) LogManager.getLogManager().readConfiguration(is);
        }
        cockroach.start();
    }

    @AfterAll
    public static void shutdown() {
        cockroach.stop();
    }

    CockroachDBSaver.Builder buildSaver() throws SQLException {
        return CockroachDBSaver.builder()
                .host(cockroach.getHost())
                .port(cockroach.getMappedPort(26257))
                .user(cockroach.getUsername())
                .password(cockroach.getPassword())
                .database(cockroach.getDatabaseName());
    }

    CockroachDBSaver.Builder buildSaverWithExistedDatasource() throws SQLException {
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(cockroach.getDatabaseName());
        ds.setUser(cockroach.getUsername());
        ds.setPassword(cockroach.getPassword());
        ds.setPortNumbers(new int[] {cockroach.getMappedPort(26257)});
        ds.setServerNames(new String[] {cockroach.getHost()});

        return CockroachDBSaver.builder().datasource(ds);
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(cockroach.getDatabaseName());
        ds.setUser(cockroach.getUsername());
        ds.setPassword(cockroach.getPassword());
        ds.setPortNumbers(new int[] {cockroach.getMappedPort(26257)});
        ds.setServerNames(new String[] {cockroach.getHost()});

        return CockroachDBSaver.builder()
                .datasource(ds)
                .createTables(true)
                .stateSerializer(stateSerializer)
                .build();
    }


}
