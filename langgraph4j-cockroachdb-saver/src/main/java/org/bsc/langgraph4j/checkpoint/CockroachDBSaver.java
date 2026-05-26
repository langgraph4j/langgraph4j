package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Checkpoint saver backed by CockroachDB.
 *
 * <p>CockroachDB speaks the PostgreSQL wire protocol, so this saver uses the
 * standard {@code org.postgresql.Driver} and the same DDL that
 * {@link PostgresSaver} uses (two tables: {@code LG4JThread} and
 * {@code LG4JCheckpoint}, state serialized to bytes and base64-wrapped in a
 * JSONB column). Cross-database migration to or from a PostgreSQL-backed
 * checkpoint store is therefore straightforward.
 *
 * <p>Every SQL construct in this class is supported on CockroachDB v22.1 and
 * later: {@code JSONB}, {@code UUID}, partial unique indexes with
 * {@code WHERE}, {@code ON CONFLICT ... WHERE ... DO NOTHING}, and
 * {@code ON DELETE CASCADE}.
 *
 * <p>Build with the {@link Builder} returned by {@link #builder()}:
 *
 * <pre>{@code
 * CockroachDBSaver saver = CockroachDBSaver.builder()
 *         .host("localhost")
 *         .port(26257)
 *         .database("defaultdb")
 *         .user("root")
 *         .password("")
 *         .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
 *         .createTables(true)
 *         .build();
 * }</pre>
 */
public class CockroachDBSaver extends AbstractCheckpointSaver {
    private static final Logger log = LoggerFactory.getLogger(CockroachDBSaver.class);

    /** Default CockroachDB SQL port. */
    public static final int DEFAULT_PORT = 26257;

    public static class Builder {
        public StateSerializer<? extends AgentState> stateSerializer;
        private String host;
        private Integer port = DEFAULT_PORT;
        private String user;
        private String password;
        private String database;
        private boolean createTables;
        private boolean dropTablesFirst;
        private DataSource datasource;
        private boolean plainTextStateSerializerLegacyMode = false;

        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Compatibility flag for {@link PlainTextStateSerializer}-based payloads
         * persisted by an older version of this saver, which wrote the JSON payload
         * as a serialized Java {@code String}. Ignored unless the configured state
         * serializer is a {@link PlainTextStateSerializer} implementation.
         *
         * @param mode compatibility flag value (default is false)
         */
        public Builder plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
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

        public CockroachDBSaver build() throws SQLException {
            requireNonNull(stateSerializer, "stateSerializer cannot be null");

            if (datasource == null) {
                if (port == null || port <= 0) {
                    throw new IllegalArgumentException("port must be greater than 0");
                }
                var ds = new PGSimpleDataSource();
                ds.setDatabaseName(requireNotBlank(database, "database"));
                ds.setUser(requireNotBlank(user, "user"));
                ds.setPassword(requireNonNull(password, "password cannot be null"));
                ds.setPortNumbers(new int[] {port});
                ds.setServerNames(new String[] {requireNotBlank(host, "host")});
                datasource = ds;
            }

            createTables = createTables || dropTablesFirst;
            return new CockroachDBSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Datasource used to create the store. */
    protected final DataSource datasource;
    private final StateSerializer<? extends AgentState> stateSerializer;
    private final boolean plainTextStateSerializerLegacyMode;

    protected CockroachDBSaver(Builder builder) throws SQLException {
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
            log.error(
                    "Failed to rollback transaction for checkpoint id {} in thread {}",
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

    private Map<String, Object> decodeState(byte[] binaryPayload, String contentType)
            throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(format(
                    "Content Type used for store state '%s' is different from one '%s' used for deserialize it",
                    contentType, stateSerializer.contentType()));
        }

        final byte[] bytes = Base64.getDecoder().decode(binaryPayload);

        if (plainTextStateSerializerLegacyMode && stateSerializer instanceof PlainTextStateSerializer<?> ser) {
            return ser.readDataFromString(new String(bytes, StandardCharsets.UTF_8));
        }
        return stateSerializer.dataFromBytes(bytes);
    }

    protected void initTable(boolean dropTablesFirst, boolean createTables) throws SQLException {
        var sqlDropTables = """
                DROP TABLE IF EXISTS LG4JCheckpoint CASCADE;
                DROP TABLE IF EXISTS LG4JThread CASCADE;
                """;

        var sqlCreateTables = """
                CREATE TABLE IF NOT EXISTS LG4JThread (
                     thread_id UUID PRIMARY KEY,
                     thread_name VARCHAR(255),
                     is_released BOOLEAN DEFAULT FALSE NOT NULL
                 );

                 CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
                     checkpoint_id UUID PRIMARY KEY,
                     parent_checkpoint_id UUID,
                     thread_id UUID NOT NULL,
                     node_id VARCHAR(255),
                     next_node_id VARCHAR(255),
                     state_data JSONB NOT NULL,
                     state_content_type VARCHAR(100) NOT NULL,
                     saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                     CONSTRAINT fk_thread
                         FOREIGN KEY(thread_id)
                         REFERENCES LG4JThread(thread_id)
                         ON DELETE CASCADE
                 );

                 CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
                 CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);
                 CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased ON LG4JThread(thread_name) WHERE is_released = FALSE;
                """;

        String sqlCommand = null;
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTablesFirst) {
                log.trace("Executing drop tables:\n---\n{}---", sqlDropTables);
                sqlCommand = sqlDropTables;
                statement.executeUpdate(sqlCommand);
            }
            if (createTables) {
                log.trace("Executing create tables:\n---\n{}---", sqlCreateTables);
                sqlCommand = sqlCreateTables;
                statement.executeUpdate(sqlCommand);
            }
        } catch (SQLException ex) {
            log.error("error executing command\n{}\n", sqlCommand, ex);
            throw ex;
        }
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();

        final var threadId = threadId(config);

        final var sqlCheckThread = """
                SELECT COUNT(*)
                FROM LG4JThread
                WHERE thread_name = ? AND is_released = FALSE
                """;
        final var sqlQueryCheckpoints = """
                WITH matched_thread AS (
                    SELECT thread_id
                    FROM LG4JThread
                    WHERE thread_name = ? AND is_released = FALSE
                )
                SELECT  c.checkpoint_id,
                        c.node_id,
                        c.next_node_id,
                        c.state_data->>'binaryPayload' AS base64_data,
                        c.state_content_type,
                        c.parent_checkpoint_id
                FROM matched_thread t
                JOIN LG4JCheckpoint c ON c.thread_id = t.thread_id
                ORDER BY c.saved_at DESC
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
                    throw new IllegalStateException(
                            format("there are more than one Thread '%s' open (not released yet)", threadId));
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

    private void insertCheckpoint(
            Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var upsertThreadSql = """
                WITH inserted AS (
                    INSERT INTO LG4JThread (thread_id, thread_name, is_released)
                    VALUES (?, ?, FALSE)
                    ON CONFLICT (thread_name)
                    WHERE is_released = FALSE
                    DO NOTHING
                    RETURNING thread_id
                )
                SELECT thread_id FROM inserted
                UNION ALL
                SELECT thread_id FROM LG4JThread
                WHERE thread_name = ? AND is_released = FALSE
                LIMIT 1;
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
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        UUID threadUUID = null;

        // 1. Upsert thread information
        try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
            var field = 0;
            ps.setObject(++field, UUID.randomUUID(), Types.OTHER);
            ps.setString(++field, threadId);
            ps.setString(++field, threadId);

            log.trace("Executing upsert thread:\n---\n{}---", upsertThreadSql);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    threadUUID = rs.getObject("thread_id", UUID.class);
                }
            }
        }

        // 2. Insert checkpoint data
        try (PreparedStatement ps = conn.prepareStatement(insertCheckpointSql)) {
            var field = 0;
            ps.setObject(++field, UUID.fromString(checkpoint.getId()), Types.OTHER);
            ps.setNull(++field, Types.OTHER);
            ps.setObject(++field, requireNonNull(threadUUID, "threadUUID cannot be null"), Types.OTHER);
            ps.setString(++field, checkpoint.getNodeId());
            ps.setString(++field, checkpoint.getNextNodeId());
            ps.setString(++field, encodeState(checkpoint.getState()));
            ps.setString(++field, stateSerializer.contentType());

            log.trace("Executing insert checkpoint:\n---\n{}---", insertCheckpointSql);
            ps.executeUpdate();
        }
    }

    @Override
    protected void insertedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        Connection conn = null;
        try (Connection ignored = conn = getConnection()) {
            conn.setAutoCommit(false);

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            conn.commit();
            log.debug("Checkpoint {} for thread {} inserted successfully.", checkpoint.getId(), threadId);

        } catch (SQLException | IOException e) {
            log.error("Error inserting checkpoint with id {} in thread {}", checkpoint.getId(), threadId, e);
            rollback(conn, checkpoint, threadId);
            throw e;
        }
    }

    @Override
    protected void updatedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

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
                    var field = 0;
                    ps.setObject(++field, UUID.fromString(config.checkPointId().get()), Types.OTHER);
                    log.trace(
                            "Executing deleting previous checkpoint with id {} in thread {}:\n---\n{}---",
                            config.checkPointId().get(),
                            threadId,
                            deletePreviousCheckpointSql);
                    ps.executeUpdate();
                }
            }

            insertCheckpoint(conn, config, checkpoints, checkpoint);

            conn.commit();

            log.debug("Checkpoint with id {} for thread {} inserted successfully.", checkpoint.getId(), threadId);

        } catch (SQLException | IOException e) {
            log.error("Error inserting checkpoint with id {} in thread {}", checkpoint.getId(), threadId, e);
            rollback(conn, checkpoint, threadId);
            throw e;
        }
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final var threadId = threadId(config);

        var selectThreadSql = """
                SELECT thread_id FROM LG4JThread
                WHERE thread_name = ? AND is_released = FALSE
                """;
        var releaseThreadSql = """
                UPDATE LG4JThread
                SET
                    is_released = TRUE
                WHERE thread_id = ?;
                """;
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
                ps.setObject(++field, Objects.requireNonNull(threadUUID, "threadUUID cannot be null"), Types.OTHER);
                ps.executeUpdate();
            }
        }

        return new Tag(threadId, checkpoints);
    }

    /**
     * Obtain a connection from the configured {@link DataSource}.
     * Override this method if your {@code DataSource} requires custom session
     * setup beyond what {@code build()} already configured.
     *
     * @return a Connection from the pool
     * @throws SQLException if the underlying pool refuses to lend a connection
     */
    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

    /**
     * No-op kept for source compatibility with the PostgreSQL saver. This
     * implementation has no in-memory checkpoint cache.
     *
     * @param threadId the thread identifier
     * @return an empty collection
     * @deprecated this method does nothing
     */
    @Deprecated(forRemoval = true)
    public Collection<Checkpoint> clearCheckpointsCache(String threadId) {
        return List.of();
    }
}
