package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class SQLiteSaver extends AbstractSQLiteSaver {

    public static class Builder extends AbstractBuilder<Builder> {

        public SQLiteSaver build() throws Exception {
            return new SQLiteSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected SQLiteSaver(Builder builder) throws Exception {
        super(builder);
    }

    @Override
    protected final String sqlCommandsResourcePath() {
        return "db/v1.0__commands.sql";
    }

    @Override
    protected final String sqlInitResourcePath() { return "db/migration/v1.0__init.sql"; }


    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var threadId = threadId(config);

        final var releaseThreadSql = sqlCommands.get("sqlReleaseThread");

        exec( conn -> {
            try (PreparedStatement ps = conn.prepareStatement(releaseThreadSql)) {
                ps.setString(1, threadId);
                ps.executeUpdate();
            }
            return null;
        });

        return new Tag(threadId, checkpoints);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, null);
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
    }


}