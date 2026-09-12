package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.bsc.langgraph4j.utils.TryFunction;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public abstract class AbstractMySQLSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    /**
     * A builder for MysqlSaver.
     */
    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        protected DataSource dataSource;
        protected CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;
        public Map<String,StateSerializer<? extends AgentState>> stateSerializerMap = new LinkedHashMap<>(2);


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

        /**
         * Sets the state serializer
         *
         * @param stateSerializer the state serializer
         * @return this builder
         */
        public B stateSerializer(StateSerializer<? extends AgentState> stateSerializer) {
            this.stateSerializerMap.put(stateSerializer.contentType(), stateSerializer);
            return this$();
        }
    }

    // Configuration
    protected final DataSource dataSource;
    protected final Map<String,StateSerializer<? extends AgentState>> stateSerializerMap;
    protected final SqlResource.Commands sqlCommands;

    /**
     * protected constructor used by the builder to create a new instance of
     * MysqlSaver.
     *
     * @param builder Builder instance
     */
    protected AbstractMySQLSaver(AbstractBuilder<?> builder) throws Exception {
        this.dataSource = builder.dataSource;
        this.sqlCommands = SqlResource.Commands.load(sqlCommandsResourcePath());
        if( builder.stateSerializerMap.isEmpty() ) {
            throw new IllegalArgumentException("no stateSerializer provided");
        }
        this.stateSerializerMap = builder.stateSerializerMap;
        initTables(builder.createOption);
    }

    protected abstract String sqlCommandsResourcePath();

    protected abstract String sqlInitResourcePath();

    /**
     * Initializes the database according the create options.
     */
    protected void initTables( CreateOption createOption) throws Exception {

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

    protected final  String encodeState(Map<String, Object> data) throws IOException {
        final var stateSerializer = encoderStateSerializer(); // get first added state serializer;
        final byte[] binaryData = stateSerializer.dataToBytes(data);
        return Base64.getEncoder().encodeToString(binaryData);
    }

    protected final Map<String, Object> decodeState(String binaryPayload, String contentType) throws IOException, ClassNotFoundException {
        final var stateSerializer = stateSerializerMap.get(contentType);
        if (stateSerializer==null) {
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
        final var threadName = threadId(config);

        final var sqlSelectCheckpoints = sqlCommands.get("sqlSelectCheckpoints");

        return exec(connection -> {
            try (var preparedStatement = connection.prepareStatement(sqlSelectCheckpoints)) {

                preparedStatement.setString(1, threadName);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Checkpoint checkpoint = Checkpoint.builder()
                                .id(resultSet.getString(1))
                                .nodeId(resultSet.getString(2))
                                .nextNodeId(resultSet.getString(3))
                                .state(decodeState(resultSet.getString(4), resultSet.getString(5)))
                                .build();
                        checkpoints.add(checkpoint);
                    }
                }
            }
            return checkpoints;
        });
    }

    protected void insertCheckpoint( Connection connection, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        final String threadName = threadId(config);

        final var sqlUpsertThread = sqlCommands.get("sqlUpsertThread");
        final var sqlLastInsertId = sqlCommands.get("sqlUpsertThread_last_insert_id");
        final var sqlInsertCheckpoint = sqlCommands.get("sqlInsertCheckpoint");

        try (var upsertStatement = connection.prepareStatement(sqlUpsertThread);
             var lastInsertId = connection.prepareStatement(sqlLastInsertId);
             var insertCheckpointStatement = connection.prepareStatement(sqlInsertCheckpoint)) {

            upsertStatement.setString(1, threadName);
            upsertStatement.execute();

            long threadKey = -1;
            try (ResultSet rs = lastInsertId.executeQuery()) {
                if (rs.next()) {
                    threadKey = rs.getLong(1);
                    log.trace("threadId {} for thread {}", threadKey, threadName);
                }
            }

            var index = 0;
            insertCheckpointStatement.setString(++index, checkpoint.getId());
            insertCheckpointStatement.setNull(++index, Types.VARCHAR);
            insertCheckpointStatement.setLong(++index, threadKey);
            insertCheckpointStatement.setString(++index, checkpoint.getNodeId());
            insertCheckpointStatement.setString(++index, checkpoint.getNextNodeId());
            insertCheckpointStatement.setString(++index, encodeState(checkpoint.getState()));
            insertCheckpointStatement.setString(++index, encoderStateSerializer().contentType());
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
     * @param config      the configuration
     * @param checkpoints the checkpoints
     * @throws Exception if an error occurs while marking the checkpoints as
     *                   released
     */
    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, String message) throws Exception {
        final String threadName = threadId(config);
        final var sqlReleaseThread = sqlCommands.get("sqlReleaseThread");

        return exec(connection -> {

            try (var preparedStatement = connection.prepareStatement(sqlReleaseThread)) {
                preparedStatement.setString(1, threadName);
                preparedStatement.execute();
            }
            return new Tag(threadName, checkpoints);
        });
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
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

        execTransaction(connection -> {
            if (config.checkPointId().isPresent()) {
                final var sqlUpdateCheckpoint = sqlCommands.get("sqlUpdateCheckpoint");

                try (var preparedStatement = connection.prepareStatement(sqlUpdateCheckpoint)) {
                    preparedStatement.setString(1, checkpoint.getId());
                    preparedStatement.setString(2, checkpoint.getNodeId());
                    preparedStatement.setString(3, checkpoint.getNextNodeId());
                    preparedStatement.setString(4, encodeState(checkpoint.getState()));
                    preparedStatement.setString(5, config.checkPointId().get());
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
