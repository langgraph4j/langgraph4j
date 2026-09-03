package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PostgresSaverV2Dashboard extends AbstractPostgresSaverV2 {

    public static class Builder extends AbstractBuilder<PostgresSaverV2Dashboard.Builder> {

        public PostgresSaverV2Dashboard build() throws Exception {
            return new PostgresSaverV2Dashboard(this);
        }
    }

    public static PostgresSaverV2Dashboard.Builder builder() {
        return new PostgresSaverV2Dashboard.Builder();
    }

    public record ThreadRecord(
            long id,
            String name,
            boolean isInterrupted,
            String message,
            String createdAt) {
    }

    public record TagRecord(
            long id,
            String name,
            int version,
            long parentId,
            boolean isReleased,
            boolean isError,
            String message,
            String createdAt
    ) {
    }

    public PostgresSaverV2Dashboard(PostgresSaverV2Dashboard.Builder builder) throws Exception {
        super(builder);
    }

    @Override
    protected String sqlCommandsResourcePath() {
        return "db/v2.0__commands.sql";
    }

    public List<ThreadRecord> selectAllThreads() throws Exception {
        final var sqlSelectAllThreads = sqlCommands.get("sqlSelectAllThreads");

        return execTransaction(conn -> {
            final List<ThreadRecord> result = new LinkedList<>();
            try (var ps = conn.prepareStatement(sqlSelectAllThreads)) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ThreadRecord(
                                rs.getLong("thread_id"),
                                rs.getString("thread_name"),
                                rs.getBoolean("is_interrupted"),
                                rs.getString("message"),
                                rs.getString("created_at")));
                    }
                }
            }
            return result;
        });
    }

    public List<TagRecord> selectAllTags() throws Exception {
        final var sqlSelectAllTags = sqlCommands.get("sqlSelectAllTags");

        return execTransaction(conn -> {
            final List<TagRecord> result = new LinkedList<>();
            try (var ps = conn.prepareStatement(sqlSelectAllTags)) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new TagRecord(
                                rs.getLong("thread_id"),
                                rs.getString("thread_name"),
                                rs.getInt("released_version"),
                                rs.getLong("parent_thread_id"),
                                rs.getBoolean("is_released"),
                                rs.getBoolean("is_error"),
                                rs.getString("message"),
                                rs.getString("created_at")));
                    }
                }
            }
            return result;
        });
    }

    @Override
    protected String sqlInitResourcePath() {
        return null;
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        return null;
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
        return null;
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return null;
    }

    @Override
    protected void initTable(boolean dropTablesFirst, boolean createTables) throws Exception {
    }

    @Override
    protected void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
    }
}
