package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.SqlResource;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Common base for checkpoint savers backed by CockroachDB.
 *
 * <p>The base owns datasource construction, state serialization, and schema
 * initialization. Concrete savers provide the versioned SQL resources and
 * implement their persistence-specific operations.
 */
public abstract class AbstractCockroachDBSaver extends AbstractCheckpointSaver implements LG4JLoggable {

    protected static class AbstractBuilder<B extends AbstractBuilder<B>> {
        public StateSerializer<? extends AgentState> stateSerializer;
        private String host;
        private Integer port = CockroachDBSaver.DEFAULT_PORT;
        private String user;
        private String password;
        private String database;
        private boolean createTables;
        private boolean dropTablesFirst;
        private DataSource datasource;
        private boolean plainTextStateSerializerLegacyMode;

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }

        public <State extends AgentState> B stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return self();
        }

        public B plainTextStateSerializerLegacyMode(boolean mode) {
            this.plainTextStateSerializerLegacyMode = mode;
            return self();
        }

        public B host(String host) { this.host = host; return self(); }
        public B port(Integer port) { this.port = port; return self(); }
        public B user(String user) { this.user = user; return self(); }
        public B password(String password) { this.password = password; return self(); }
        public B database(String database) { this.database = database; return self(); }
        public B datasource(DataSource datasource) { this.datasource = datasource; return self(); }
        public B createTables(boolean createTables) { this.createTables = createTables; return self(); }
        public B dropTablesFirst(boolean dropTablesFirst) { this.dropTablesFirst = dropTablesFirst; return self(); }

        private String requireNotBlank(String value, String name) {
            if (requireNonNull(value, format("'%s' cannot be null", name)).isBlank()) {
                throw new IllegalArgumentException(format("'%s' cannot be blank", name));
            }
            return value;
        }

        private void validate() {
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
        }
    }

    protected final DataSource datasource;
    protected final SqlResource.Commands sqlCommands;
    private final StateSerializer<? extends AgentState> stateSerializer;
    @SuppressWarnings("unused")
    private final boolean plainTextStateSerializerLegacyMode;

    protected AbstractCockroachDBSaver(AbstractBuilder<?> builder) throws SQLException {
        builder.validate();
        datasource = builder.datasource;
        stateSerializer = builder.stateSerializer;
        plainTextStateSerializerLegacyMode = builder.plainTextStateSerializerLegacyMode;
        sqlCommands = loadSqlCommands(sqlCommandsResourcePath());
        initTable(builder.dropTablesFirst, builder.createTables);
    }

    protected abstract String sqlCommandsResourcePath();
    protected abstract String sqlInitResourcePath();

    protected final StateSerializer<? extends AgentState> stateSerializer() {
        return stateSerializer;
    }

    protected String encodeState(Map<String, Object> data) throws IOException {
        return stateSerializer.writeDataAsString(data);
    }

    protected Map<String, Object> decodeState(String payload, String contentType)
            throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(format(
                    "Content Type used for store state '%s' is different from one '%s' used for deserialize it",
                    contentType, stateSerializer.contentType()));
        }
        return stateSerializer.readDataFromString(payload).data();
    }

    private static SqlResource.Commands loadSqlCommands(String resourcePath) throws SQLException {
        try {
            return SqlResource.Commands.load(resourcePath);
        } catch (Exception ex) {
            throw new SQLException("Unable to load SQL resource " + resourcePath, ex);
        }
    }

    protected void initTable(boolean dropTablesFirst, boolean createTables) throws SQLException {
        var sqlInitCommands = loadSqlCommands(sqlInitResourcePath());
        String sqlCommand = null;
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTablesFirst) {
                for (var sql : sqlCommands.getMultiple("sqlDropTables")) {
                    log.trace("Executing drop table:\n---\n{}---", sql);
                    sqlCommand = sql;
                    statement.execute(sql);
                }
            }
            if (createTables) {
                for (var sql : sqlInitCommands.getMultiple("sqlCreateTables")) {
                    log.trace("Executing create tables:\n---\n{}---", sql);
                    sqlCommand = sql;
                    statement.execute(sql);
                }
            }
        } catch (SQLException ex) {
            log.error("error executing command\n{}\n", sqlCommand, ex);
            throw ex;
        }
    }

    protected void rollback(Connection conn, Checkpoint checkpoint, String threadId) {
        if (conn == null) return;
        requireNonNull(checkpoint, "checkpoint cannot be null");
        try {
            conn.rollback();
            log.warn("Transaction rolled back for checkpoint {}", checkpoint.getId());
        } catch (SQLException exRollback) {
            log.error("Failed to rollback transaction for checkpoint id {} in thread {}",
                    checkpoint.getId(), threadId, exRollback);
        }
    }

    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }
}
