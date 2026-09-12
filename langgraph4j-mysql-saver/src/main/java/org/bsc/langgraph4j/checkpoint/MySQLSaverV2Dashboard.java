package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MySQLSaverV2Dashboard extends AbstractMySQLSaverV2 {

    public static class Builder extends AbstractBuilder<Builder> {

        public MySQLSaverV2Dashboard build() throws Exception {
            return new MySQLSaverV2Dashboard(this);
        }

    }

    public static Builder builder() {
        return new Builder();
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

    public MySQLSaverV2Dashboard(Builder builder) throws Exception {
        super(builder);
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
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        return null;
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
        return null;
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return null;
    }

    @Override
    protected void initTables(CreateOption createOption) throws Exception {
    }

}
