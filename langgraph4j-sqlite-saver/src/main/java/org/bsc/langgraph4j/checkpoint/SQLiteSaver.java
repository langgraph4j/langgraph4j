package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class SQLiteSaver extends AbstractCheckpointSaver {
    private static final Logger log = LoggerFactory.getLogger(SQLiteSaver.class);

    public static class Builder {
        public StateSerializer<? extends AgentState> stateSerializer;
        private String url;
        private String databasePath;
        private boolean createTables;
        private boolean dropTablesFirst;
        private DataSource datasource;
        private boolean plainTextStateSerializerLegacyMode = false;

        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Intended to enable compatibility mode for {@code PlainTextStateSerializer}-based state payloads.
         * The legacy mode saves the JSON payload as binary format (i.e. a serialized java String).
         * If state serializer is not a PlainTextStateSerializer implementation this flag is ignored.
         *
         * @param mode compatibility flag value (default is false)
         * @return this builder
         */
        public Builder plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder databasePath(String databasePath) {
            this.databasePath = databasePath;
            return this;
        }

        public Builder datasource(DataSource datasource) {
            this.datasource = datasource;
            return this;
        }

        public Builder createTables(boolean createTables) {
            this.createTables = createTables;
            return this;
        }

        public Builder dropTablesFirst(boolean dropTablesFirst) {
            this.dropTablesFirst = dropTablesFirst;
            return this;
        }

        private String requireNotBlank(String value, String name) {
            if (requireNonNull(value, format("'%s' cannot be null", name)).isBlank()) {
                throw new IllegalArgumentException(format("'%s' cannot be blank", name));
            }
            return value;
        }

        public SQLiteSaver build() throws SQLException {
            requireNonNull(stateSerializer, "stateSerializer cannot be null");

            if (datasource == null) {
                var ds = new SQLiteDataSource();
                if (url != null) {
                    ds.setUrl(requireNotBlank(url, "url"));
                }
                else {
                    ds.setUrl("jdbc:sqlite:" + requireNotBlank(databasePath, "databasePath"));
                }
                datasource = ds;
            }

            createTables = createTables || dropTablesFirst;
            return new SQLiteSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected final DataSource datasource;
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final boolean plainTextStateSerializerLegacyMode;

    protected SQLiteSaver(Builder builder) throws SQLException {
        this.datasource = builder.datasource;
        this.stateSerializer = builder.stateSerializer;
        this.plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;

        initTable(builder.dropTablesFirst, builder.createTables);
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
        }
        else {
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

    protected void initTable(boolean dropTablesFirst, boolean createTables) throws SQLException {
        var sqlDropTables = """
                DROP TABLE IF EXISTS LG4JCheckpoint;
                DROP TABLE IF EXISTS LG4JThread;
                """;

        var sqlCreateTables = """
                CREATE TABLE IF NOT EXISTS LG4JThread (
                    thread_id TEXT PRIMARY KEY,
                    thread_name TEXT,
                    is_released INTEGER DEFAULT 0 NOT NULL CHECK (is_released IN (0, 1))
                );

                CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    checkpoint_id TEXT NOT NULL UNIQUE,
                    parent_checkpoint_id TEXT,
                    thread_id TEXT NOT NULL,
                    node_id TEXT,
                    next_node_id TEXT,
                    state_data TEXT NOT NULL,
                    state_content_type TEXT NOT NULL,
                    saved_at TEXT DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

                    CONSTRAINT fk_thread
                        FOREIGN KEY(thread_id)
                        REFERENCES LG4JThread(thread_id)
                        ON DELETE CASCADE
                );

                CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
                CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC, id DESC);
                CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased
                    ON LG4JThread(thread_name)
                    WHERE is_released = 0;
                """;

        String sqlCommand = null;
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTablesFirst) {
                log.trace("Executing drop tables:\n---\n{}---", sqlDropTables);
                sqlCommand = sqlDropTables;
                executeSqlStatements(statement, sqlCommand);
            }
            if (createTables) {
                log.trace("Executing create tables:\n---\n{}---", sqlCreateTables);
                sqlCommand = sqlCreateTables;
                executeSqlStatements(statement, sqlCommand);
            }
        }
        catch (SQLException ex) {
            log.error("error executing command\n{}\n", sqlCommand, ex);
            throw ex;
        }
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

        final var sqlCheckThread = """
                SELECT COUNT(*)
                FROM LG4JThread
                WHERE thread_name = ? AND is_released = 0
                """;
        final var sqlQueryCheckpoints = """
                WITH matched_thread AS (
                    SELECT thread_id
                    FROM LG4JThread
                    WHERE thread_name = ? AND is_released = 0
                )
                SELECT  c.checkpoint_id,
                        c.node_id,
                        c.next_node_id,
                        c.state_data,
                        c.state_content_type,
                        c.parent_checkpoint_id
                FROM matched_thread t
                JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
                ORDER BY c.saved_at DESC, c.id DESC
                """;

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
                            .state(decodeState(rs.getString(4), rs.getString(5)))
                            .build();
                    checkpoints.add(checkpoint);
                }
            }
        }

        return checkpoints;
    }

    private void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var upsertThreadSql = """
                INSERT OR IGNORE INTO LG4JThread (thread_id, thread_name, is_released)
                VALUES (?, ?, 0)
                """;

        var selectThreadSql = """
                SELECT thread_id
                FROM LG4JThread
                WHERE thread_name = ? AND is_released = 0
                LIMIT 1
                """;

        var insertCheckpointSql = """
                INSERT INTO LG4JCheckpoint(
                    checkpoint_id,
                    parent_checkpoint_id,
                    thread_id,
                    node_id,
                    next_node_id,
                    state_data,
                    state_content_type)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setString(2, threadId);

            log.trace("Executing upsert thread:\n---\n{}---", upsertThreadSql);
            ps.executeUpdate();
        }

        String threadUUID = null;
        try (PreparedStatement ps = conn.prepareStatement(selectThreadSql)) {
            ps.setString(1, threadId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    threadUUID = rs.getString("thread_id");
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(insertCheckpointSql)) {
            var field = 0;
            ps.setString(++field, checkpoint.getId());
            ps.setString(++field, null);
            ps.setString(++field, requireNonNull(threadUUID, "threadUUID cannot be null"));
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
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        Connection conn = null;
        try (Connection ignored = conn = getConnection()) {
            conn.setAutoCommit(false);

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            conn.commit();
            log.debug("Checkpoint {} for thread {} inserted successfully.", checkpoint.getId(), threadId);
        }
        catch (SQLException | IOException e) {
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

        var deletePreviousCheckpointSql = """
                DELETE FROM LG4JCheckpoint
                WHERE checkpoint_id = ?;
                """;

        Connection conn = null;

        try (Connection ignored = conn = getConnection()) {
            conn.setAutoCommit(false);

            if (config.checkPointId().isPresent()) {
                try (PreparedStatement ps = conn.prepareStatement(deletePreviousCheckpointSql)) {
                    ps.setString(1, config.checkPointId().get());
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
        }
        catch (SQLException | IOException e) {
            log.error("Error inserting checkpoint with id {} in thread {}",
                    checkpoint.getId(),
                    threadId,
                    e);
            rollback(conn, checkpoint, threadId);
            throw e;
        }
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final var threadId = threadId(config);

        var selectThreadSql = """
                SELECT thread_id FROM LG4JThread
                WHERE thread_name = ? AND is_released = 0
                """;
        var releaseThreadSql = """
                UPDATE LG4JThread
                SET
                    is_released = 1
                WHERE thread_id = ?;
                """;
        try (Connection conn = getConnection()) {
            String threadUUID = null;
            try (PreparedStatement ps = conn.prepareStatement(selectThreadSql)) {
                ps.setString(1, threadId);

                try (ResultSet rs = ps.executeQuery()) {
                    var rows = 0;
                    while (rs.next()) {
                        threadUUID = rs.getString("thread_id");
                        ++rows;
                    }
                    if (rows == 0) {
                        throw new IllegalStateException( format("active Thread '%s' not found",threadId) );
                    }
                    if (rows > 1) {
                        throw new IllegalStateException(format("duplicate active Thread '%s' found", threadId));
                    }
                }
            }

            log.trace("Executing release Thread:\n---\n{}---", releaseThreadSql);
            try (PreparedStatement ps = conn.prepareStatement(releaseThreadSql)) {
                ps.setString(1, Objects.requireNonNull(threadUUID, "threadUUID cannot be null"));
                ps.executeUpdate();
            }
        }

        return new Tag(threadId, checkpoints);
    }

    protected Connection getConnection() throws SQLException {
        var connection = datasource.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

}
