package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.bsc.langgraph4j.utils.TryFunction;
import org.jspecify.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

public abstract class AbstractPostgresSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        public Map<String, StateSerializer<? extends AgentState>> stateSerializerMap = new LinkedHashMap<>(2);
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String database;
        private boolean createTables;
        private boolean dropTablesFirst;
        private DataSource datasource;
        private boolean plainTextStateSerializerLegacyMode = false;
        private final Properties additionalProperties = new Properties();

        @SuppressWarnings("unchecked")
        private B this$() {
            return (B) this;
        }

        public <State extends AgentState> B stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializerMap.put(stateSerializer.contentType(), stateSerializer);
            return this$();
        }

        /**
         * Intended to enable compatibility mode for {@code PlainTextStateSerializer}-based state payloads.
         * The legacy mode save the JSON payload as binary format (i.e. a serialized java String )
         * If state serializer is not a PlainTextStateSerializer implementation this flag is ignored
         *
         * @param mode compatibility flag value (default is false)
         */
        public B plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return this$();
        }


        public B host(String host) {
            this.host = host;
            return this$();
        }

        public B port(Integer port) {
            this.port = port;
            return this$();
        }

        public B user(String user) {
            this.user = user;
            return this$();
        }

        public B password(String password) {
            this.password = password;
            return this$();
        }

        public B database(String database) {
            this.database = database;
            return this$();
        }

        public B datasource(DataSource datasource) {
            this.datasource = datasource;
            return this$();
        }

        public B property(String name, String value) {
            this.additionalProperties.setProperty(name, value);
            return this$();
        }

        public B properties(Properties properties) {
            this.additionalProperties.putAll(properties);
            return this$();
        }

        public B createTables(boolean createTables) {
            this.createTables = createTables;
            return this$();
        }

        public B dropTablesFirst(boolean dropTablesFirst) {
            this.dropTablesFirst = dropTablesFirst;
            return this$();
        }

        private String requireNotBlank(String value, String name) {
            if (requireNonNull(value, format("'%s' cannot be null", name)).isBlank()) {
                throw new IllegalArgumentException(format("'%s' cannot be blank", name));
            }
            return value;
        }

        private void validate() throws SQLException {
            if (stateSerializerMap.isEmpty()) {
                throw new IllegalArgumentException("no stateSerializer provided");
            }

            // Create datasource individually
            if (datasource == null) {
                if (port <= 0) {
                    throw new IllegalArgumentException("port must be greater than 0");
                }
                var ds = new PGSimpleDataSource();
                ds.setDatabaseName(requireNotBlank(database, "database"));
                ds.setUser(requireNotBlank(user, "user"));
                ds.setPassword(requireNonNull(password, "password cannot be null"));
                ds.setPortNumbers(new int[]{port});
                ds.setServerNames(new String[]{requireNotBlank(host, "host")});
                for (var entry : additionalProperties.entrySet()) {
                    ds.setProperty(entry.getKey().toString(), entry.getValue().toString());
                }
                datasource = ds;
            }

            // Or use the shared datasource
            createTables = createTables || dropTablesFirst;
        }
    }

    /**
     * Datasource used to create the store
     */
    protected final DataSource datasource;
    private final Map<String, StateSerializer<? extends AgentState>> stateSerializerMap;
    private final boolean plainTextStateSerializerLegacyMode;
    protected final SqlResource.Commands sqlCommands;

    protected AbstractPostgresSaver(AbstractBuilder<?> builder) throws Exception {
        builder.validate();

        this.datasource = builder.datasource;
        this.plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;
        this.sqlCommands = SqlResource.Commands.load(sqlCommandsResourcePath());
        this.stateSerializerMap = builder.stateSerializerMap;

        initTable(builder.dropTablesFirst, builder.createTables);
    }

    protected String sqlCommandsResourcePath() {
        return "db/v1.0__commands.sql";
    }

    protected String sqlInitResourcePath() {
        return "db/migration/v1.0__init.sql";
    }

    protected final StateSerializer<? extends AgentState> encoderStateSerializer() {
        return stateSerializerMap.values().iterator().next(); // get first added state serializer;
    }

    private void rollback(Connection conn, Checkpoint checkpoint, String threadId) {
        if (conn == null) return;

        requireNonNull(checkpoint, "checkpoint cannot be null");

        try {
            conn.rollback();
            log.warn("Transaction rolled back for checkpoint {}", checkpoint.getId());
        } catch (SQLException exRollback) {
            log.error("Failed to rollback transaction for checkpoint id {} in thread {}",
                    checkpoint.getId(),
                    threadId,
                    exRollback);
        }
    }

    protected String encodeState(Map<String, Object> data) throws IOException {
        final var stateSerializer = encoderStateSerializer();
        final byte[] binaryData;

        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            binaryData = ser.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
        } else {
            binaryData = stateSerializer.dataToBytes(data);
        }
        final var base64Data = Base64.getEncoder().encodeToString(binaryData);
        return """
                {"binaryPayload": "%s"}
                """.formatted(base64Data);
    }

    protected Map<String, Object> decodeState(byte[] binaryPayload, String contentType) throws IOException, ClassNotFoundException {
        final var stateSerializer = stateSerializerMap.get(contentType);
        if (stateSerializer == null) {
            throw new IllegalStateException(
                    "Content Type used for store state '%s' has not been provided!".formatted(contentType));
        }

        final byte[] bytes = Base64.getDecoder().decode(binaryPayload);

        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            return ser.readDataFromString(new String(bytes, StandardCharsets.UTF_8));
        }
        return stateSerializer.dataFromBytes(bytes);
    }

    protected void initTable(boolean dropTablesFirst, boolean createTables) throws Exception {
        final var sqlInitCommands = SqlResource.Commands.load(sqlInitResourcePath());

        execTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                if (dropTablesFirst) {
                    for (var sql : sqlCommands.getMultiple("sqlDropTables")) {
                        log.trace("Executing drop table:\n---\n{}---", sql);
                        statement.execute(sql);
                    }
                }
                if (createTables) {
                    for (var sql : sqlInitCommands.getMultiple("sqlCreateTables")) {
                        log.trace("Executing create tables:\n---\n{}---", sql);
                        statement.execute(sql);
                    }
                }
            }
            return null;
        });
    }


    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();

        final var threadId = threadId(config);

        final var sqlCheckThread = sqlCommands.get("sqlCheckThread");
        final var sqlQueryCheckpoints = sqlCommands.get("sqlSelectCheckpoints");

        return exec(conn -> {

            try (var ps = conn.prepareStatement(sqlCheckThread)) {
                ps.setString(1, threadId);
                var resultSet = ps.executeQuery();
                resultSet.next();
                var count = resultSet.getInt(1);

                if (count == 0) {
                    return checkpoints;
                }
                if (count > 1) {
                    throw new IllegalStateException(format("there are more than one Thread '%s' open (not released yet)", threadId));
                }
            }

            log.trace("Executing select checkpoints:\n---\n{}---", sqlQueryCheckpoints);
            try (var ps = conn.prepareStatement(sqlQueryCheckpoints)) {
                ps.setString(1, threadId);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var checkpoint = Checkpoint.builder()
                            .id(rs.getString(1))
                            .nodeId(rs.getString(2))
                            .nextNodeId(rs.getString(3))
                            .state(decodeState(rs.getBytes(4), rs.getString(5)))
                            .build();
                    checkpoints.add(checkpoint);
                }
            }
            return checkpoints;

        });

    }

    protected abstract void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception;

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

        execTransaction(conn -> {
            ;

            insertCheckpoint(conn, config, checkpoints, checkpoint);
            return null;

        });

    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

        execTransaction(connection -> {
            if (config.checkPointId().isPresent()) {
                final var sqlUpdateCheckpoint = sqlCommands.get("sqlUpdateCheckpoint");

                try (var preparedStatement = connection.prepareStatement(sqlUpdateCheckpoint)) {
                    preparedStatement.setObject(1, UUID.fromString(checkpoint.getId()), Types.OTHER);
                    preparedStatement.setString(2, checkpoint.getNodeId());
                    preparedStatement.setString(3, checkpoint.getNextNodeId());
                    preparedStatement.setString(4, encodeState(checkpoint.getState()));
                    preparedStatement.setObject(5, UUID.fromString(config.checkPointId().get()), Types.OTHER);
                    preparedStatement.execute();
                }
            } else {
                insertCheckpoint(connection, config, checkpoints, checkpoint);
            }
            return null;
        });
    }

    protected void updatedCheckpoint2(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {

        final var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var deletePreviousCheckpointSql = sqlCommands.get("sqlDeletePreviousCheckpoint");

        execTransaction(conn -> {

            if (config.checkPointId().isPresent()) {

                try (PreparedStatement ps = conn.prepareStatement(deletePreviousCheckpointSql)) {
                    var field = 0;
                    ps.setObject(++field,
                            UUID.fromString(config.checkPointId().get()),
                            Types.OTHER); // nullable
                    log.trace("Executing deleting previous checkpoint with id {} in thread {}:\n---\n{}---",
                            config.checkPointId().get(),
                            threadId,
                            deletePreviousCheckpointSql);
                    ps.executeUpdate();
                }
            }

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            log.debug("Checkpoint with id {} for thread {} inserted successfully.",
                    checkpoint.getId(),
                    threadId);

            return null;
        });
    }



    protected final <R> R exec(TryFunction<Connection, R, Exception> execStatement) throws Exception {
        final var connection = datasource.getConnection();

        connection.setAutoCommit(true);

        return execStatement.tryApply(connection);
    }


    protected final <R> R execTransaction(TryFunction<Connection, R, Exception> execStatement) throws Exception {
        try (Connection connection = datasource.getConnection()) {
            final var previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                final var result = execStatement.tryApply(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                log.error("Error executing statement", e);
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }


}
