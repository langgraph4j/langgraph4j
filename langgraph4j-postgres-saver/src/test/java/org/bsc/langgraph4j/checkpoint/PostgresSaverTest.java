package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.LogManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostgresSaverTest extends AbstractCheckpointSaverTest {


    private static final String DATABASE_NAME = "lg4j-store_v1";

    private static final String[] IMAGES = {
            "postgres:16-alpine",
            "pgvector/pgvector:pg16"
    };

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(IMAGES[1])
                    .withDatabaseName(DATABASE_NAME)
                    .waitingFor(new CustomPostgreSQLWaitStrategy());

    @BeforeAll
    public static void init() throws IOException {
        // initialize log
        try (var is = PostgresSaverTest.class.getResourceAsStream("/logging.properties")) {
            if (is != null) LogManager.getLogManager().readConfiguration(is);
        }

        // start postgres container
        postgres.start();

    }

    @AfterAll
    public static void shutdown() {
        postgres.stop();
    }

    PostgresSaver.Builder buildPostgresSaver() throws SQLException {
        return PostgresSaver.builder()
                //.host("localhost")
                .host(postgres.getHost())
                //.port(5432)
                .port(postgres.getFirstMappedPort())
                //.user("admin")
                .user(postgres.getUsername())
                //.password("bsorrentino")
                .password(postgres.getPassword())
                .database(DATABASE_NAME)
                ;
    }

    PostgresSaver.Builder buildPostgresSaverWithExistedDatasource() throws SQLException {
        // Simulate a existed datasource
        // Maybe a existed bean in your project
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(DATABASE_NAME);
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setPortNumbers(new int[]{postgres.getFirstMappedPort()});
        ds.setServerNames(new String[]{postgres.getHost()});

        return PostgresSaver.builder()
                .datasource(ds);
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return buildPostgresSaverWithExistedDatasource()
                .createTables(true)
                .stateSerializer(stateSerializer)
                .build();
    }


    @Test
    public void testBuilderSinglePropertyApplied() throws Exception {
        var saver = buildPostgresSaver()
                .property("ApplicationName", "lg4j-property-test")
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertEquals("lg4j-property-test", rs.getString(1));
        }
    }

    @Test
    public void testBuilderMultiplePropertiesApplied() throws Exception {
        var props = new Properties();
        props.setProperty("ApplicationName", "lg4j-props-test");
        props.setProperty("connectTimeout", "30");

        var saver = buildPostgresSaver()
                .properties(props)
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertEquals("lg4j-props-test", rs.getString(1));
        }
    }

    @Test
    public void testBuilderPropertyIgnoredWithExternalDatasource() throws Exception {
        // when datasource() is provided explicitly, property()/properties() have no effect
        var saver = buildPostgresSaverWithExistedDatasource()
                .property("ApplicationName", "should-not-apply")
                .createTables(false)
                .stateSerializer(StateSerializerEnum.BINARY.stateSerializer)
                .build();

        try (var conn = saver.datasource.getConnection();
             var rs = conn.createStatement().executeQuery("SELECT current_setting('application_name')")) {
            assertTrue(rs.next());
            assertNotEquals("should-not-apply", rs.getString(1));
        }
    }

}
