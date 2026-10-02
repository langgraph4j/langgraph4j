package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: loading checkpoints must return its connection. With a pooled DataSource a leaked
 * connection is never handed back, so the pool runs dry after a few graph steps.
 */
public class PostgresSaverConnectionLeakTest {

    private static final String DATABASE_NAME = "lg4j-store-leak";

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName(DATABASE_NAME)
                    .waitingFor(new CustomPostgreSQLWaitStrategy());

    @BeforeAll
    public static void init() {
        postgres.start();
    }

    @AfterAll
    public static void shutdown() {
        postgres.stop();
    }

    static PGSimpleDataSource dataSource() {
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(DATABASE_NAME);
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setPortNumbers(new int[]{postgres.getFirstMappedPort()});
        ds.setServerNames(new String[]{postgres.getHost()});
        return ds;
    }

    @Test
    void everyConnectionTheSaverBorrowsIsClosed() throws Exception {
        var ds = new TrackingDataSource(dataSource());
        BaseCheckpointSaver saver = PostgresSaverV2.builder()
                .datasource(ds)
                .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
                .createTables(true)
                .build();
        assertEquals(0, ds.openConnections(), "after build");

        var config = RunnableConfig.builder().threadId("connection-leak-test").build();

        saver.list(config);
        assertEquals(0, ds.openConnections(), "after list");

        saver.get(config);
        assertEquals(0, ds.openConnections(), "after get");

        var checkpoint = Checkpoint.builder()
                .state(Map.of("step", 1))
                .nodeId("node")
                .nextNodeId("next")
                .build();
        config = saver.put(config, checkpoint);
        assertEquals(0, ds.openConnections(), "after put");

        assertTrue(saver.get(config).isPresent());
        assertEquals(0, ds.openConnections(), "after get of a stored checkpoint");

        saver.release(config);
        assertEquals(0, ds.openConnections(), "after release");
    }
}
