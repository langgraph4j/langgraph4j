package org.bsc.langgraph4j.checkpoint;

import com.mongodb.ConnectionString;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import org.bsc.langgraph4j.GraphRunnerException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.setOnInsert;
import static com.mongodb.client.model.Updates.unset;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Checkpoint saver backed by MongoDB.
 *
 * <p>Data model (the V2 model: release tags, error metadata and interruption state):
 * <ul>
 *   <li>{@code <prefix>_thread}: one document per execution of a logical thread. A thread is
 *       {@code ACTIVE} until it is released, then it becomes {@code RELEASED} and receives the next
 *       {@code version} for its {@code thread_name}. Released threads are the <i>tags</i>.</li>
 *   <li>{@code <prefix>_checkpoint}: one document per checkpoint, pointing to its thread document
 *       through {@code thread_ref} and ordered by a per-thread monotonic {@code seq}.</li>
 * </ul>
 *
 * <p>Lifecycle: a {@link MongoClient} or {@link MongoDatabase} supplied by the caller is never
 * closed by the saver. Only a client created by the saver itself (from a connection string) is
 * closed by {@link #close()}.
 *
 * <pre>{@code
 * try (var saver = MongoDBSaver.builder()
 *         .connectionString("mongodb://localhost:27017")
 *         .databaseName("langgraph4j")
 *         .stateSerializer(new ObjectStreamStateSerializer<>(AgentState::new))
 *         .build()) {
 *     ...
 * }
 * }</pre>
 */
public class MongoDBSaver extends AbstractCheckpointSaver implements LG4JLoggable, AutoCloseable {

    public static final String DEFAULT_COLLECTION_PREFIX = "lg4j";

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_RELEASED = "RELEASED";

    private static final int MAX_RETRIES = 10;
    private static final String NO_REASON = "No reason provided";

    public static class Builder {
        private StateSerializer<? extends AgentState> stateSerializer;
        private MongoClient mongoClient;
        private MongoDatabase mongoDatabase;
        private String connectionString;
        private String databaseName;
        private String collectionPrefix = DEFAULT_COLLECTION_PREFIX;
        private boolean createIndexes = true;
        private boolean dropCollectionsFirst;

        public <State extends AgentState> Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /** Existing client, owned by the caller (never closed by the saver). Requires {@link #databaseName}. */
        public Builder mongoClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        /** Existing database, owned by the caller (its client is never closed by the saver). */
        public Builder mongoDatabase(MongoDatabase mongoDatabase) {
            this.mongoDatabase = mongoDatabase;
            return this;
        }

        /** Connection string: the saver creates its own client and closes it on {@link #close()}. */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /** Prefix of the two collections, default {@value #DEFAULT_COLLECTION_PREFIX}. */
        public Builder collectionPrefix(String collectionPrefix) {
            this.collectionPrefix = collectionPrefix;
            return this;
        }

        /** Create the indexes (idempotent) on build, default {@code true}. */
        public Builder createIndexes(boolean createIndexes) {
            this.createIndexes = createIndexes;
            return this;
        }

        /** Drop the saver collections before creating the indexes. Implies {@code createIndexes}. */
        public Builder dropCollectionsFirst(boolean dropCollectionsFirst) {
            this.dropCollectionsFirst = dropCollectionsFirst;
            return this;
        }

        public MongoDBSaver build() {
            return new MongoDBSaver(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final StateSerializer<? extends AgentState> stateSerializer;
    private final @Nullable MongoClient ownedClient;
    private final MongoCollection<Document> threads;
    private final MongoCollection<Document> checkpoints;

    protected MongoDBSaver(Builder builder) {
        stateSerializer = requireNonNull(builder.stateSerializer, "stateSerializer cannot be null");
        final var prefix = requireNotBlank(builder.collectionPrefix, "collectionPrefix");

        final var sources = (builder.mongoDatabase != null ? 1 : 0)
                + (builder.mongoClient != null ? 1 : 0)
                + (builder.connectionString != null ? 1 : 0);
        if (sources != 1) {
            throw new IllegalArgumentException(
                    "exactly one of 'mongoDatabase', 'mongoClient' or 'connectionString' must be provided");
        }
        if (builder.mongoDatabase != null && builder.databaseName != null) {
            // the database already has a name: accepting a second one would silently write elsewhere
            throw new IllegalArgumentException("'databaseName' cannot be combined with 'mongoDatabase'");
        }

        MongoClient owned = null;
        try {
            final MongoDatabase database;
            if (builder.mongoDatabase != null) {
                database = builder.mongoDatabase;
            } else if (builder.mongoClient != null) {
                database = builder.mongoClient.getDatabase(requireNotBlank(builder.databaseName, "databaseName"));
            } else {
                final var cs = new ConnectionString(builder.connectionString);
                final var dbName = builder.databaseName != null ? builder.databaseName : cs.getDatabase();
                requireNotBlank(dbName, "databaseName");
                owned = MongoClients.create(cs);
                database = owned.getDatabase(dbName);
            }
            ownedClient = owned;
            threads = database.getCollection(prefix + "_thread");
            checkpoints = database.getCollection(prefix + "_checkpoint");

            if (builder.dropCollectionsFirst) {
                checkpoints.drop();
                threads.drop();
            }
            if (builder.createIndexes || builder.dropCollectionsFirst) {
                createIndexes();
            }
        } catch (RuntimeException e) {
            // do not leak the client we created if construction fails (see PR #484)
            if (owned != null) {
                owned.close();
            }
            throw e;
        }
    }

    private static String requireNotBlank(@Nullable String value, String name) {
        if (requireNonNull(value, format("'%s' cannot be null", name)).isBlank()) {
            throw new IllegalArgumentException(format("'%s' cannot be blank", name));
        }
        return value;
    }

    /**
     * Idempotent: creating an index that already exists with the same definition is a no-op, and
     * creating an index implicitly creates its collection.
     */
    private void createIndexes() {
        // at most one live thread per name: makes the upsert of the live thread race-free
        threads.createIndex(Indexes.ascending("thread_name"), new IndexOptions()
                .name("uq_active_thread_name")
                .unique(true)
                .partialFilterExpression(eq("status", STATUS_ACTIVE)));
        // release versions are unique per name: concurrent releases cannot get the same version
        threads.createIndex(Indexes.ascending("thread_name", "version"), new IndexOptions()
                .name("uq_released_thread_name_version")
                .unique(true)
                .partialFilterExpression(eq("status", STATUS_RELEASED)));
        checkpoints.createIndex(Indexes.ascending("checkpoint_id"), new IndexOptions()
                .name("uq_checkpoint_id")
                .unique(true));
        // checkpoint history, newest first
        checkpoints.createIndex(Indexes.compoundIndex(Indexes.ascending("thread_ref"), Indexes.descending("seq")),
                new IndexOptions().name("uq_thread_ref_seq").unique(true));
    }

    /**
     * Closes the client only if it was created by this saver. Caller-supplied clients and
     * databases are left open. Safe to call more than once.
     */
    @Override
    public void close() {
        if (ownedClient != null) {
            ownedClient.close();
        }
    }

    // ---- serialization -------------------------------------------------------------------------

    private String encodeState(Map<String, Object> data) throws IOException {
        return stateSerializer.writeDataAsString(data);
    }

    private Map<String, Object> decodeState(String payload, String contentType)
            throws IOException, ClassNotFoundException {
        if (!Objects.equals(contentType, stateSerializer.contentType())) {
            throw new IllegalStateException(format(
                    "Content Type used for store state '%s' is different from one '%s' used for deserialize it",
                    contentType, stateSerializer.contentType()));
        }
        return stateSerializer.readDataFromString(payload).data();
    }

    private static boolean isDuplicateKey(MongoException e) {
        return ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY;
    }

    // ---- threads ---------------------------------------------------------------------------------

    private static Bson activeThread(String threadName) {
        return and(eq("thread_name", threadName), eq("status", STATUS_ACTIVE));
    }

    private @Nullable Document findActiveThread(String threadName) {
        return threads.find(activeThread(threadName)).first();
    }

    /**
     * Atomically gets (creating it if absent) the live thread and reserves the next checkpoint
     * sequence number on it. Safe with concurrent writers, also from other processes.
     *
     * <p>A new checkpoint means the execution has moved on, so a pending interruption is cleared.
     */
    private Document claimSeq(String threadName) {
        final var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);
        final var update = combine(
                inc("seq", 1L),
                set("is_interrupted", false),
                unset("interruption_reason"),
                setOnInsert("is_error", false),
                setOnInsert("created_at", new Date()));
        for (var attempt = 0; ; attempt++) {
            try {
                return requireNonNull(threads.findOneAndUpdate(activeThread(threadName), update, options));
            } catch (MongoException e) {
                // two writers upserted the same new thread: the loser retries and finds it
                if (!isDuplicateKey(e) || attempt >= MAX_RETRIES) {
                    throw e;
                }
            }
        }
    }

    // ---- AbstractCheckpointSaver hooks -----------------------------------------------------------

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var thread = findActiveThread(threadId(config));
        if (thread == null) {
            return new LinkedList<>();
        }
        return loadCheckpoints(thread);
    }

    private LinkedList<Checkpoint> loadCheckpoints(Document thread) throws IOException, ClassNotFoundException {
        final var result = new LinkedList<Checkpoint>();
        final var cursor = checkpoints.find(eq("thread_ref", thread.getObjectId("_id")))
                .sort(Indexes.descending("seq"));
        for (var doc : cursor) {
            result.add(Checkpoint.builder()
                    .id(doc.getString("checkpoint_id"))
                    .nodeId(doc.getString("node_id"))
                    .nextNodeId(doc.getString("next_node_id"))
                    .state(decodeState(doc.getString("state_data"), doc.getString("state_content_type")))
                    .build());
        }
        return result;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        // serialize first: a state that cannot be written must not create a live thread
        final var stateData = encodeState(checkpoint.state());
        final var thread = claimSeq(threadId(config));

        this.checkpoints.insertOne(new Document()
                .append("checkpoint_id", checkpoint.id())
                .append("thread_ref", thread.getObjectId("_id"))
                .append("seq", thread.getLong("seq"))
                .append("node_id", checkpoint.nodeId())
                .append("next_node_id", checkpoint.nextNodeId())
                .append("state_data", stateData)
                .append("state_content_type", stateSerializer.contentType())
                .append("saved_at", new Date()));
        log.debug("Checkpoint {} for thread {} inserted successfully.", checkpoint.id(), threadId(config));
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        final var threadId = threadId(config);
        final var previousId = config.checkPointId()
                .orElseThrow(() -> new IllegalStateException("checkpoint id is required to update a checkpoint"));

        final var thread = findActiveThread(threadId);
        if (thread == null) {
            throw new NoSuchElementException(format("active Thread '%s' not found", threadId));
        }

        // replaced in place: the checkpoint keeps its position (seq) in the history
        final var result = this.checkpoints.updateOne(
                and(eq("thread_ref", thread.getObjectId("_id")), eq("checkpoint_id", previousId)),
                combine(set("checkpoint_id", checkpoint.id()),
                        set("node_id", checkpoint.nodeId()),
                        set("next_node_id", checkpoint.nextNodeId()),
                        set("state_data", encodeState(checkpoint.state())),
                        set("state_content_type", stateSerializer.contentType()),
                        set("saved_at", new Date())));
        if (result.getMatchedCount() == 0) {
            throw new NoSuchElementException(format("Checkpoint with id %s not found!", previousId));
        }
        log.debug("Checkpoint {} for thread {} updated successfully.", checkpoint.id(), threadId);
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, @Nullable String message)
            throws Exception {
        return release(threadId(config), checkpoints, message, false);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable exception)
            throws Exception {
        final var message = ExceptionUtils.findCauseByType(exception, GraphRunnerException.class)
                .map(GraphRunnerException::getMessage)
                .orElseGet(exception::getMessage);
        return release(threadId(config), checkpoints, message, true);
    }

    private Tag release(String threadName, LinkedList<Checkpoint> checkpoints, @Nullable String message, boolean isError) {
        final var thread = findActiveThread(threadName);
        if (thread == null) {
            // nothing was saved for this thread (or it was already released): nothing to tag
            log.debug("no active thread '{}' to release", threadName);
            return new Tag(threadName, checkpoints);
        }
        return releaseThread(thread.getObjectId("_id"), threadName, checkpoints, message, isError);
    }

    /**
     * Releases exactly the given thread document. It is never looked up again by name, so a
     * release that loses a race can not release a newer run of the same thread.
     */
    Tag releaseThread(ObjectId threadRef, String threadName, LinkedList<Checkpoint> checkpoints,
                      @Nullable String message, boolean isError) {
        for (var attempt = 0; attempt < MAX_RETRIES; attempt++) {
            final var last = threads.find(and(eq("thread_name", threadName), eq("status", STATUS_RELEASED)))
                    .sort(Indexes.descending("version"))
                    .first();
            final var version = (last == null ? 0 : last.getInteger("version")) + 1;

            try {
                final var released = threads.findOneAndUpdate(
                        and(eq("_id", threadRef), eq("status", STATUS_ACTIVE)),
                        combine(set("status", STATUS_RELEASED),
                                set("version", version),
                                set("is_error", isError),
                                set("message", message),
                                set("released_at", new Date())));
                if (released != null) {
                    return new Tag(threadName, version, checkpoints);
                }
                // someone else released this thread first: report the version it got
                log.debug("thread '{}' was already released", threadName);
                final var current = threads.find(eq("_id", threadRef)).first();
                return new Tag(threadName, current == null ? null : current.getInteger("version"), checkpoints);
            } catch (MongoException e) {
                // another thread with the same name took this version: recompute it
                if (!isDuplicateKey(e)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException(format("unable to release Thread '%s': too much contention", threadName));
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        try {
            // always overwrite: a resumed thread can be interrupted again, and the latest reason wins
            threads.updateOne(
                    activeThread(threadId(config)),
                    combine(set("is_interrupted", true),
                            set("interruption_reason", interruptionMetadata.reason().orElse(NO_REASON))));
            return completedFuture(interruptionMetadata);
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, @Nullable Integer version) throws Exception {
        final var threadName = threadId(config);
        final var query = version == null
                ? and(eq("thread_name", threadName), eq("status", STATUS_RELEASED))
                : and(eq("thread_name", threadName), eq("status", STATUS_RELEASED), eq("version", version));

        final var thread = threads.find(query).sort(Indexes.descending("version")).first();
        if (thread == null) {
            return Optional.empty();
        }
        return Optional.of(new Tag(threadName, thread.getInteger("version"), loadCheckpoints(thread)));
    }
}
