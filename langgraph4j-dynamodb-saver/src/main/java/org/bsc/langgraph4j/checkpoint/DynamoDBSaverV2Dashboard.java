package org.bsc.langgraph4j.checkpoint;

import static java.util.concurrent.CompletableFuture.failedFuture;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

/**
 * Read-only companion of {@link DynamoDBSaverV2} for dashboards and admin tools.
 *
 * <p>Lists the live threads ({@link #selectAllThreads()}) and the release tags
 * ({@link #selectAllTags()}) through the sparse {@code GSI1} index, so listing never reads
 * checkpoints or payloads. The inherited read path still works: {@link #list(RunnableConfig)} and
 * {@link #get(RunnableConfig)} return the checkpoints of a live thread, and
 * {@link #tag(RunnableConfig, Integer)} the checkpoints of a release.
 *
 * <p>It never creates or drops the table, and every write operation throws
 * {@link UnsupportedOperationException}.
 *
 * <pre>{@code
 * var dashboard = DynamoDBSaverV2Dashboard.builder()
 *     .tableName("lg4j-checkpoints-v2")
 *     .dynamoDbClient(client)
 *     .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
 *     .build();
 *
 * dashboard.selectAllThreads().forEach(System.out::println);
 * }</pre>
 */
public class DynamoDBSaverV2Dashboard extends AbstractDynamoDBSaverV2 {

    /** Fluent builder for {@link DynamoDBSaverV2Dashboard}. Table creation options are ignored. */
    public static class Builder extends AbstractBuilder<Builder> {

        Builder() {}

        public DynamoDBSaverV2Dashboard build() {
            return new DynamoDBSaverV2Dashboard(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A live thread ({@code ACTIVE} item).
     *
     * @param threadUid id of the execution, shared by its checkpoints
     * @param name thread name (the {@code threadId} of the {@link RunnableConfig})
     * @param isInterrupted whether the execution is waiting at an interruption
     * @param message interruption reason, if interrupted
     * @param createdAt when the execution saved its first checkpoint
     */
    public record ThreadRecord(String threadUid,
                               String name,
                               boolean isInterrupted,
                               @Nullable String message,
                               Instant createdAt) {
    }

    /**
     * A released execution ({@code V#} tag).
     *
     * @param threadUid id of the execution, shared by its checkpoints
     * @param name thread name
     * @param version release version, starting from 1 for each thread name
     * @param isReleased always {@code true}, for parity with the SQL V2 dashboards
     * @param isError whether the execution was released because of an error
     * @param message release message or error message
     * @param createdAt when the execution saved its first checkpoint
     * @param releasedAt when the execution was released
     */
    public record TagRecord(String threadUid,
                            String name,
                            int version,
                            boolean isReleased,
                            boolean isError,
                            @Nullable String message,
                            Instant createdAt,
                            Instant releasedAt) {
    }

    protected DynamoDBSaverV2Dashboard(Builder builder) {
        super(builder, false);
    }

    /** All live threads, most recently created first. */
    public List<ThreadRecord> selectAllThreads() {
        return repository.selectAllThreads().stream()
                .map(t -> new ThreadRecord(
                        t.threadUid(),
                        t.threadName(),
                        t.isInterrupted(),
                        t.message(),
                        Instant.ofEpochMilli(t.createdAt())))
                .toList();
    }

    /** All release tags, most recently released first. */
    public List<TagRecord> selectAllTags() {
        return repository.selectAllTags().stream()
                .map(t -> new TagRecord(
                        t.threadUid(),
                        t.threadName(),
                        t.version(),
                        true,
                        t.isError(),
                        t.message(),
                        Instant.ofEpochMilli(t.createdAt()),
                        Instant.ofEpochMilli(t.releasedAt())))
                .toList();
    }

    // ─── Write path: not supported ───────────────────────────────────────────────

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) {
        throw readOnly();
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) {
        throw readOnly();
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message) {
        throw readOnly();
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception) {
        throw readOnly();
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return failedFuture(readOnly());
    }

    @Override
    public void deleteThread(String threadName) {
        throw readOnly();
    }

    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("DynamoDBSaverV2Dashboard is read-only");
    }
}
