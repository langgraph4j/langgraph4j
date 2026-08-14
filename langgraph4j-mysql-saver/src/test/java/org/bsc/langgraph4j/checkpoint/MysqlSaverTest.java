package org.bsc.langgraph4j.checkpoint;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.LG4JTestUtil;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;

public class MysqlSaverTest extends AbstractCheckpointSaverTest  {

    protected static final String MYSQL_IMAGE_NAME = "mysql:8.0";
    protected static MysqlDataSource DATA_SOURCE;

    protected static MySQLContainer<?> mysqlContainer;

    @BeforeAll
    public static void setup() {
        try {
            DATA_SOURCE = new MysqlDataSource();
            String urlFromEnv = System.getenv("MYSQL_JDBC_URL");

            if (urlFromEnv == null) {
                @SuppressWarnings("resource")
                MySQLContainer<?> container = new MySQLContainer<>(MYSQL_IMAGE_NAME)
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpwd");
                container.start();
                mysqlContainer = container;

                DATA_SOURCE.setURL(mysqlContainer.getJdbcUrl());
                DATA_SOURCE.setUser(mysqlContainer.getUsername());
                DATA_SOURCE.setPassword(mysqlContainer.getPassword());

            } else {

                DATA_SOURCE.setURL(urlFromEnv);
                DATA_SOURCE.setUser(System.getenv("MYSQL_JDBC_USER"));
                DATA_SOURCE.setPassword(System.getenv("MYSQL_JDBC_PASSWORD"));
            }

        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

    }

    @AfterAll
    public static void tearDown() {
        if (mysqlContainer != null) {
            mysqlContainer.close();
        }
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return MysqlSaver.builder()
                .dataSource(DATA_SOURCE)
                .stateSerializer(stateSerializer)
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();
    }

}
