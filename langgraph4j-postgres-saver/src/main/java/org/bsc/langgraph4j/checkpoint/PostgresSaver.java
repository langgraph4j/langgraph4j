package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Postgres checkpoint saver.
 */
public class PostgresSaver extends AbstractPostgresSaver {

    public static class Builder extends AbstractBuilder<Builder> {

        public PostgresSaver build() throws Exception {
            return new PostgresSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }


    protected PostgresSaver(Builder builder) throws Exception {
        super(builder);
    }

    @Override
    protected void insertCheckpoint(Connection conn, RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {

        var threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        var upsertThreadSql = sqlCommands.get("sqlUpsertThread");

        var insertCheckpointSql = sqlCommands.get("sqlInsertCheckpoint");

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
            ps.setString(++field, encoderStateSerializer().contentType());

            // DB schema has DEFAULT CURRENT_TIMESTAMP for saved_at.
            // If checkpoint provides a specific time, use it. Otherwise, use current time from Java.
            // To use DB default, one would typically omit the column or pass NULL if the column definition allows it to trigger default.
            // OffsetDateTime savedAt = checkpoint.getSavedAt().orElse(OffsetDateTime.now());
            // psCheckpoint.setObject(8, savedAt);
            log.trace("Executing insert checkpoint:\n---\n{}---", insertCheckpointSql);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var threadId = threadId(config);

        var selectThreadSql = sqlCommands.get("sqlSelectThread");
        var releaseThreadSql = sqlCommands.get("sqlReleaseThread");
        return execTransaction(conn -> {

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
                        throw new IllegalStateException("active Thread '%s' not found".formatted(threadId));
                    }
                    if (rows > 1) {
                        throw new IllegalStateException("duplicate active Thread '%s' found".formatted(threadId));
                    }
                }
            }

            log.trace("Executing release Thread:\n---\n{}---", releaseThreadSql);
            try (PreparedStatement ps = conn.prepareStatement(releaseThreadSql)) {
                var field = 0;
                ps.setObject(++field,
                        requireNonNull(threadUUID, "threadUUID cannot be null"),
                        Types.OTHER); // nullable
                ps.executeUpdate();

            }

            return new Tag(threadId, checkpoints);
        });
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, null);
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }
}
