package org.bsc.langgraph4j.checkpoint;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MongoDBSaverTest extends AbstractCheckpointSaverTest {

    static final String DB = "lg4j_test";

    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");
    static MongoClient client;

    @BeforeAll
    public static void init() throws IOException {
        try (var is = MongoDBSaverTest.class.getResourceAsStream("/logging.properties")) {
            if (is != null) LogManager.getLogManager().readConfiguration(is);
        }
        mongo.start();
        client = MongoClients.create(mongo.getConnectionString());
    }

    @AfterAll
    public static void shutdown() {
        client.close();
        mongo.stop();
    }

    static StateSerializer<AgentState> serializer() {
        return new ObjectStreamStateSerializer<>(AgentState::new);
    }

    static MongoDBSaver.Builder builder(String prefix) {
        return MongoDBSaver.builder()
                .mongoClient(client)
                .databaseName(DB)
                .collectionPrefix(prefix)
                .stateSerializer(serializer());
    }

    static MongoDBSaver newSaver(String prefix) {
        return builder(prefix).build();
    }

    static Checkpoint checkpoint(String node, Object value) {
        return Checkpoint.builder()
                .nodeId(node)
                .nextNodeId("next-of-" + node)
                .state(Map.<String, Object>of("v", value))
                .build();
    }

    static RunnableConfig thread(String id) {
        return RunnableConfig.builder().threadId(id).build();
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer,
                                                       @Nullable String threadId) {
        return MongoDBSaver.builder()
                .mongoClient(client)
                .databaseName(DB)
                .stateSerializer(stateSerializer)
                .build();
    }

    // ---- history, isolation, replace ---------------------------------------------------------------

    @Test
    void testHistoryIsNewestFirstAndThreadsAreIsolated() throws Exception {
        var saver = newSaver("history");
        var a = thread("a");
        var b = thread("b");

        var a1 = checkpoint("n1", "a1");
        var a2 = checkpoint("n2", "a2");
        var b1 = checkpoint("n1", "b1");
        saver.put(a, a1);
        saver.put(b, b1);
        saver.put(a, a2);

        assertEquals(List.of(a2.id(), a1.id()), saver.list(a).stream().map(Checkpoint::id).toList());
        assertEquals(List.of(b1.id()), saver.list(b).stream().map(Checkpoint::id).toList());
        assertEquals(a2, saver.get(a).orElseThrow());
        assertEquals(a1, saver.get(RunnableConfig.builder(a).checkPointId(a1.id()).build()).orElseThrow());
        assertTrue(saver.list(thread("unknown")).isEmpty());
        assertTrue(saver.get(thread("unknown")).isEmpty());
    }

    @Test
    void testPutWithCheckpointIdReplacesInPlace() throws Exception {
        var saver = newSaver("replace");
        var cfg = thread("t");
        var c1 = checkpoint("n1", "one");
        var c2 = checkpoint("n2", "two");
        saver.put(cfg, c1);
        saver.put(cfg, c2);

        var replacement = new Checkpoint(c1.id(), Map.of("v", "ONE"), c1.nodeId(), "elsewhere");
        saver.put(RunnableConfig.builder(cfg).checkPointId(c1.id()).build(), replacement);

        var history = List.copyOf(saver.list(cfg));
        assertEquals(2, history.size());
        assertEquals(c2.id(), history.get(0).id());
        assertEquals(replacement, history.get(1));

        assertThrows(NoSuchElementException.class,
                () -> saver.put(RunnableConfig.builder(cfg).checkPointId("missing").build(), c2));
    }

    // ---- release tags, error and interruption metadata ---------------------------------------------

    @Test
    void testReleaseTagsAreVersioned() throws Exception {
        var saver = newSaver("tags");
        var cfg = thread("t");

        var first = checkpoint("n1", "first");
        saver.put(cfg, first);
        var tag1 = saver.release(cfg);
        assertEquals(Optional.of(1), tag1.version());
        assertTrue(saver.list(cfg).isEmpty(), "released thread must not be loaded as live");

        var second = checkpoint("n1", "second");
        saver.put(cfg, second);
        var tag2 = saver.release(cfg, "done");
        assertEquals(Optional.of(2), tag2.version());

        assertEquals(List.of(first), List.copyOf(saver.tag(cfg, 1).orElseThrow().checkpoints()));
        assertEquals(List.of(second), List.copyOf(saver.tag(cfg, 2).orElseThrow().checkpoints()));
        var last = saver.lastTag(cfg).orElseThrow();
        assertEquals(Optional.of(2), last.version());
        assertEquals(Optional.of(second), last.lastCheckpoint());
        assertTrue(saver.tag(cfg, 3).isEmpty());
        assertTrue(saver.lastTag(thread("never-released")).isEmpty());

        // a new run after a release starts from an empty history
        assertTrue(saver.get(cfg).isEmpty());
    }

    @Test
    void testReleaseOnErrorStoresErrorMetadata() throws Exception {
        var saver = newSaver("errors");
        var cfg = thread("t");
        saver.put(cfg, checkpoint("n1", "x"));

        var tag = saver.releaseOnError(cfg, new IllegalStateException("boom"));
        assertEquals(Optional.of(1), tag.version());

        var doc = client.getDatabase(DB).getCollection("errors_thread")
                .find(new Document("thread_name", "t").append("version", 1)).first();
        assertNotNull(doc);
        assertEquals("RELEASED", doc.getString("status"));
        assertTrue(doc.getBoolean("is_error"));
        assertEquals("boom", doc.getString("message"));
        assertEquals(1, saver.tag(cfg, 1).orElseThrow().checkpoints().size());
    }

    @Test
    void testNormalReleaseIsNotAnError() throws Exception {
        var saver = newSaver("noerror");
        var cfg = thread("t");
        saver.put(cfg, checkpoint("n1", "x"));
        saver.release(cfg, "all good");

        var doc = client.getDatabase(DB).getCollection("noerror_thread").find(new Document("thread_name", "t")).first();
        assertNotNull(doc);
        assertFalse(doc.getBoolean("is_error"));
        assertEquals("all good", doc.getString("message"));
    }

    @Test
    void testReleaseWithoutActiveThreadIsNoOp() throws Exception {
        var saver = newSaver("noop");
        assertTrue(saver.release(thread("nothing")).version().isEmpty());
        assertTrue(saver.releaseOnError(thread("nothing"), new RuntimeException("x")).checkpoints().isEmpty());
    }

    @Test
    void testRegisterInterruptionMarksActiveThread() throws Exception {
        var saver = newSaver("interrupt");
        var cfg = thread("t");
        var c = checkpoint("n1", "x");
        saver.put(cfg, c);

        var metadata = InterruptionMetadata.builder("n1", new AgentState(Map.of()))
                .reason("waiting for approval")
                .build();
        assertSame(metadata, saver.registerInterruption(cfg, metadata).get());

        var threads = client.getDatabase(DB).getCollection("interrupt_thread");
        var doc = threads.find(new Document("thread_name", "t").append("status", "ACTIVE")).first();
        assertNotNull(doc);
        assertTrue(doc.getBoolean("is_interrupted"));
        assertEquals("waiting for approval", doc.getString("interruption_reason"));

        // checkpoints survive a new saver instance (resume after restart)
        assertEquals(c, newSaver("interrupt").get(cfg).orElseThrow());
    }

    private static InterruptionMetadata<AgentState> interruption(String reason) {
        return InterruptionMetadata.builder("n", new AgentState(Map.of())).reason(reason).build();
    }

    @Test
    void testInterruptionLifecycleAcrossResumes() throws Exception {
        var saver = newSaver("reinterrupt");
        var cfg = thread("t");
        var threads = client.getDatabase(DB).getCollection("reinterrupt_thread");
        var live = new Document("thread_name", "t").append("status", "ACTIVE");

        var c1 = checkpoint("n1", "x");
        saver.put(cfg, c1);
        saver.registerInterruption(cfg, interruption("before agent_2")).get();

        // a human edits the state while interrupted: still waiting
        saver.put(RunnableConfig.builder(cfg).checkPointId(c1.id()).build(),
                new Checkpoint(c1.id(), Map.of("v", "edited"), "n1", "agent_2"));
        assertTrue(threads.find(live).first().getBoolean("is_interrupted"));

        // resume: the next node writes a checkpoint, the interruption is over
        saver.put(cfg, checkpoint("agent_2", "y"));
        var doc = threads.find(live).first();
        assertFalse(doc.getBoolean("is_interrupted"));
        assertNull(doc.getString("interruption_reason"));

        // interrupted again: the new reason is recorded
        saver.registerInterruption(cfg, interruption("before agent_3")).get();
        doc = threads.find(live).first();
        assertTrue(doc.getBoolean("is_interrupted"));
        assertEquals("before agent_3", doc.getString("interruption_reason"));

        // a plain release keeps the interruption reason apart from the (null) release message
        saver.release(cfg);
        doc = threads.find(new Document("thread_name", "t").append("version", 1)).first();
        assertEquals("before agent_3", doc.getString("interruption_reason"));
        assertNull(doc.getString("message"));
    }

    @Test
    void testUnserializableStateCreatesNoThread() throws Exception {
        var saver = newSaver("unserializable");
        var cfg = thread("t");
        var bad = Checkpoint.builder().nodeId("n1").nextNodeId("n2")
                .state(Map.<String, Object>of("v", new Object())) // not Serializable
                .build();

        assertThrows(java.io.IOException.class, () -> saver.put(cfg, bad));

        assertEquals(0, client.getDatabase(DB).getCollection("unserializable_thread").countDocuments());
        assertTrue(saver.releaseOnError(cfg, new RuntimeException("boom")).version().isEmpty(),
                "no empty version may be tagged for a run that saved nothing");
    }

    // ---- subgraph savers ---------------------------------------------------------------------------

    @Test
    void testSubGraphSaversAreListedAndReleasedWithParent() throws Exception {
        var saver = newSaver("subgraph");
        var parent = thread("parent");
        var child = thread("parent-child");
        var childSaver = newSaver("subgraph");

        saver.put(parent, checkpoint("p1", "p"));
        childSaver.put(child, checkpoint("c1", "c"));
        saver.putSubGraphSaver(parent, child, childSaver);

        var subs = saver.listSubGraphSaver(parent);
        assertEquals(1, subs.size());
        assertEquals("parent-child", subs.iterator().next().threadId());
        assertSame(childSaver, subs.iterator().next().saver());

        saver.release(parent);

        assertTrue(saver.listSubGraphSaver(parent).isEmpty());
        assertEquals(Optional.of(1), childSaver.lastTag(child).orElseThrow().version());
        assertEquals(Optional.of(1), saver.lastTag(parent).orElseThrow().version());
    }

    // ---- indexes, collections and builder ----------------------------------------------------------

    @Test
    void testIndexCreationIsIdempotentAndCollectionsAreLazy() {
        var db = client.getDatabase(DB);
        assertFalse(collectionNames().contains("idem_thread"));

        newSaver("idem");
        newSaver("idem"); // second build must not fail

        assertTrue(collectionNames().containsAll(List.of("idem_thread", "idem_checkpoint")));
        var threadIndexes = names(db.getCollection("idem_thread").listIndexes());
        assertTrue(threadIndexes.containsAll(List.of("uq_active_thread_name", "uq_released_thread_name_version")));
        var checkpointIndexes = names(db.getCollection("idem_checkpoint").listIndexes());
        assertTrue(checkpointIndexes.containsAll(List.of("uq_checkpoint_id", "uq_thread_ref_seq")));
    }

    @Test
    void testCreateIndexesDisabledCreatesNothing() {
        builder("noindex").createIndexes(false).build();
        assertFalse(collectionNames().contains("noindex_thread"));
    }

    @Test
    void testDropCollectionsFirst() throws Exception {
        var cfg = thread("t");
        newSaver("drop").put(cfg, checkpoint("n1", "x"));
        var saver = builder("drop").dropCollectionsFirst(true).build();
        assertTrue(saver.list(cfg).isEmpty());
    }

    @Test
    void testBuilderValidation() {
        assertThrows(NullPointerException.class, () -> MongoDBSaver.builder().mongoClient(client).databaseName(DB).build());
        assertThrows(IllegalArgumentException.class, () -> MongoDBSaver.builder().stateSerializer(serializer()).build());
        assertThrows(IllegalArgumentException.class, () -> builder("x").databaseName(" ").build());
        assertThrows(IllegalArgumentException.class,
                () -> builder("x").mongoDatabase(client.getDatabase(DB)).build(), "two sources are ambiguous");
        assertThrows(IllegalArgumentException.class, () -> MongoDBSaver.builder()
                .mongoDatabase(client.getDatabase("other")).databaseName(DB)
                .stateSerializer(serializer()).build(), "databaseName would be silently ignored");
        assertThrows(IllegalArgumentException.class, () -> builder("x").collectionPrefix("").build());
    }

    // ---- client lifecycle --------------------------------------------------------------------------

    @Test
    void testCloseDoesNotCloseCallerSuppliedClient() throws Exception {
        try (var own = MongoClients.create(mongo.getConnectionString())) {
            var saver = MongoDBSaver.builder()
                    .mongoClient(own).databaseName(DB).collectionPrefix("lifecycle")
                    .stateSerializer(serializer()).build();
            saver.put(thread("t"), checkpoint("n1", "x"));
            saver.close();
            saver.close();
            // the caller's client is still usable
            assertEquals(1, own.getDatabase(DB).getCollection("lifecycle_checkpoint").countDocuments());
        }
    }

    @Test
    void testCloseClosesOwnedClient() throws Exception {
        var saver = MongoDBSaver.builder()
                .connectionString(mongo.getConnectionString()).databaseName(DB).collectionPrefix("owned")
                .stateSerializer(serializer()).build();
        var cfg = thread("t");
        saver.put(cfg, checkpoint("n1", "x"));
        saver.close();
        saver.close(); // idempotent
        var ex = assertThrows(RuntimeException.class, () -> saver.list(cfg), "owned client must be closed");
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void testDatabaseNameFromConnectionString() throws Exception {
        try (var saver = MongoDBSaver.builder()
                .connectionString(mongo.getConnectionString() + "/" + DB).collectionPrefix("csdb")
                .stateSerializer(serializer()).build()) {
            saver.put(thread("t"), checkpoint("n1", "x"));
        }
        assertTrue(collectionNames().contains("csdb_checkpoint"));
    }

    @Test
    void testBuildFailsWhenServerIsUnreachable() {
        // index creation fails after the owned client was created: the saver closes it before rethrowing
        assertThrows(RuntimeException.class, () -> MongoDBSaver.builder()
                .connectionString("mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=300")
                .databaseName(DB).stateSerializer(serializer()).build());
    }

    // ---- concurrency -------------------------------------------------------------------------------

    @Test
    void testConcurrentWritersToOneThreadLoseNothing() throws Exception {
        final var writers = 8;
        final var perWriter = 25;
        final var cfg = thread("shared");

        // each writer has its own saver instance (its own in-process lock), as separate processes would
        var savers = new ArrayList<MongoDBSaver>();
        for (var i = 0; i < writers; i++) savers.add(newSaver("concurrent"));

        var pool = Executors.newFixedThreadPool(writers);
        var start = new CountDownLatch(1);
        var expected = ConcurrentHashMap.<String>newKeySet();
        var futures = new ArrayList<Future<?>>();
        for (var w = 0; w < writers; w++) {
            final var saver = savers.get(w);
            final var writer = w;
            futures.add(pool.submit(() -> {
                start.await();
                for (var i = 0; i < perWriter; i++) {
                    var c = checkpoint("n" + writer, writer + "-" + i);
                    saver.put(cfg, c);
                    expected.add(c.id());
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        var history = List.copyOf(newSaver("concurrent").list(cfg));
        assertEquals(writers * perWriter, history.size());
        assertEquals(expected, history.stream().map(Checkpoint::id).collect(Collectors.toSet()));

        // a single live thread, and a gap-free unique sequence (no checkpoint overwritten)
        var db = client.getDatabase(DB);
        assertEquals(1, db.getCollection("concurrent_thread").countDocuments(new Document("thread_name", "shared")));
        var seqs = new ArrayList<Long>();
        db.getCollection("concurrent_checkpoint").find().forEach(d -> seqs.add(d.getLong("seq")));
        assertEquals(writers * perWriter, new HashSet<>(seqs).size());

        // per writer order is preserved in the history (newest first)
        for (var w = 0; w < writers; w++) {
            final var writer = w;
            var values = history.stream()
                    .filter(c -> c.nodeId().equals("n" + writer))
                    .map(c -> (String) c.state().get("v")).toList();
            var expectedOrder = new ArrayList<String>();
            for (var i = perWriter - 1; i >= 0; i--) expectedOrder.add(writer + "-" + i);
            assertEquals(expectedOrder, values);
        }
    }

    @Test
    void testConcurrentFirstWritersCreateOneThread() throws Exception {
        // many writers racing to create the very first checkpoint of a new thread
        final var writers = 8;
        final var cfg = thread("first-write-race");
        var pool = Executors.newFixedThreadPool(writers);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();
        for (var w = 0; w < writers; w++) {
            final var saver = newSaver("race");
            final var writer = w;
            futures.add(pool.submit(() -> {
                start.await();
                saver.put(cfg, checkpoint("n", writer));
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(writers, newSaver("race").list(cfg).size());
        assertEquals(1, client.getDatabase(DB).getCollection("race_thread")
                .countDocuments(new Document("thread_name", "first-write-race")));
    }

    @Test
    void testSuccessiveRunsReleasedFromDifferentSaversGetIncreasingVersions() throws Exception {
        final var rounds = 6;
        final var cfg = thread("rel");
        for (var i = 0; i < rounds; i++) {
            var saver = newSaver("relver");
            saver.put(cfg, checkpoint("n", i));
            assertEquals(Optional.of(i + 1), saver.release(cfg).version());
        }
        assertEquals(rounds, newSaver("relver").lastTag(cfg).orElseThrow().version().orElseThrow());
    }

    @Test
    void testConcurrentReleasesOfOneRunReleaseItOnce() throws Exception {
        final var releasers = 8;
        final var cfg = thread("rel-race");
        newSaver("relrace").put(cfg, checkpoint("n", "x"));

        var pool = Executors.newFixedThreadPool(releasers);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Optional<Integer>>>();
        for (var i = 0; i < releasers; i++) {
            final var saver = newSaver("relrace");
            futures.add(pool.submit(() -> {
                start.await();
                return saver.release(cfg).version();
            }));
        }
        start.countDown();
        var versions = new ArrayList<Optional<Integer>>();
        for (var f : futures) versions.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();

        // one RELEASED document, and every releaser that saw the run reports the version it got
        assertEquals(1, client.getDatabase(DB).getCollection("relrace_thread")
                .countDocuments(new Document("thread_name", "rel-race")));
        assertTrue(versions.stream().allMatch(v -> v.isEmpty() || v.equals(Optional.of(1))), versions::toString);
        assertTrue(versions.contains(Optional.of(1)));
    }

    @Test
    void testLateReleaseDoesNotReleaseANewerRun() throws Exception {
        var saver = newSaver("stale");
        var cfg = thread("t");
        var threads = client.getDatabase(DB).getCollection("stale_thread");

        saver.put(cfg, checkpoint("n1", "run-1"));
        var run1 = threads.find(new Document("thread_name", "t")).first().getObjectId("_id");
        saver.release(cfg);                       // another process releases run 1 as version 1...
        saver.put(cfg, checkpoint("n1", "run-2")); // ...and run 2 starts

        // a release that loaded run 1 before all this now completes
        var tag = saver.releaseThread(run1, "t", new LinkedList<>(), null, false);

        assertEquals(Optional.of(1), tag.version(), "reports the version run 1 already got");
        var run2 = threads.find(new Document("thread_name", "t").append("status", "ACTIVE")).first();
        assertNotNull(run2, "run 2 must still be live");
        assertEquals(1, saver.list(cfg).size());
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private static Set<String> collectionNames() {
        return client.getDatabase(DB).listCollectionNames().into(new HashSet<>());
    }

    private static List<String> names(Iterable<Document> indexes) {
        var result = new ArrayList<String>();
        indexes.forEach(d -> result.add(d.getString("name")));
        return result;
    }
}
