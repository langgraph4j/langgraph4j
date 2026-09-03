package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.LinkedList;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Postgres checkpoint saver.
 */
public class PostgresSaver extends AbstractPostgresSaver {

    public static class Builder extends AbstractBuilder<Builder> {

        public PostgresSaver build() throws Exception {
            validate();
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

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
    }

}
