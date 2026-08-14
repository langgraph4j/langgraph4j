package org.bsc.langgraph4j.checkpoint;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.datasource.OracleDataSource;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.testcontainers.oracle.OracleContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class OracleSaverTest extends AbstractCheckpointSaverTest {

    protected static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23.7-slim-faststart";
    protected static OracleDataSource DATA_SOURCE;
    protected static OracleDataSource SYSDBA_DATA_SOURCE;

    protected static OracleContainer oracleContainer;

    @BeforeAll
    public static void setup() throws IOException {
        try {
            DATA_SOURCE = new oracle.jdbc.datasource.impl.OracleDataSource();
            SYSDBA_DATA_SOURCE = new oracle.jdbc.datasource.impl.OracleDataSource();
            String urlFromEnv = System.getenv("ORACLE_JDBC_URL");

            if (urlFromEnv == null) {
                // The Ryuk component is relied upon to stop this container.
                oracleContainer = new OracleContainer(ORACLE_IMAGE_NAME)
                        .withStartupTimeout(Duration.ofSeconds(600))
                        .withConnectTimeoutSeconds(600)
                        .withDatabaseName("pdb1")
                        .withUsername("testuser")
                        .withPassword("testpwd");
                oracleContainer.start();

                initDataSource(
                        DATA_SOURCE,
                        oracleContainer.getJdbcUrl(),
                        oracleContainer.getUsername(),
                        oracleContainer.getPassword());
                initDataSource(SYSDBA_DATA_SOURCE,
                        oracleContainer.getJdbcUrl(),
                        "sys",
                        oracleContainer.getPassword());

            } else {
                initDataSource(
                        DATA_SOURCE,
                        urlFromEnv,
                        System.getenv("ORACLE_JDBC_USER"),
                        System.getenv("ORACLE_JDBC_PASSWORD"));
                initDataSource(
                        SYSDBA_DATA_SOURCE,
                        urlFromEnv,
                        System.getenv("ORACLE_JDBC_USER"),
                        System.getenv("ORACLE_JDBC_PASSWORD"));
            }
            SYSDBA_DATA_SOURCE.setConnectionProperty(OracleConnection.CONNECTION_PROPERTY_INTERNAL_LOGON, "SYSDBA");

        } catch (SQLException sqlException) {
            throw new AssertionError(sqlException);
        }

    }

    @AfterAll
    public static void tearDown() {
        if (oracleContainer != null) {
            oracleContainer.close();
        }
    }

    static void initDataSource(OracleDataSource dataSource, String url, String username, String password)
            throws SQLException {
        dataSource.setURL(url + "?oracle.jdbc.provider.json=jackson-json-provider");
        dataSource.setUser(username);
        dataSource.setPassword(password);
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return OracleSaver.builder()
                .dataSource(DATA_SOURCE)
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .stateSerializer(stateSerializer)
                .build();
    }


}
