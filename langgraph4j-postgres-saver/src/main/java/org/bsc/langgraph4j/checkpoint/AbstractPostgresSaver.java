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
        public StateSerializer<? extends AgentState> stateSerializer;
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
            this.stateSerializer = stateSerializer;
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

        protected void validate() throws SQLException {
            requireNonNull(stateSerializer, "stateSerializer cannot be null");

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
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final boolean plainTextStateSerializerLegacyMode;
    protected final SqlResource.Commands sqlCommands;

    protected AbstractPostgresSaver(AbstractBuilder<?> builder) throws Exception {
        this.datasource = builder.datasource;
        this.stateSerializer = builder.stateSerializer;
        this.plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;
        this.sqlCommands = SqlResource.Commands.load(sqlCommandsResourcePath());

        initTable(builder.dropTablesFirst, builder.createTables);
    }

    protected String sqlCommandsResourcePath() {
        return "db/v1.0__commands.sql";
    }

    protected String sqlInitResourcePath() {
        return "db/migration/v1.0__init.sql";
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

    private String encodeState(Map<String, Object> data) throws IOException {
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

    private Map<String, Object> decodeState(byte[] binaryPayload, String contentType) throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(
                    format("Content Type used for store state '%s' is different from one '%s' used for deserialize it",
                            contentType,
                            stateSerializer.contentType()));
        }

        final byte[] bytes = Base64.getDecoder().decode(binaryPayload);

        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            return ser.readDataFromString(new String(bytes, StandardCharsets.UTF_8));
        }
        return stateSerializer.dataFromBytes(bytes);
    }

    protected void initTable(boolean dropTablesFirst, boolean createTables) throws Exception {
        final var sqlInitCommands = SqlResource.Commands.load(sqlInitResourcePath());

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
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
    }


    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();

        final var threadId = threadId(config);

        final var sqlCheckThread = sqlCommands.get("sqlCheckThread");
        final var sqlQueryCheckpoints = sqlCommands.get("sqlSelectCheckpoints");
        try (Connection conn = getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(sqlCheckThread)) {
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
            try (PreparedStatement ps = conn.prepareStatement(sqlQueryCheckpoints)) {
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

        }

        return checkpoints;
    }

    private void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var upsertThreadSql = sqlCommands.get("sqlUpsertThread");

        var insertCheckpointSql = sqlCommands.get("sqlInsertCheckpoint");
        Long id = null;

        // 1. Upsert thread information
        try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
            var field = 0;
            ps.setString(++field, threadId); // thread id
            ps.setString(++field, threadId); // thread id

            log.trace("Executing upsert thread:\n---\n{}---", upsertThreadSql);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    id = rs.getLong("thread_id");
                }
            }
        }


        // 2. Insert checkpoint data
        try (PreparedStatement ps = conn.prepareStatement(insertCheckpointSql)) {
            var field = 0;
            // checkpoint_id
            ps.setObject(++field,
                    UUID.fromString(checkpoint.getId()),
                    Types.OTHER);
            // parent_checkpoint_id
            ps.setNull(++field, java.sql.Types.OTHER);
            // thread_id
            ps.setLong(++field,
                    requireNonNull(id, "thread id cannot be null"));
            // node_id
            ps.setString(++field, checkpoint.getNodeId());
            // next_node_id
            ps.setString(++field, checkpoint.getNextNodeId());
            // state_data
            ps.setString(++field, encodeState(checkpoint.getState()));
            // state_content_type
            ps.setString(++field, stateSerializer.contentType());

            // DB schema has DEFAULT CURRENT_TIMESTAMP for saved_at.
            // If checkpoint provides a specific time, use it. Otherwise, use current time from Java.
            // To use DB default, one would typically omit the column or pass NULL if the column definition allows it to trigger default.
            // OffsetDateTime savedAt = checkpoint.getSavedAt().orElse(OffsetDateTime.now());
            // psCheckpoint.setObject(8, savedAt);
            log.trace("Executing insert checkpoint:\n---\n{}---", insertCheckpointSql);
            ps.executeUpdate();
        }

    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        Connection conn = null;
        try (Connection ignored = conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            conn.commit();
            log.debug("Checkpoint {} for thread {} inserted successfully.", checkpoint.getId(), threadId);

        } catch (SQLException | IOException e) { // IOException from convertStateToJson
            log.error("Error inserting checkpoint with id {} in thread {}", checkpoint.getId(), threadId, e);
            rollback(conn, checkpoint, threadId);
            throw e;
        }

    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {

        final var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var deletePreviousCheckpointSql = sqlCommands.get("sqlDeletePreviousCheckpoint");

        Connection conn = null;

        try (Connection ignored = conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

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

            conn.commit();

            log.debug("Checkpoint with id {} for thread {} inserted successfully.",
                    checkpoint.getId(),
                    threadId);

        } catch (SQLException | IOException e) { // IOException from convertStateToJson
            log.error("Error inserting checkpoint with id {} in thread {}",
                    checkpoint.getId(),
                    threadId,
                    e);
            rollback(conn, checkpoint, threadId);
            throw e;
        }
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var threadId = threadId(config);

        var selectThreadSql = sqlCommands.get("sqlSelectThread");
        var releaseThreadSql = sqlCommands.get("sqlReleaseThread");
        try (Connection conn = getConnection()) {

            UUID threadUUID = null;
            try (PreparedStatement ps = conn.prepareStatement(selectThreadSql)) {
                var field = 0;
                ps.setString(++field, threadId);

                try (ResultSet rs = ps.executeQuery()) {
                    var rows = 0;
                    while (rs.next()) {
                        threadUUID = rs.getObject("thread_id", UUID.class);
                        ++rows;
                    }
                    if (rows == 0) {
                        throw new IllegalStateException(format("active Thread '%s' not found", threadId));
                    }
                    if (rows > 1) {
                        throw new IllegalStateException(format("duplicate active Thread '%s' found", threadId));
                    }
                }
            }

            log.trace("Executing release Thread:\n---\n{}---", releaseThreadSql);
            try (PreparedStatement ps = conn.prepareStatement(releaseThreadSql)) {
                var field = 0;
                ps.setObject(++field,
                        Objects.requireNonNull(threadUUID, "threadUUID cannot be null"),
                        Types.OTHER); // nullable
                ps.executeUpdate();

            }
        }

        return new Tag(threadId, checkpoints);
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
     * Datasource connection
     * Creates the vector extension and add the vector type if it does not exist.
     * Could be overridden in case extension creation and adding type is done at datasource initialization step.
     *
     * @return Datasource connection
     * @throws SQLException exception
     */
    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

    protected final <R> R execTransaction(TryFunction<Connection, R, Exception> execStatement) throws Exception {
        try (Connection connection = getConnection()) {
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
