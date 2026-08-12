package org.bsc.langgraph4j.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.TryFunction;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public abstract class AbstractMysqlServer extends AbstractCheckpointSaver implements LG4JLoggable {
    // DDL statements
    private static final String CREATE_THREAD_TABLE = """
            CREATE TABLE IF NOT EXISTS LANGRAPH4J_THREAD (
               thread_id VARCHAR(36) PRIMARY KEY,
               thread_name VARCHAR(255),
               is_released BOOLEAN DEFAULT FALSE NOT NULL
            )""";

    private static final String INDEX_THREAD_TABLE = """
            CREATE UNIQUE INDEX IDX_LANGRAPH4J_THREAD_NAME_RELEASED
              ON LANGRAPH4J_THREAD(thread_name, is_released)
            """;

    private static final String CREATE_CHECKPOINT_TABLE = """
            CREATE TABLE IF NOT EXISTS LANGRAPH4J_CHECKPOINT (
               id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT UNIQUE KEY,
               checkpoint_id VARCHAR(36) PRIMARY KEY,
               thread_id VARCHAR(36) NOT NULL,
               node_id VARCHAR(255),
               next_node_id VARCHAR(255),
               state_data JSON NOT NULL,
               saved_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                
            
               CONSTRAINT LANGRAPH4J_FK_THREAD
                   FOREIGN KEY (thread_id)
                   REFERENCES LANGRAPH4J_THREAD(thread_id)
                   ON DELETE CASCADE
            )""";

    private static final String DROP_CHECKPOINT_TABLE = "DROP TABLE IF EXISTS LANGRAPH4J_CHECKPOINT";
    private static final String DROP_THREAD_TABLE = "DROP TABLE IF EXISTS LANGRAPH4J_THREAD";

    // DML statements
    private static final String UPSERT_THREAD = """
            INSERT INTO LANGRAPH4J_THREAD (thread_id, thread_name, is_released)
            VALUES (?, ?, FALSE)
            ON DUPLICATE KEY UPDATE thread_id = thread_id
            """;

    private static final String INSERT_CHECKPOINT = """
            INSERT INTO LANGRAPH4J_CHECKPOINT(checkpoint_id, thread_id, node_id, next_node_id, state_data)
            SELECT ?, thread_id, ?, ?, ?
            FROM LANGRAPH4J_THREAD
            WHERE thread_name = ? AND is_released = FALSE
            """;

    private static final String UPDATE_CHECKPOINT = """
            UPDATE LANGRAPH4J_CHECKPOINT
            SET
              checkpoint_id = ?,
              node_id = ?,
              next_node_id = ?,
              state_data = ?
            WHERE checkpoint_id = ?
            """;

    private static final String SELECT_CHECKPOINTS = """
            SELECT
              c.checkpoint_id,
              c.node_id,
              c.next_node_id,
              c.state_data
            FROM LANGRAPH4J_CHECKPOINT c
              INNER JOIN LANGRAPH4J_THREAD t ON c.thread_id = t.thread_id
            WHERE t.thread_name = ? AND t.is_released != TRUE
            ORDER BY c.saved_at DESC, c.id DESC
            """;

    private static final String DELETE_CHECKPOINTS = """
                DELETE FROM LANGRAPH4J_CHECKPOINT WHERE checkpoint_id = ?
            """;

    private static final String RELEASE_THREAD = """
            UPDATE LANGRAPH4J_THREAD SET is_released = TRUE WHERE thread_name = ? AND is_released = FALSE
            """;

    /**
     * A builder for MysqlSaver.
     */
    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        protected DataSource dataSource;
        protected CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;

        @SuppressWarnings("unchecked")
        private B this$() {
            return (B)this;
        }
        /**
         * Sets the datasource
         *
         * @param dataSource the datasource
         * @return this builder
         */
        public B dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this$();
        }

        /**
         * Sets the create options (default {@link CreateOption#CREATE_IF_NOT_EXISTS}.
         *
         * @param createOption the create options
         * @return this builder
         */
        public B createOption(CreateOption createOption) {
            this.createOption = createOption;
            return this$();
        }

    }

    // Configuration
    protected final DataSource dataSource;
    protected final CreateOption createOption;
    protected final ObjectMapper objectMapper;

    /**
     * protected constructor used by the builder to create a new instance of
     * MysqlSaver.
     *
     * @param builder   Builder instance
     */
    protected AbstractMysqlServer(AbstractBuilder<? > builder) {
        this.dataSource = builder.dataSource;
        this.createOption = builder.createOption;
        this.objectMapper = new ObjectMapper();
        initTables();
    }


    /**
     * If the list of checkpoints is empty, loads the checkpoints from the database.
     *
     * @param config      the configuration
     * @return a list of checkpoints
     * @throws Exception if an error occurs while the checkpoints are being
     *                   loaded from the database.
     */
    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();
        final var threadName = threadId(config);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHECKPOINTS)) {

            preparedStatement.setString(1, threadName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String jsonString = resultSet.getString(4);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> state = objectMapper.readValue(jsonString, Map.class);
                    Checkpoint checkpoint = Checkpoint.builder()
                            .id(resultSet.getString(1))
                            .nodeId(resultSet.getString(2))
                            .nextNodeId(resultSet.getString(3))
                            .state(state)
                            .build();
                    checkpoints.add(checkpoint);
                }
            }
        } catch (SQLException sqlException) {
            throw new Exception("Unable to load checkpoints", sqlException);
        }
        return checkpoints;
    }

    /**
     * Inserts a checkpoint to the database
     *
     * @param config      the configuration
     * @param checkpoints the list of checkpoints
     * @param checkpoint  the checkpoint to insert
     * @throws Exception if an error occurs while inserting the checkpoint in the
     *                   database.
     */
    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {

        final String threadName = config.threadId().orElse(THREAD_ID_DEFAULT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsertStatement = connection.prepareStatement(UPSERT_THREAD);
             PreparedStatement insertCheckpointStatement = connection.prepareStatement(INSERT_CHECKPOINT)) {

            upsertStatement.setString(1, UUID.randomUUID().toString());
            upsertStatement.setString(2, threadName);
            upsertStatement.execute();

            insertCheckpointStatement.setString(1, checkpoint.getId());
            insertCheckpointStatement.setString(2, checkpoint.getNodeId());
            insertCheckpointStatement.setString(3, checkpoint.getNextNodeId());
            insertCheckpointStatement.setString(4, objectMapper.writeValueAsString(checkpoint.getState()));
            insertCheckpointStatement.setString(5, threadName);

            insertCheckpointStatement.execute();
        } catch (SQLException sqlException) {
            throw new RuntimeException("Unable to insert checkpoint", sqlException);
        }

    }

    /**
     * Marks the checkpoints as released
     *
     * @param config      the configuration
     * @param checkpoints the checkpoints
     * @throws Exception if an error occurs while marking the checkpoints as
     *                   released
     */
    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, String message) throws Exception {
        final String threadName = threadId(config);

        try (Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(RELEASE_THREAD)) {
            preparedStatement.setString(1, threadName);
            preparedStatement.execute();
        } catch (SQLException sqlException) {
            throw new Exception("Unable to release checkpoint", sqlException);
        }

        return new Tag( threadName, checkpoints);
    }


    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, exception.getMessage());
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }

    /**
     * If the checkpoint exists, updates the checkpoint, otherwise it inserts it.
     *
     * @param config      the configuration
     * @param checkpoints the list of checkpoints
     * @param checkpoint  the checkpoint
     * @throws Exception if an error occurs while inserting or updating the
     *                   checkpoint.
     */
    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        if (config.checkPointId().isPresent()) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_CHECKPOINT)) {
                preparedStatement.setString(1, checkpoint.getId());
                preparedStatement.setString(2, checkpoint.getNodeId());
                preparedStatement.setString(3, checkpoint.getNextNodeId());
                preparedStatement.setString(4, objectMapper.writeValueAsString(checkpoint.getState()));
                preparedStatement.setString(5, config.checkPointId().get());
                preparedStatement.execute();
            } catch (SQLException sqlException) {
                throw new Exception("Unable to update checkpoint", sqlException);
            }
        } else {
            insertedCheckpoint(config, checkpoints, checkpoint);
        }
    }

    /**
     * Initializes the database according the create options.
     */
    protected void initTables() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (createOption == CreateOption.CREATE_OR_REPLACE) {
                // Drop tables (indexes are automatically dropped with tables in MySQL)
                statement.addBatch(DROP_CHECKPOINT_TABLE);
                statement.addBatch(DROP_THREAD_TABLE);
                statement.executeBatch();
            }
            if (createOption == CreateOption.CREATE_OR_REPLACE ||
                    createOption == CreateOption.CREATE_IF_NOT_EXISTS) {
                statement.execute(CREATE_THREAD_TABLE);
                statement.execute(CREATE_CHECKPOINT_TABLE);

                // Try to create index, ignore error if it already exists
                try {
                    statement.execute(INDEX_THREAD_TABLE);
                } catch (SQLException e) {
                    // Ignore "Duplicate key name" error (error code 1061)
                    if (e.getErrorCode() != 1061) {
                        throw e;
                    }
                }
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException("Unable to create tables", sqlException);
        }
    }

    /**
     * Removes the cached checkpoints associated with the given thread identifier from the in-memory cache.
     *
     * @param threadId the thread identifier whose cached checkpoints must be cleared
     * @return the checkpoints removed from the cache, or an empty collection if no cached checkpoints exist
     * @deprecated this method do nothing because currently this saver don't use cache anymore
     */
    @Deprecated(forRemoval = true)
    public Collection<Checkpoint> clearCheckpointsCache( String threadId ) {
        return List.of();
    }


}
