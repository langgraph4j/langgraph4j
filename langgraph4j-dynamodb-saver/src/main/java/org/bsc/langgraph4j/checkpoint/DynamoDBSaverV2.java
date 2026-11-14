package org.bsc.langgraph4j.checkpoint;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bsc.langgraph4j.GraphRunnerException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.jspecify.annotations.Nullable;

/**
 * Amazon DynamoDB checkpoint saver, version 2: <b>archive on release</b>.
 *
 * <p>Each execution of a thread has a live {@code THREAD_{name}/ACTIVE} item that points to its
 * checkpoints through a random {@code threadUid}. Releasing the thread archives the execution as
 * the next release tag {@code THREAD_{name}/V#{version}} in one transaction, recording whether it
 * ended with an error. Released executions are read back with {@link #tag(RunnableConfig, Integer)}
 * and listed by {@link DynamoDBSaverV2Dashboard}. See {@link DynamoDBRepositoryV2} for the layout.
 *
 * <p>V2 uses its own table layout and is not compatible with tables written by {@link DynamoDBSaver}.
 *
 * <pre>{@code
 * var saver = DynamoDBSaverV2.builder()
 *     .tableName("lg4j-checkpoints-v2")
 *     .dynamoDbClient(client)
 *     .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
 *     .createTableIfNotExists(true)
 *     .build();
 * }</pre>
 */
public class DynamoDBSaverV2 extends AbstractDynamoDBSaverV2 {

    /** Maximum attempts to find a free release version under contention. */
    private static final int MAX_RELEASE_RETRIES = 10;

    private static final String NO_REASON = "No reason provided";

    /** Fluent builder for {@link DynamoDBSaverV2}. */
    public static class Builder extends AbstractBuilder<Builder> {

        Builder() {}

        public DynamoDBSaverV2 build() {
            return new DynamoDBSaverV2(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected DynamoDBSaverV2(Builder builder) {
        super(builder, true);
    }

    // ─── Write path ──────────────────────────────────────────────────────────────

    @Override
    protected void insertedCheckpoint(RunnableConfig config,
                                      LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) throws Exception {
        final String threadName = threadId(config);

        // serialize first: a state that cannot be written must not create a live thread
        final byte[] payload = encodeState(checkpoint.state());

        // creates ACTIVE (with a new uid) if needed and reserves the next seq
        final var active = repository.claimThread(threadName, UUID.randomUUID().toString());

        repository.putCheckpoint(
                active.threadUid(),
                checkpoint.id(),
                checkpoint.nodeId(),
                checkpoint.nextNodeId(),
                payload,
                stateSerializer.contentType(),
                config.checkPointId().orElse(null),
                active.seq(),
                false);

        log.debug("Checkpoint '{}' of thread '{}' inserted (uid {}, seq {})",
                checkpoint.id(), threadName, active.threadUid(), active.seq());
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {
        final String threadName = threadId(config);
        final String previousId = config.checkPointId()
                .orElseThrow(() -> new IllegalStateException("checkpoint id is required to update a checkpoint"));

        final var active = activeOf(threadName, checkpoints);
        if (active == null) {
            throw new NoSuchElementException(format("active thread '%s' not found", threadName));
        }

        final byte[] payload = encodeState(checkpoint.state());

        // replaced in place: the new checkpoint keeps the position (seq) of the old one
        final long seq = repository.deleteCheckpoint(active.threadUid(), previousId)
                .orElseThrow(() -> new NoSuchElementException(format("Checkpoint with id %s not found!", previousId)));

        repository.putCheckpoint(
                active.threadUid(),
                checkpoint.id(),
                checkpoint.nodeId(),
                checkpoint.nextNodeId(),
                payload,
                stateSerializer.contentType(),
                previousId,
                seq,
                true);

        log.debug("Checkpoint '{}' of thread '{}' replaced by '{}' (uid {}, seq {})",
                previousId, threadName, checkpoint.id(), active.threadUid(), seq);
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     @Nullable String message) throws Exception {
        return release(threadId(config), checkpoints, message, false);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config,
                                            LinkedList<Checkpoint> checkpoints,
                                            Throwable exception) throws Exception {
        final String message = ExceptionUtils.findCauseByType(exception, GraphRunnerException.class)
                .map(GraphRunnerException::getMessage)
                .orElseGet(exception::getMessage);
        return release(threadId(config), checkpoints, message, true);
    }

    /**
     * Archives the live execution as the next release version of the thread.
     *
     * <p>The version is the last released one plus one. If a concurrent release of another
     * execution with the same name takes it first, the version is recomputed (up to
     * {@value #MAX_RELEASE_RETRIES} attempts). The {@code ACTIVE} item is deleted only if it still
     * belongs to the loaded execution, so a release can never archive a newer execution.
     */
    private Tag release(String threadName,
                        LinkedList<Checkpoint> checkpoints,
                        @Nullable String message,
                        boolean isError) {
        final var active = activeOf(threadName, checkpoints);
        if (active == null) {
            // nothing was saved for this thread (or it was already released): nothing to tag
            log.debug("no active thread '{}' to release", threadName);
            return new Tag(threadName, checkpoints);
        }

        for (int attempt = 0; attempt < MAX_RELEASE_RETRIES; attempt++) {
            final int version = repository.lastTagVersion(threadName).orElse(0) + 1;

            switch (repository.releaseThread(active, version, isError, message)) {
                case RELEASED -> {
                    return new Tag(threadName, version, checkpoints);
                }
                case ALREADY_RELEASED -> {
                    log.debug("thread '{}' (uid {}) was already released", threadName, active.threadUid());
                    return new Tag(threadName, checkpoints);
                }
                case VERSION_TAKEN -> log.debug("release version {} of thread '{}' taken, retrying", version, threadName);
            }
        }
        throw new IllegalStateException(format("unable to release thread '%s': too much contention", threadName));
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        try {
            // always overwrite: a resumed thread can be interrupted again, and the latest reason wins
            repository.interruptThread(threadId(config), interruptionMetadata.reason().orElse(NO_REASON));
            return completedFuture(interruptionMetadata);
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    /** The {@code ACTIVE} item read by {@link #loadCheckpoints}, or read now if not available. */
    private DynamoDBRepositoryV2.@Nullable ThreadItem activeOf(String threadName, LinkedList<Checkpoint> checkpoints) {
        return (checkpoints instanceof ThreadCheckpoints loaded)
                ? loaded.active
                : repository.findActiveThread(threadName).orElse(null);
    }
}
