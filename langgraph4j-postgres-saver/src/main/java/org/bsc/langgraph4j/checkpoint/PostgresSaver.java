package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class PostgresSaver extends MemorySaver {
    private static final Logger log = LoggerFactory.getLogger(PostgresSaver.class);
    /**
     * Datasource used to create the store
     */
    protected final DataSource datasource;

    private final StateSerializer<? extends AgentState> stateSerializer;

    protected PostgresSaver( Builder builder ) throws SQLException {
        this.datasource = builder.datasource;
        this.stateSerializer =  builder.stateSerializer;
        initTable( builder.dropTablesFirst, builder.createTables);
    }

    public static Builder builder() {
        return new Builder();
    }

    private String encodeState( Map<String,Object> data ) throws IOException {
        var binaryData = stateSerializer.dataToBytes(data);
        var base64Data = Base64.getEncoder().encodeToString(binaryData);
        return format("""
                     {"binaryPayload": "%s"}
                     """, base64Data);
    }

    private Map<String,Object> decodeState( byte[] binaryPayload, String contentType ) throws IOException, ClassNotFoundException {
        if( !Objects.equals(contentType, stateSerializer.contentType() )) {
            throw new IllegalStateException(
                    format( "Content Type used for store state '%s' is different from one '%s' used for deserialize it",
                            contentType,
                            stateSerializer.contentType() ));
        }

        byte[] bytes = Base64.getDecoder().decode(binaryPayload);
        return stateSerializer.dataFromBytes( bytes );
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
                     state_content_type VARCHAR(100) NOT NULL, -- New field for content type
                     saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                
                     CONSTRAINT fk_thread
                         FOREIGN KEY(thread_id)
                         REFERENCES LG4JThread(thread_id)
                         ON DELETE CASCADE
                 );
                
                 CREATE INDEX idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
                 CREATE INDEX idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);
                 CREATE UNIQUE INDEX idx_unique_lg4jthread_thread_name_unreleased  ON LG4JThread(thread_name) WHERE is_released = FALSE;
                """;


        String sqlCommand = null;
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTablesFirst) {
                log.trace( "Executing drop tables:\n---\n{}---", sqlDropTables);
                sqlCommand = sqlDropTables;
                statement.executeUpdate(sqlCommand);
            }
            if (createTables) {
                log.trace( "Executing create tables:\n---\n{}---", sqlCreateTables);
                sqlCommand = sqlCreateTables;
                statement.executeUpdate(sqlCommand);
            }
        }
        catch ( SQLException ex ) {
            log.error( "error executing command\n{}\n", sqlCommand, ex );
            throw ex;
        }
    }

    @Override
    protected LinkedList<Checkpoint> loadedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {

        if( !checkpoints.isEmpty() ) return checkpoints;

        var threadId = config.threadId().orElse( THREAD_ID_DEFAULT );

        var sqlCheckThread = """
                SELECT COUNT(*)
                FROM LG4JThread
                WHERE thread_name = ? AND is_released = FALSE
                """;
        var sqlQueryCheckpoints = """
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
        try( Connection conn = getConnection() ) {

            try( PreparedStatement ps = conn.prepareStatement(sqlCheckThread) ) {
                ps.setString(1, threadId);
                var resultSet = ps.executeQuery();
                resultSet.next();
                var count = resultSet.getInt(1);

                if( count == 0 ) {
                    return checkpoints;
                }
                if( count > 1 ) {
                    throw new IllegalStateException( format("there are more than one Thread '%s' open (not released yet)", threadId));
                }
            }

            log.trace( "Executing select checkpoints:\n---\n{}---", sqlQueryCheckpoints);
            try( PreparedStatement ps = conn.prepareStatement(sqlQueryCheckpoints) ) {
                ps.setString(1, threadId);
                var rs = ps.executeQuery();
                while( rs.next() ) {
                    var checkpoint = Checkpoint.builder()
                            .id( rs.getString(1) )
                            .nodeId( rs.getString(2) )
                            .nextNodeId( rs.getString(3) )
                            .state( decodeState( rs.getBytes(4), rs.getString( 5) ) )
                            .build();
                    checkpoints.add( checkpoint );
                }
            }

        }

        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint( RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        var threadId = config.threadId().orElse( THREAD_ID_DEFAULT );

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

        Connection conn = null;
        try( Connection ignored = conn = getConnection() )  {
            conn.setAutoCommit(false); // Start transaction

            UUID threadUUID = null;

            // 1. Upsert thread information
            try (PreparedStatement ps = conn.prepareStatement(upsertThreadSql)) {
                var field = 0;
                ps.setObject(++field, UUID.randomUUID(), Types.OTHER);
                ps.setString(++field, threadId);
                ps.setString(++field, threadId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        threadUUID = rs.getObject("thread_id", UUID.class);
                    }
                }
            }

            log.trace( "Executing insert checkpoint:\n---\n{}---", insertCheckpointSql);
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
                ps.setObject(++field,
                        requireNonNull(threadUUID, "threadUUID cannot be null"),
                        Types.OTHER);
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

                ps.executeUpdate();
            }

            conn.commit();
            log.debug("Checkpoint {} for thread {} inserted successfully.", checkpoint.getId(), threadId);

        } catch (SQLException | IOException e) { // IOException from convertStateToJson
            log.error("Error inserting checkpoint with id {}: {}", checkpoint.getId(), e.getMessage(), e);
            if (conn != null) {
                try {
                    conn.rollback();
                    log.warn("Transaction rolled back for checkpoint {}", checkpoint.getId());
                } catch (SQLException exRollback) {
                    log.error("Failed to rollback transaction for checkpoint id {}: {}", checkpoint.getId(), exRollback.getMessage(), exRollback);
                }
            }
            throw e;
        }

    }

    @Override
    protected void updatedCheckpoint( RunnableConfig config,
                                      LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) throws Exception {

        var updateCheckpointSql = """
                UPDATE LG4JCheckpoint
                SET
                    node_id = ?,
                    next_node_id = ?,
                    state_data = ?,
                WHERE checkpoint_id = ?;
                """;

        log.trace( "Executing update checkpoints:\n---\n{}---", updateCheckpointSql);
        try( Connection conn = getConnection() ; PreparedStatement ps = conn.prepareStatement(updateCheckpointSql) )  {
            var field = 0;
            // node_id
            ps.setString(++field, checkpoint.getNodeId());
            // next_node_id
            ps.setString(++field, checkpoint.getNextNodeId());
            // state_data
            ps.setString(++field, encodeState(checkpoint.getState()));

            ps.setObject(++field,
                    UUID.fromString(checkpoint.getId()),
                    Types.OTHER); // nullable
            ps.executeUpdate();
        }
    }

    @Override
    protected void releasedCheckpoints( RunnableConfig config, LinkedList<Checkpoint> checkpoints, Tag releaseTag) throws Exception {
        var threadId = config.threadId().orElse( THREAD_ID_DEFAULT );

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
        try( Connection conn = getConnection()  )  {

            UUID threadUUID = null;
            try (PreparedStatement ps = conn.prepareStatement(selectThreadSql)) {
                var field = 0;
                ps.setString(++field, threadId);

                try (ResultSet rs = ps.executeQuery()) {
                    var rows = 0;
                    while( rs.next() ) {
                        threadUUID = rs.getObject("thread_id", UUID.class);
                        ++rows;
                    }
                    if( rows == 0 ) {
                        throw new IllegalStateException( format("active Thread '%s' not found",threadId) );
                    }
                    if( rows > 1 ) {
                        throw new IllegalStateException( format("duplicate active Thread '%s' found",threadId) );
                    }
                }
            }

            log.trace( "Executing release Thread:\n---\n{}---", releaseThreadSql);
            try (PreparedStatement ps = conn.prepareStatement(releaseThreadSql)) {
                var field = 0;
                ps.setObject(++field,
                        Objects.requireNonNull(threadUUID,"threadUUID cannot be null"),
                        Types.OTHER); // nullable
                ps.executeUpdate();

            }
        }

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

    public static class Builder {
        public StateSerializer<? extends AgentState> stateSerializer;
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String database;
        private boolean createTables;
        private boolean dropTablesFirst;
        private DataSource datasource;

        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
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

        public Builder createTables(boolean createTables) {
            this.createTables = createTables;
            return this;
        }

        public Builder dropTablesFirst(boolean dropTablesFirst) {
            this.dropTablesFirst = dropTablesFirst;
            return this;
        }

        private String requireNotBlank( String value, String name ) {
            if( requireNonNull(value, format("'%s' cannot be null", name) ).isBlank() ) {
                throw new IllegalArgumentException(format("'%s' cannot be blank", name));
            }
            return value;
        }

        public PostgresSaver build() throws SQLException {
            requireNonNull( stateSerializer, "stateSerializer cannot be null");
            if( port <=0 ) {
                throw new IllegalArgumentException("port must be greater than 0");
            }
            var ds = new PGSimpleDataSource();
            ds.setDatabaseName( requireNotBlank(database, "database"));
            ds.setUser(requireNotBlank(user, "user"));
            ds.setPassword(requireNonNull(password, "password cannot be null"));
            ds.setPortNumbers( new int[] {port} );
            ds.setServerNames( new String[] { requireNotBlank(host, "host") } );

            datasource = ds;
            createTables = createTables || dropTablesFirst;

            return new PostgresSaver( this );
        }
    }
}

