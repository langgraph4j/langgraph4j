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
import org.bsc.langgraph4j.utils.TryFunction;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public abstract class AbstractOracleSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        protected DataSource dataSource;
        protected CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;
        public Map<String, StateSerializer<? extends AgentState>> stateSerializerMap = new LinkedHashMap<>(2);


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
            this.stateSerializerMap.put(stateSerializer.contentType(), stateSerializer);
            return this$();
        }
    }

    protected final DataSource dataSource;
    protected final Map<String, StateSerializer<? extends AgentState>> stateSerializerMap;
    protected final SqlResource.Commands sqlCommands;

    protected AbstractOracleSaver(AbstractBuilder<?> builder) throws Exception {
        this.dataSource = builder.dataSource;
        this.stateSerializerMap = builder.stateSerializerMap;
        this.sqlCommands = SqlResource.Commands.load(sqlCommandsResourcePath());

        initTables(builder.createOption);
    }

    protected abstract String sqlCommandsResourcePath();

    protected abstract String sqlInitResourcePath();

    /**
     * Initializes the database according the create options.
     */
    protected void initTables(CreateOption createOption) throws Exception {
        final var sqlInitCommands = SqlResource.Commands.load(sqlInitResourcePath());

        execTransaction(connection -> {

            try (var statement = connection.createStatement()) {
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
            }
            return null;
        });
    }

    private StateSerializer<? extends AgentState> encoderStateSerializer() {
        return stateSerializerMap.values().iterator().next(); // get first added state serializer;
    }

    protected final String encodeState(Map<String, Object> data) throws IOException {
        final var stateSerializer = encoderStateSerializer(); // get first added state serializer;
        final byte[] binaryData = stateSerializer.dataToBytes(data);
        return Base64.getEncoder().encodeToString(binaryData);
    }

    protected final Map<String, Object> decodeState(String binaryPayload, String contentType) throws IOException, ClassNotFoundException {
        final var stateSerializer = stateSerializerMap.get(contentType);
        if (stateSerializer == null) {
            throw new IllegalStateException(
                    "Content Type used for store state '%s' has not been provided!".formatted(contentType));
        }

        final byte[] bytes = Base64.getDecoder().decode(binaryPayload);

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

        return exec(connection -> {
            try (var preparedStatement = connection.prepareStatement(sqlSelectCheckpoints)) {

                // Calls to defineColumnType reduce the number of network requests.
                OracleStatement oracleStatement = preparedStatement.unwrap(OracleStatement.class);
                oracleStatement.defineColumnType(1, OracleTypes.VARCHAR); // checkpoint_id
                oracleStatement.defineColumnType(2, OracleTypes.VARCHAR); // node_id
                oracleStatement.defineColumnType(3, OracleTypes.VARCHAR); // next_node_id
                oracleStatement.defineColumnType(4, OracleTypes.CLOB); // state_data
                oracleStatement.defineColumnType(5, OracleTypes.VARCHAR); // state_data_type
                oracleStatement.setLobPrefetchSize(Integer.MAX_VALUE); // Workaround for Oracle JDBC bug 37030121

                preparedStatement.setString(1, threadName);
                try (var rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        Checkpoint checkpoint = Checkpoint.builder()
                                .id(rs.getString(1))
                                .nodeId(rs.getString(2))
                                .nextNodeId(rs.getString(3))
                                .state(decodeState(rs.getString(4), rs.getString(5)))
                                .build();
                        checkpoints.add(checkpoint);
                    }
                }
            }
            return checkpoints;
        });
    }

    protected void insertCheckpoint(Connection connection, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {

        final String threadName = config.threadId().orElse(THREAD_ID_DEFAULT);
        final var sqlUpsertThread = sqlCommands.get("sqlUpsertThread");
        final var sqlInsertCheckpoint = sqlCommands.get("sqlInsertCheckpoint");

        try (var upsertStatement = connection.prepareStatement(sqlUpsertThread);
             var insertCheckpointStatement = connection.prepareStatement(sqlInsertCheckpoint)) {

            upsertStatement.setString(1, UUID.randomUUID().toString());
            upsertStatement.setString(2, threadName);
            upsertStatement.execute();

            insertCheckpointStatement.setString(1, checkpoint.getId());
            insertCheckpointStatement.setString(2, checkpoint.getNodeId());
            insertCheckpointStatement.setString(3, checkpoint.getNextNodeId());
            insertCheckpointStatement.setString(4, encodeState(checkpoint.getState()));
            insertCheckpointStatement.setString(5, encoderStateSerializer().contentType());
            insertCheckpointStatement.setString(6, threadName);

            insertCheckpointStatement.execute();
        }
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
        execTransaction(connection -> {
            insertCheckpoint(connection, config, checkpoints, checkpoint);
            return null;
        });
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

    /**
     * If the checkpoint exists, updates the checkpoint, otherwise it inserts it.
     *
     * @param config      the configuration
     * @param checkpoints the list of checkpoints
     * @param checkpoint  the checkpoint
     * @throws Exception if an error occurs while inserting or updating the
     *                   checkpoint.
     */
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

        execTransaction(connection -> {
            if (config.checkPointId().isPresent()) {
                final var sqlUpdateCheckpoint = sqlCommands.get("sqlUpdateCheckpoint");

                try (var preparedStatement = connection.prepareStatement(sqlUpdateCheckpoint)) {
                    preparedStatement.setString(1, checkpoint.getId());
                    preparedStatement.setString(2, checkpoint.getNodeId());
                    preparedStatement.setString(3, checkpoint.getNextNodeId());
                    preparedStatement.setString(4, encodeState(checkpoint.getState()));
                    preparedStatement.setString(5, encoderStateSerializer().contentType());
                    preparedStatement.setString(6, config.checkPointId().get());
                    preparedStatement.execute();
                }
            } else {
                insertCheckpoint(connection, config, checkpoints, checkpoint);
            }
            return null;
        });
    }

    protected final <R> R exec(TryFunction<Connection, R, Exception> execStatement) throws Exception {
        final var connection = dataSource.getConnection();

        connection.setAutoCommit(true);

        return execStatement.tryApply(connection);
    }

    protected final <R> R execTransaction(TryFunction<Connection, R, Exception> execStatement) throws Exception {
        final var connection = dataSource.getConnection();

        final var previousAutoCommit = connection.getAutoCommit();

        connection.setAutoCommit(false);
        try {
            return execStatement.tryApply(connection);
        } catch (Exception e) {
            log.error("Error executing statement", e);
            connection.rollback();
            throw e;
        } finally {
            connection.commit();
            connection.setAutoCommit(previousAutoCommit);
        }
    }

}
