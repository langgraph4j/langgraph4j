package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public abstract class AbstractSQLiteSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    @FunctionalInterface
    public interface ExecStatement<R> {
        R apply(Connection connection) throws Exception;
    }

    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        public StateSerializer<? extends AgentState> stateSerializer;
        String url;
        String databasePath;
        boolean createTables;
        boolean dropTablesFirst;
        DataSource datasource;
        boolean plainTextStateSerializerLegacyMode = false;

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
         * The legacy mode saves the JSON payload as binary format (i.e. a serialized java String).
         * If state serializer is not a PlainTextStateSerializer implementation this flag is ignored.
         *
         * @param mode compatibility flag value (default is false)
         * @return this builder
         */
        public B plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return this$();
        }

        public B url(String url) {
            this.url = url;
            return this$();
        }

        public B databasePath(String databasePath) {
            this.databasePath = databasePath;
            return this$();
        }

        public B datasource(DataSource datasource) {
            this.datasource = datasource;
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


    }

    protected final DataSource datasource;
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final boolean plainTextStateSerializerLegacyMode;
    protected final SqlResource.Commands sqlCommands;

    protected AbstractSQLiteSaver(AbstractBuilder<?> builder) throws Exception {
        if (builder.datasource != null) {
            this.datasource = builder.datasource;
        } else {
            final var ds = new SQLiteDataSource();
            ofNullable(builder.url)
                    .ifPresentOrElse(
                            $1 -> ds.setUrl(requireNotBlank($1, "url")),
                            () -> ds.setUrl("jdbc:sqlite:".concat(requireNotBlank(builder.databasePath, "databasePath"))));

            this.datasource = ds;
        }

        this.stateSerializer = requireNonNull(builder.stateSerializer, "stateSerializer cannot be null");
        this.plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;

        sqlCommands = new SqlResource.Commands(sqlCommandsResourcePath());

        initTable(builder.dropTablesFirst, builder.createTables || builder.dropTablesFirst);
    }

    protected abstract String sqlCommandsResourcePath();

    protected abstract String sqlInitResourcePath();


    protected void initTable(boolean dropTablesFirst, boolean createTables) throws Exception {

        SqlResource.loadSql(sqlInitResourcePath(), sqlCreateTables ->
                execTransaction(conn -> {
                    String sqlCommand = null;
                    try (Statement statement = conn.createStatement()) {
                        if (dropTablesFirst) {
                            sqlCommand = sqlCommands.get("sqlDropTables");
                            log.trace("Executing drop tables:\n---\n{}---", sqlCommand);
                            executeSqlStatements(statement, sqlCommand);
                        }
                        if (createTables) {
                            log.trace("Executing create tables:\n---\n{}---", sqlCreateTables);
                            sqlCommand = sqlCreateTables;
                            executeSqlStatements(statement, sqlCommand);
                        }
                    }
                    return null;
                })
        );

    }

    protected String requireNotBlank(String value, String name) {
        if (requireNonNull(value, format("'%s' cannot be null", name)).isBlank()) {
            throw new IllegalArgumentException(format("'%s' cannot be blank", name));
        }
        return value;
    }

    private String encodeState(Map<String, Object> data) throws IOException {
        final byte[] binaryData;

        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            binaryData = ser.writeDataAsString(data).getBytes(StandardCharsets.UTF_8);
        } else {
            binaryData = stateSerializer.dataToBytes(data);
        }
        return Base64.getEncoder().encodeToString(binaryData);
    }

    private Map<String, Object> decodeState(String binaryPayload, String contentType) throws IOException, ClassNotFoundException {
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

    private void executeSqlStatements(Statement statement, String sqlStatements) throws SQLException {
        var statements = Stream.of(sqlStatements.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toList();

        for (var sql : statements) {
            statement.execute(sql);
        }
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var checkpoints = new LinkedList<Checkpoint>();
        final var threadId = threadId(config);

        final var sqlQueryCheckpoints = sqlCommands.get("sqlQueryCheckpoints");

        return exec(conn -> {

            log.trace("Executing select checkpoints:\n---\n{}---", sqlQueryCheckpoints);

            try (PreparedStatement ps = conn.prepareStatement(sqlQueryCheckpoints)) {
                ps.setString(1, threadId);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var checkpoint = Checkpoint.builder()
                            .id(rs.getString(1))
                            .nodeId(rs.getString(2))
                            .nextNodeId(rs.getString(3))
                            .state(decodeState(rs.getString(4), rs.getString(5)))
                            .build();
                    checkpoints.add(checkpoint);
                }
            }

            return checkpoints;
        });
    }

    private void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        final var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        final var upsertThreadSql = sqlCommands.get("sqlUpsertThread");

        final var insertCheckpointSql = sqlCommands.get("sqlInsertCheckpoint");

        long id = 0;
        try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
            ps.setString(1, threadId);

            log.trace("Executing upsert thread:\n---\n{}---", upsertThreadSql);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    id = rs.getLong("thread_id");
                }
                else {
                    throw new SQLException(
                            "No LG4JThread found for thread_id: %s".formatted(threadId) );
                }
            }

        }

        try (PreparedStatement ps = conn.prepareStatement(insertCheckpointSql)) {
            var field = 0;
            ps.setString(++field, checkpoint.getId());
            ps.setString(++field, null);
            ps.setLong(++field, id);
            ps.setString(++field, checkpoint.getNodeId());
            ps.setString(++field, checkpoint.getNextNodeId());
            ps.setString(++field, encodeState(checkpoint.getState()));
            ps.setString(++field, stateSerializer.contentType());

            log.trace("Executing insert checkpoint:\n---\n{}---", insertCheckpointSql);
            ps.executeUpdate();
        }
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        execTransaction(conn -> {
            insertCheckpoint(conn, config, checkpoints, checkpoint);
            return null;
        });
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {
        final var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        final var sqlDeletePreviousCheckpoint = sqlCommands.get("sqlDeletePreviousCheckpoint");

        execTransaction(conn -> {
            if (config.checkPointId().isPresent()) {
                try (PreparedStatement ps = conn.prepareStatement(sqlDeletePreviousCheckpoint)) {
                    ps.setString(1, config.checkPointId().get());
                    log.trace("Executing deleting previous checkpoint with id {} in thread {}:\n---\n{}---",
                            config.checkPointId().get(),
                            threadId,
                            sqlDeletePreviousCheckpoint);
                    int result = ps.executeUpdate();
                    if (result == 0) {
                        throw new SQLException(
                                "No LG4JCheckpoint found for checkpoint_id: %s in thread_id: %s".formatted(config.checkPointId().get(), threadId));
                    }
                    if (result > 1) {
                        throw new SQLException(
                                "Multiple LG4JCheckpoint found for checkpoint_id: %s in thread_id: %s".formatted(config.checkPointId().get(), threadId));
                    }
                }
            }

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            log.debug("Checkpoint with id {} for thread {} inserted successfully.",
                    checkpoint.getId(),
                    threadId);

            return null;
        });
    }


    protected final  <R> R exec(ExecStatement<R> execStatement) throws Exception {
        final var connection = datasource.getConnection();

        connection.setAutoCommit(true);
        try (var statement = connection.createStatement()) {
            statement.execute(sqlCommands.get("sqlEnableForeignKeys"));
        }

        return execStatement.apply(connection);
    }

    protected final <R> R execTransaction(ExecStatement<R> execStatement) throws Exception {
        final var connection = datasource.getConnection();

        final var previousAutoCommit = connection.getAutoCommit();

        connection.setAutoCommit(true);
        try (var statement = connection.createStatement()) {
            statement.execute(sqlCommands.get("sqlEnableForeignKeys"));
        }

        connection.setAutoCommit(false);
        try {
            return execStatement.apply(connection);
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
