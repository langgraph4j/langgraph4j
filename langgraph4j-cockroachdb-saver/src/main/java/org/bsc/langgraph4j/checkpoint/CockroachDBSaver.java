package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

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
public class CockroachDBSaver extends AbstractCockroachDBSaver {

    /** Default CockroachDB SQL port. */
    public static final int DEFAULT_PORT = 26257;

    public static class Builder extends AbstractBuilder<Builder> {
        public CockroachDBSaver build() throws SQLException {
            return new CockroachDBSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected CockroachDBSaver(Builder builder) throws SQLException {
        super(builder);
    }

    @Override
    protected String sqlCommandsResourcePath() {
        return "db/v1.1__commands.sql";
    }

    @Override
    protected String sqlInitResourcePath() {
        return "db/migration/v1.1__init.sql";
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {

        final var checkpoints = new LinkedList<Checkpoint>();

        final var threadId = threadId(config);

        final var sqlCheckThread = sqlCommands.get("sqlCheckThread");
        final var sqlQueryCheckpoints = sqlCommands.get("sqlSelectCheckpoints");
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
                            .state(decodeState(rs.getString(4), rs.getString(5)))
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
            ps.setObject(++field, UUID.fromString(checkpoint.getId()), Types.OTHER);
            ps.setNull(++field, Types.OTHER);
            ps.setObject(++field, requireNonNull(threadUUID, "threadUUID cannot be null"), Types.OTHER);
            ps.setString(++field, checkpoint.getNodeId());
            ps.setString(++field, checkpoint.getNextNodeId());
            ps.setString(++field, encodeState(checkpoint.getState()));
            ps.setString(++field, stateSerializer().contentType());

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

        var deletePreviousCheckpointSql = sqlCommands.get("sqlDeletePreviousCheckpoint");

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
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) throws Exception {
        final var threadId = threadId(config);

        var selectThreadSql = sqlCommands.get("sqlSelectThread");
        var releaseThreadSql = sqlCommands.get("sqlReleaseThread");
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

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) throws Exception {
        return releaseCheckpoints(config, checkpoints, exception.getMessage());
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return completedFuture(interruptionMetadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
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
