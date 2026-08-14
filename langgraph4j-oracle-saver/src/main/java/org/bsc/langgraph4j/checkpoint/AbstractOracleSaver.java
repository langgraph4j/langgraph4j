package org.bsc.langgraph4j.checkpoint;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.provider.oson.OsonFactory;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public abstract class AbstractOracleSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        protected DataSource dataSource;
        protected CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;
        public StateSerializer<? extends AgentState> stateSerializer;

        @SuppressWarnings("unchecked")
        private B this$() {
            return (B) this;
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

        public B stateSerializer(StateSerializer<? extends AgentState> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this$();
        }
    }

    protected final DataSource dataSource;
    protected final CreateOption createOption;
    protected final StateSerializer<? extends AgentState> stateSerializer;
    protected final ObjectMapper objectMapper;
    protected final SqlResource.Commands sqlCommands;

    protected AbstractOracleSaver(AbstractBuilder<?> builder) throws Exception {
        this.dataSource = builder.dataSource;
        this.createOption = builder.createOption;
        this.stateSerializer = builder.stateSerializer;
        if (builder.stateSerializer != null) {
            objectMapper = null;
        } else {
            JsonFactory osonFactory = new OsonFactory();
            objectMapper = new ObjectMapper(osonFactory);
        }
        this.sqlCommands = SqlResource.Commands.load("db/v1.0__commands.sql");

        initTables();
    }

    /**
     * Initializes the database according the create options.
     */
    protected void initTables() throws Exception {
        final var sqlInitCommands = SqlResource.Commands.load("db/migration/v1.0__init.sql");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (createOption == CreateOption.CREATE_OR_REPLACE) {
                for (var sql : sqlCommands.getMultiple("sqlDropTables")) {
                    log.trace("Executing drop table:\n---\n{}---", sql);
                    statement.execute(sql);
                }
            }
            if (createOption == CreateOption.CREATE_OR_REPLACE ||
                    createOption == CreateOption.CREATE_IF_NOT_EXISTS) {
                for (var sql : sqlInitCommands.getMultiple("sqlCreateTables")) {
                    log.trace("Executing create tables:\n---\n{}---", sql);
                    statement.execute(sql);
                }
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException("Unable to create tables", sqlException);
        }
    }

    private String encodeState(Map<String, Object> data) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");

        if (stateSerializer == null) {
            return objectMapper.writeValueAsString(data);
        }

        var bytes = stateSerializer.dataToBytes(data);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeState(String statePayload,
                                            @Nullable String stateContentType) throws IOException, ClassNotFoundException {

        if (stateSerializer == null) {
            return objectMapper.readValue(statePayload, Map.class);
        }

        if (stateContentType != null && !Objects.equals(stateContentType, stateSerializer.contentType())) {
            throw new IllegalStateException(
                    "Content Type used for stored state '%s' is different from one '%s' used to deserialize it".formatted(
                            stateContentType,
                            stateSerializer.contentType()));
        }

        var bytes = Base64.getDecoder().decode(statePayload);

        return stateSerializer.dataFromBytes(bytes);
    }

    /**
     * If the list of checkpoints is empty, loads the checkpoints from the database.
     *
     * @param config the configuration
     * @return a list of checkpoints
     * @throws Exception if an error occurs while the checkpoints are being
     *                   loaded from the database.
     */
    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var checkpoints = new LinkedList<Checkpoint>();
        final String threadName = threadId(config);
        final var sqlSelectCheckpoints = sqlCommands.get("sqlSelectCheckpoints");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlSelectCheckpoints)) {

            // Calls to defineColumnType reduce the number of network requests.
            OracleStatement oracleStatement = preparedStatement.unwrap(OracleStatement.class);
            oracleStatement.defineColumnType(1, OracleTypes.VARCHAR); // checkpoint_id
            oracleStatement.defineColumnType(2, OracleTypes.VARCHAR); // node_id
            oracleStatement.defineColumnType(3, OracleTypes.VARCHAR); // next_node_id
            oracleStatement.defineColumnType(4, OracleTypes.CLOB); // state_data
            oracleStatement.setLobPrefetchSize(Integer.MAX_VALUE); // Workaround for Oracle JDBC bug 37030121

            preparedStatement.setString(1, threadName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Checkpoint checkpoint = Checkpoint.builder()
                            .id(resultSet.getString(1))
                            .nodeId(resultSet.getString(2))
                            .nextNodeId(resultSet.getString(3))
                            .state(decodeState(resultSet.getString(4), null))
                            .build();
                    checkpoints.add(checkpoint);
                }
            }
        } catch (SQLException sqlException) {
            throw new Exception("Unable to create tables", sqlException);
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
        final var sqlUpsertThread = sqlCommands.get("sqlUpsertThread");
        final var sqlInsertCheckpoint = sqlCommands.get("sqlInsertCheckpoint");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsertStatement = connection.prepareStatement(sqlUpsertThread);
             PreparedStatement insertCheckpointStatement = connection.prepareStatement(sqlInsertCheckpoint)) {

            upsertStatement.setString(1, UUID.randomUUID().toString());
            upsertStatement.setString(2, threadName);
            upsertStatement.execute();

            insertCheckpointStatement.setString(1, checkpoint.getId());
            insertCheckpointStatement.setString(2, checkpoint.getNodeId());
            insertCheckpointStatement.setString(3, checkpoint.getNextNodeId());
            insertCheckpointStatement.setString(4, encodeState(checkpoint.getState()));
            insertCheckpointStatement.setString(5, threadName);

            insertCheckpointStatement.execute();
        } catch (SQLException sqlException) {
            throw new RuntimeException("Unable to insert checkpoint", sqlException);
        }
    }

    /**
     * Marks the checkpoints as released
     *
     * @param config      the configuraiton
     * @param checkpoints the checkpoints
     * @throws Exception if an error occurs while marking the checkpoints as
     *                   released
     */
    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final String threadName = threadId(config);
        final var sqlReleaseThread = sqlCommands.get("sqlReleaseThread");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlReleaseThread)) {
            preparedStatement.setString(1, threadName);
            preparedStatement.execute();
        } catch (SQLException sqlException) {
            throw new Exception("Unable to release checkpoint", sqlException);
        }

        return new Tag(threadName, checkpoints);
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
            final var sqlUpdateCheckpoint = sqlCommands.get("sqlUpdateCheckpoint");
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(sqlUpdateCheckpoint)) {
                preparedStatement.setString(1, checkpoint.getId());
                preparedStatement.setString(2, checkpoint.getNodeId());
                preparedStatement.setString(3, checkpoint.getNextNodeId());
                preparedStatement.setString(4, encodeState(checkpoint.getState()));
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
     * Removes the cached checkpoints associated with the given thread identifier from the in-memory cache.
     *
     * @param threadId the thread identifier whose cached checkpoints must be cleared
     * @return the checkpoints removed from the cache, or an empty collection if no cached checkpoints exist
     * @deprecated this method do nothing because currently this saver don't use cache anymore
     */
    @Deprecated(forRemoval = true)
    public Collection<Checkpoint> clearCheckpointsCache(String threadId) {
        return List.of();
    }
}
