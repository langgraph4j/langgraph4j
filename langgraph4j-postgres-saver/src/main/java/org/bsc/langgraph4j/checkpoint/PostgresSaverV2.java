package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.GraphRunnerException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Postgres checkpoint saver with thread release tags and interruption state.
 */
public class PostgresSaverV2 extends AbstractPostgresSaver {

    public static class Builder extends AbstractBuilder<Builder> {

        public PostgresSaverV2 build() throws Exception {
            validate();
            return new PostgresSaverV2(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected PostgresSaverV2(Builder builder) throws Exception {
        super(builder);
    }

    @Override
    protected final String sqlCommandsResourcePath() {
        return "db/v2.0__commands.sql";
    }

    @Override
    protected final String sqlInitResourcePath() {
        return "db/migration/v2.0__init.sql";
    }

    private Tag internalReleaseCheckpoints(String threadId,
                                           LinkedList<Checkpoint> checkpoints,
                                           @Nullable String message,
                                           @Nullable Exception exception) throws Exception {

        final var sqlInsertTag = sqlCommands.get("sqlReleaseThread_insertTag");
        final var sqlDeleteThread = sqlCommands.get("sqlReleaseThread_deleteThread");

        execTransaction(conn -> {
                Long id = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertTag)) {
                var index = 0;

                if (exception != null) {
                    ps.setBoolean(++index, true);
                    final var msg = ExceptionUtils.findCauseByType(exception, GraphRunnerException.class)
                            .map(GraphRunnerException::getMessage)
                            .orElseGet(exception::getMessage);
                    ps.setString(++index, msg);
                } else {
                    ps.setBoolean(++index, false);
                    if (message != null) {
                        ps.setString(++index, message);
                    } else {
                        ps.setNull(++index, java.sql.Types.VARCHAR);
                    }
                }
                ps.setString(++index, threadId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getLong("thread_id");
                    } else {
                        throw new SQLException(
                                "No LG4JThread found for thread_id: %s".formatted(threadId));
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlDeleteThread)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            return null;
        });

        return new Tag(threadId, checkpoints);
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        return internalReleaseCheckpoints(threadId(config), checkpoints, message, null);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Exception exception) throws Exception {
        return internalReleaseCheckpoints(threadId(config), checkpoints, null, exception);
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        final var threadId = threadId(config);
        final var sqlInterruptThread = sqlCommands.get("sqlInterruptThread");

        try {
            return execTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sqlInterruptThread)) {
                    var index = 0;
                    ps.setString(++index, interruptionMetadata.reason().orElse("No reason provided"));
                    ps.setString(++index, threadId);
                    ps.executeUpdate();
                }

                return completedFuture(interruptionMetadata);
            });
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    @Override
    protected void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
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
    }
