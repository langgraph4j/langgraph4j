package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SQLiteSaverV2Dashboard extends AbstractSQLiteSaverV2 {

    public static class Builder extends AbstractBuilder<SQLiteSaverV2Dashboard.Builder> {

        public SQLiteSaverV2Dashboard build() throws Exception {
            return new SQLiteSaverV2Dashboard(this);
        }

    }

    public static SQLiteSaverV2Dashboard.Builder builder() {
        return new SQLiteSaverV2Dashboard.Builder();
    }

    public record ThreadRecord(
            int id,
            String name,
            boolean isInterrupted,
            String message,
            String createdAt) {
    }

    public record TagRecord (
            int id,
            String name,
            int version,
            int parentId,
            boolean isReleased,
            boolean isError,
            String message,
            String createdAt
            ) {
    }

    public SQLiteSaverV2Dashboard(SQLiteSaverV2Dashboard.Builder builder) throws Exception {
        super(builder);
    }

    @Override
    protected String sqlCommandsResourcePath() {
        return "db/v2.0__commands.sql";
    }

    public List<ThreadRecord> selectAllThreads() throws Exception {
        final var sqlSelectAllThreads = sqlCommands.get("sqlSelectAllThreads");

        return exec(conn -> {
            final List<ThreadRecord> result = new LinkedList<>();
            try (var ps = conn.prepareStatement(sqlSelectAllThreads)) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {

                        result.add(new ThreadRecord(
                                rs.getInt("thread_id"),
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

        return exec(conn -> {
            final List<TagRecord> result = new LinkedList<>();
            try (var ps = conn.prepareStatement(sqlSelectAllTags)) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {

                        result.add(new TagRecord(
                                rs.getInt("thread_id"),
                                rs.getString("thread_name"),
                                rs.getInt("released_version"),
                                rs.getInt("parent_thread_id"),
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

}
