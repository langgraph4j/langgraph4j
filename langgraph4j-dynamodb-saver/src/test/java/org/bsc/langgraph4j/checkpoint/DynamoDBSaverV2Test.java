package org.bsc.langgraph4j.checkpoint;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Integration tests for {@link DynamoDBSaverV2} and {@link DynamoDBSaverV2Dashboard} using
 * DynamoDB Local via Testcontainers.
 *
 * <p>The inherited {@link AbstractCheckpointSaverTest} tests share one table; the V2-specific
 * tests start from a fresh table ({@code dropTableFirst}).
 *
 * <p>Run with:
 * <pre>
 *   ./mvnw -pl langgraph4j-dynamodb-saver -am test -DskipTests=false -Dtest=DynamoDBSaverV2Test -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@Testcontainers
public class DynamoDBSaverV2Test extends AbstractCheckpointSaverTest {

    private static final int DYNAMODB_PORT = 8000;
    private static final String TABLE_NAME = "lg4j-test-checkpoints-v2";

    @Container
    static final FixedHostPortGenericContainer<?> dynamoContainer =
            new FixedHostPortGenericContainer<>("amazon/dynamodb-local:latest")
                    .withFixedExposedPort(8346, DYNAMODB_PORT) // V1 tests use 8344 and 8345
                    .waitingFor(Wait.forLogMessage(".*Initializing DynamoDB Local.*\\n", 1));

    @BeforeAll
    static void init() throws IOException {
        try (var is = DynamoDBSaverV2Test.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        }
        assertTrue(dynamoContainer.isRunning(), "DynamoDB Local container should be running");
    }

    @AfterAll
    static void shutdown() {
        dynamoContainer.close();
    }

    // ─── Factories ───────────────────────────────────────────────────────────────

    static DynamoDbClient client() {
        final var endpoint = "http://%s:%d".formatted(
                dynamoContainer.getHost(),
                dynamoContainer.getMappedPort(DYNAMODB_PORT));

        return DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionMaxIdleTime(Duration.ofSeconds(1))
                        .connectionTimeToLive(Duration.ofSeconds(10))
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(b -> b
                        .apiCallAttemptTimeout(Duration.ofSeconds(6))
                        .apiCallTimeout(Duration.ofSeconds(20)))
                .build();
    }

    /** A saver on a fresh, empty table. */
    static DynamoDBSaverV2 freshSaver(StateSerializer<? extends AgentState> stateSerializer) {
        return DynamoDBSaverV2.builder()
                .tableName(TABLE_NAME)
                .dynamoDbClient(client())
                .stateSerializer(stateSerializer)
                .dropTableFirst(true)
                .build();
    }

    /** Another saver instance on the existing table, as a second process would use it. */
    static DynamoDBSaverV2 reuseSaver(StateSerializer<? extends AgentState> stateSerializer) {
        return DynamoDBSaverV2.builder()
                .tableName(TABLE_NAME)
                .dynamoDbClient(client())
                .stateSerializer(stateSerializer)
                .build();
    }

    static DynamoDBSaverV2Dashboard dashboard(StateSerializer<? extends AgentState> stateSerializer) {
        return DynamoDBSaverV2Dashboard.builder()
                .tableName(TABLE_NAME)
                .dynamoDbClient(client())
                .stateSerializer(stateSerializer)
                .build();
    }

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer,
                                                       @Nullable String threadId) {
        return DynamoDBSaverV2.builder()
                .tableName(TABLE_NAME)
                .dynamoDbClient(client())
                .stateSerializer(stateSerializer)
                .createTableIfNotExists(true)
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    StateGraph<State> singleNodeGraph(StateSerializerEnum param) throws Exception {
        return new StateGraph<>(State.SCHEMA, param.stateSerializer)
                .addNode("agent_1", CustomNodeAction.of("agent_1"))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);
    }

    StateGraph<State> twoNodeGraph(StateSerializerEnum param) throws Exception {
        return new StateGraph<>(State.SCHEMA, param.stateSerializer)
                .addNode("agent_1", CustomNodeAction.of("agent_1"))
                .addNode("agent_2", CustomNodeAction.of("agent_2"))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", END);
    }

    static RunnableConfig thread(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    static List<Map<String, AttributeValue>> allItems(AbstractDynamoDBSaverV2 saver) {
        return saver.getDynamoDbClient().scan(r -> r.tableName(TABLE_NAME).consistentRead(true)).items();
    }

    static long countItems(AbstractDynamoDBSaverV2 saver, String pkPrefix) {
        return allItems(saver).stream()
                .filter(item -> item.get("PK").s().startsWith(pkPrefix))
                .count();
    }

    static Set<String> ids(BaseCheckpointSaver.Tag tag) {
        final Set<String> ids = new HashSet<>();
        tag.checkpoints().forEach(cp -> ids.add(cp.id()));
        return ids;
    }

    // ─── Release and tags ────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testReleaseCreatesVersionedTags(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var config = thread("thread-tags");
        final var workflow = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true)
                .build());

        for (int run = 1; run <= 3; run++) {
            assertTrue(workflow.invoke(GraphInput.args(Map.of("input", "run" + run)), config).isPresent());
            // released: the next execution starts empty
            assertTrue(workflow.getStateHistory(config).isEmpty());
        }

        final var tag1 = saver.tag(config, 1).orElseThrow();
        final var tag2 = saver.tag(config, 2).orElseThrow();
        final var tag3 = saver.tag(config, 3).orElseThrow();

        assertEquals(1, tag1.version().orElseThrow());
        assertEquals(2, tag2.version().orElseThrow());
        assertEquals(3, tag3.version().orElseThrow());
        assertFalse(tag1.checkpoints().isEmpty());
        assertFalse(tag2.checkpoints().isEmpty());
        assertFalse(tag3.checkpoints().isEmpty());

        // every execution kept its own checkpoints
        final var seen = new HashSet<String>();
        assertTrue(seen.addAll(ids(tag1)));
        assertTrue(seen.addAll(ids(tag2)));
        assertTrue(seen.addAll(ids(tag3)));
        assertEquals(ids(tag1).size() + ids(tag2).size() + ids(tag3).size(), seen.size());

        // latest tag and missing versions
        assertEquals(3, saver.lastTag(config).orElseThrow().version().orElseThrow());
        assertTrue(saver.tag(config, 4).isEmpty());
        assertTrue(saver.lastTag(thread("never-released")).isEmpty());

        // tags are persisted, not held in memory
        final var other = reuseSaver(param.stateSerializer);
        assertEquals(ids(tag2), ids(other.tag(config, 2).orElseThrow()));

        // no ACTIVE item left: 3 tags under THREAD_thread-tags
        assertEquals(3, countItems(saver, "THREAD_thread-tags"));
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testVersionsArePerThreadName(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var workflow = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .build());

        workflow.invoke(GraphInput.args(Map.of("input", "a")), thread("thread-a"));
        workflow.invoke(GraphInput.args(Map.of("input", "a")), thread("thread-a"));
        workflow.invoke(GraphInput.args(Map.of("input", "b")), thread("thread-b"));

        assertEquals(2, saver.lastTag(thread("thread-a")).orElseThrow().version().orElseThrow());
        assertEquals(1, saver.lastTag(thread("thread-b")).orElseThrow().version().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testReleaseOnErrorIsRecorded(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var config = thread("thread-error");
        final var workflow = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        workflow.invoke(GraphInput.args(Map.of("input", "x")), config);
        final var tag = saver.releaseOnError(config, new IllegalStateException("simulated failure"));

        assertEquals(1, tag.version().orElseThrow());
        assertFalse(tag.checkpoints().isEmpty());
        assertTrue(workflow.getStateHistory(config).isEmpty());

        final var tags = dashboard(param.stateSerializer).selectAllTags();
        assertEquals(1, tags.size());
        assertTrue(tags.get(0).isError());
        assertEquals("simulated failure", tags.get(0).message());
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testReleaseWithoutActiveThread(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);

        final var tag = saver.release(thread("thread-empty"));

        assertTrue(tag.version().isEmpty());
        assertTrue(tag.checkpoints().isEmpty());
        assertEquals(0, allItems(saver).size());
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testConcurrentReleaseCreatesOneTag(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var config = thread("thread-race");
        singleNodeGraph(param).compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .releaseThread(false)
                        .build())
                .invoke(GraphInput.args(Map.of("input", "x")), config);

        // two saver instances (separate locks), as two processes releasing the same execution
        final var savers = List.of(reuseSaver(param.stateSerializer), reuseSaver(param.stateSerializer));
        final List<Callable<BaseCheckpointSaver.Tag>> releases = new ArrayList<>();
        savers.forEach(s -> releases.add(() -> s.release(config)));

        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var versions = new ArrayList<Integer>();
            for (var future : executor.invokeAll(releases)) {
                future.get().version().ifPresent(versions::add);
            }
            // exactly one release archived the execution
            assertEquals(List.of(1), versions);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, countItems(saver, "THREAD_thread-race"));
        assertEquals(1, dashboard(param.stateSerializer).selectAllTags().size());
    }

    // ─── Checkpoint history ──────────────────────────────────────────────────────

    @Test
    void testHistoryIsOrderedBySeq() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        // no checkpoint id in the config: every put is an insert
        final var config = thread("thread-order");

        final var inserted = new ArrayList<String>();
        for (int step = 0; step < 5; step++) {
            final var checkpoint = Checkpoint.builder()
                    .state(Map.of("step", step))
                    .nodeId("node_" + step)
                    .nextNodeId("next")
                    .build();
            saver.put(config, checkpoint);
            inserted.add(0, checkpoint.id());   // newest first
        }

        // inserted within the same milliseconds: the order comes from seq, not savedAt
        final var loaded = reuseSaver(StateSerializerEnum.JSON.stateSerializer)
                .list(thread("thread-order")).stream().map(Checkpoint::id).toList();
        assertEquals(inserted, loaded);
    }

    @Test
    void testUpdateKeepsPosition() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        // no checkpoint id in the config: every put is an insert
        final var config = thread("thread-update");

        final var ids = new ArrayList<String>();
        for (int step = 0; step < 3; step++) {
            final var checkpoint = Checkpoint.builder()
                    .state(Map.of("step", step))
                    .nodeId("node_" + step)
                    .nextNodeId("next")
                    .build();
            saver.put(config, checkpoint);
            ids.add(0, checkpoint.id());
        }

        // replace the middle checkpoint
        final var middle = ids.get(1);
        final var replacement = Checkpoint.builder()
                .id(middle)
                .state(Map.of("step", 99))
                .nodeId("node_1")
                .nextNodeId("next")
                .build();
        // a checkpoint id in the config: this put is an update
        saver.put(RunnableConfig.builder(config).checkPointId(middle).build(), replacement);

        final var loaded = List.copyOf(reuseSaver(StateSerializerEnum.JSON.stateSerializer).list(thread("thread-update")));
        assertEquals(ids, loaded.stream().map(Checkpoint::id).toList());
        assertEquals(99, loaded.get(1).state().get("step"));
    }

    // ─── Interruption ────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testInterruptionIsRecordedAndClearedOnResume(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var dashboard = dashboard(param.stateSerializer);
        final var config = thread("thread-hitl");
        final var workflow = twoNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("agent_2")
                .build());

        // paused before agent_2: the live thread is flagged
        workflow.invoke(GraphInput.noArgs(), config);

        var threads = dashboard.selectAllThreads();
        assertEquals(1, threads.size());
        assertEquals("thread-hitl", threads.get(0).name());
        assertTrue(threads.get(0).isInterrupted());
        assertNotNull(threads.get(0).message());
        assertTrue(threads.get(0).message().contains("agent_2"));

        // resumed: runs to END and is released
        workflow.invoke(GraphInput.resume(), config);

        assertTrue(dashboard.selectAllThreads().isEmpty());
        final var tags = dashboard.selectAllTags();
        assertEquals(1, tags.size());
        assertFalse(tags.get(0).isError());
        assertEquals(1, tags.get(0).version());
    }

    // ─── Dashboard ───────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testDashboardListsThreadsAndTags(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var dashboard = dashboard(param.stateSerializer);

        final var released = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .build());
        final var kept = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        released.invoke(GraphInput.args(Map.of("input", "1")), thread("released-1"));
        released.invoke(GraphInput.args(Map.of("input", "2")), thread("released-2"));
        kept.invoke(GraphInput.args(Map.of("input", "3")), thread("live-1"));

        final var threads = dashboard.selectAllThreads();
        assertEquals(1, threads.size());
        assertEquals("live-1", threads.get(0).name());
        assertFalse(threads.get(0).isInterrupted());

        final var tags = dashboard.selectAllTags();
        assertEquals(2, tags.size());
        // newest release first
        assertEquals("released-2", tags.get(0).name());
        assertEquals("released-1", tags.get(1).name());
        assertTrue(tags.stream().allMatch(DynamoDBSaverV2Dashboard.TagRecord::isReleased));

        // the inherited read path works on the dashboard
        assertFalse(dashboard.list(thread("live-1")).isEmpty());
        assertFalse(dashboard.tag(thread("released-1"), 1).orElseThrow().checkpoints().isEmpty());

        // the index is sparse: only thread and tag items carry GSI1PK
        final long indexed = allItems(saver).stream().filter(item -> item.containsKey("GSI1PK")).count();
        assertEquals(threads.size() + tags.size(), indexed);
    }

    @Test
    void testDashboardIsReadOnly() {
        freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var dashboard = dashboard(StateSerializerEnum.JSON.stateSerializer);
        final var config = thread("thread-readonly");
        final var checkpoint = Checkpoint.builder()
                .state(Map.of("step", 1))
                .nodeId("node")
                .nextNodeId("next")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> dashboard.put(config, checkpoint));
        assertThrows(UnsupportedOperationException.class, () -> dashboard.release(config));
        assertThrows(UnsupportedOperationException.class, () -> dashboard.deleteThread("thread-readonly"));
    }

    // ─── GSI sort keys ───────────────────────────────────────────────────────────

    static Map<String, AttributeValue> getItem(AbstractDynamoDBSaverV2 saver, String pk, String sk) {
        return saver.getDynamoDbClient().getItem(r -> r
                .tableName(TABLE_NAME)
                .key(Map.of(
                        "PK", AttributeValue.builder().s(pk).build(),
                        "SK", AttributeValue.builder().s(sk).build()))
                .consistentRead(true)).item();
    }

    static Checkpoint checkpoint(int step) {
        return Checkpoint.builder()
                .state(Map.of("step", step))
                .nodeId("node_" + step)
                .nextNodeId("next")
                .build();
    }

    @Test
    void testThreadGsiSortKeyIsSetOnceAndKept() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var config = thread("thread-gsi-stable");

        saver.put(config, checkpoint(1));
        final var first = getItem(saver, "THREAD_thread-gsi-stable", "ACTIVE");

        Thread.sleep(20);
        saver.put(config, checkpoint(2));
        final var second = getItem(saver, "THREAD_thread-gsi-stable", "ACTIVE");

        // GSI1SK is derived from createdAt and written with if_not_exists:
        // more checkpoints do not move the thread in the dashboard order
        assertEquals(first.get("GSI1SK").s(), second.get("GSI1SK").s());
        assertEquals(first.get("createdAt").n(), second.get("createdAt").n());
        assertEquals("THREAD", second.get("GSI1PK").s());
        assertTrue(second.get("GSI1SK").s().startsWith("%013d#".formatted(Long.parseLong(first.get("createdAt").n()))));
        assertTrue(second.get("GSI1SK").s().endsWith("#thread-gsi-stable"));
        // the seq counter did move
        assertEquals("2", second.get("seq").n());
    }

    @Test
    void testDashboardThreadsNewestFirst() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var dashboard = dashboard(StateSerializerEnum.JSON.stateSerializer);

        // created in this order; within the same millisecond the name breaks the tie the same way
        for (var name : List.of("live-a", "live-b", "live-c")) {
            saver.put(thread(name), checkpoint(1));
            Thread.sleep(5);
        }

        assertEquals(List.of("live-c", "live-b", "live-a"),
                dashboard.selectAllThreads().stream().map(DynamoDBSaverV2Dashboard.ThreadRecord::name).toList());
    }

    @Test
    void testTagGsiSortKeyAndOrderOfOneThread() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var dashboard = dashboard(StateSerializerEnum.JSON.stateSerializer);
        final var config = thread("thread-many-tags");

        for (int run = 1; run <= 3; run++) {
            saver.put(config, checkpoint(run));
            saver.release(config);
        }

        // newest release first
        assertEquals(List.of(3, 2, 1),
                dashboard.selectAllTags().stream().map(DynamoDBSaverV2Dashboard.TagRecord::version).toList());

        final var tag2 = getItem(saver, "THREAD_thread-many-tags", "V#0000000002");
        assertEquals("TAG", tag2.get("GSI1PK").s());
        assertEquals(DynamoDBRepositoryV2.tagGsiSK(
                        Long.parseLong(tag2.get("releasedAt").n()), "thread-many-tags", 2),
                tag2.get("GSI1SK").s());

        // releasing deleted ACTIVE, so the thread left the THREAD side of the index
        assertTrue(dashboard.selectAllThreads().isEmpty());
    }

    @Test
    void testGsiRangeQueryOnReleaseTime() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);

        saver.put(thread("released-early"), checkpoint(1));
        saver.release(thread("released-early"));
        Thread.sleep(20);

        final long from = System.currentTimeMillis();
        saver.put(thread("released-in-window"), checkpoint(1));
        saver.release(thread("released-in-window"));
        final long to = System.currentTimeMillis();

        Thread.sleep(20);
        saver.put(thread("released-late"), checkpoint(1));
        saver.release(thread("released-late"));

        // an ad-hoc query on the same index, outside the dashboard API:
        // "which executions were released between from and to?"
        final var response = saver.getDynamoDbClient().query(r -> r
                .tableName(TABLE_NAME)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :tag AND GSI1SK BETWEEN :from AND :to")
                .expressionAttributeValues(Map.of(
                        ":tag", AttributeValue.builder().s("TAG").build(),
                        ":from", AttributeValue.builder().s("%013d".formatted(from)).build(),
                        ":to", AttributeValue.builder().s("%013d#￿".formatted(to)).build())));

        assertEquals(List.of("released-in-window"),
                response.items().stream().map(item -> item.get("threadName").s()).toList());
    }

    // ─── Stored items ────────────────────────────────────────────────────────────

    /** Puts two checkpoints (steps 1 and 2) on a new live thread; returns their ids, oldest first. */
    static List<String> liveThreadWithTwoCheckpoints(DynamoDBSaverV2 saver, String threadName) throws Exception {
        final var config = thread(threadName);
        final var first = checkpoint(1);
        final var second = checkpoint(2);
        saver.put(config, first);
        saver.put(config, second);
        return List.of(first.id(), second.id());
    }

    static Object decodedStep(Map<String, AttributeValue> chunk) throws Exception {
        final var json = new String(chunk.get("payload").b().asByteArray(), StandardCharsets.UTF_8);
        return StateSerializerEnum.JSON.stateSerializer.readDataFromString(json).data().get("step");
    }

    @Test
    void testStoredItemsOfLiveThread() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var ids = liveThreadWithTwoCheckpoints(saver, "thread-items");

        // 1 ACTIVE + 2 checkpoint metadata + 2 chunks, nothing else
        assertEquals(5, allItems(saver).size());

        // ── ACTIVE ──
        final var active = getItem(saver, "THREAD_thread-items", "ACTIVE");
        assertFalse(active.isEmpty());
        assertEquals("thread-items", active.get("threadName").s());
        final var uid = active.get("threadUid").s();
        assertDoesNotThrow(() -> UUID.fromString(uid), "threadUid must be a UUID");
        assertEquals("2", active.get("seq").n());
        assertFalse(active.get("isInterrupted").bool());
        assertFalse(active.containsKey("message"));
        assertTrue(Long.parseLong(active.get("createdAt").n()) > 0);
        assertEquals("THREAD", active.get("GSI1PK").s());
        assertFalse(active.containsKey("ttl"), "no ttl unless ttlSeconds is configured");

        // ── checkpoint metadata + chunks ──
        for (int i = 0; i < ids.size(); i++) {
            final var checkpointId = ids.get(i);
            final var meta = getItem(saver, "CHECKPOINT_" + uid, checkpointId);

            assertFalse(meta.isEmpty(), "checkpoint metadata " + checkpointId);
            assertEquals(checkpointId, meta.get("checkpointId").s());
            assertEquals("node_" + (i + 1), meta.get("nodeId").s());
            assertEquals("next", meta.get("nextNodeId").s());
            assertEquals(StateSerializerEnum.JSON.stateSerializer.contentType(), meta.get("contentType").s());
            assertEquals(String.valueOf(i + 1), meta.get("seq").n(), "seq follows insertion order");
            assertTrue(Long.parseLong(meta.get("savedAt").n()) > 0);
            assertEquals("DYNAMODB", meta.get("ref_loc").s());
            assertEquals("CHUNK_" + uid + "#" + checkpointId, meta.get("ref_key").s());
            assertFalse(meta.containsKey("parentCheckpointId"), "inserted without a checkpoint id in the config");
            assertFalse(meta.containsKey("GSI1PK"), "checkpoints must stay out of the sparse index");
            assertFalse(meta.containsKey("payload"), "the state lives in the chunk, not in the metadata");

            final var chunk = getItem(saver, meta.get("ref_key").s(), "CHUNK");
            assertFalse(chunk.isEmpty(), "chunk at ref_key " + meta.get("ref_key").s());
            assertEquals(i + 1, decodedStep(chunk), "payload decodes back to the saved state");
            assertFalse(chunk.containsKey("GSI1PK"));
        }
    }

    @Test
    void testStoredItemsAfterRelease() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var ids = liveThreadWithTwoCheckpoints(saver, "thread-archived");

        final var active = getItem(saver, "THREAD_thread-archived", "ACTIVE");
        final var uid = active.get("threadUid").s();
        final var checkpointsBefore = new ArrayList<Map<String, AttributeValue>>();
        for (var id : ids) {
            checkpointsBefore.add(getItem(saver, "CHECKPOINT_" + uid, id));
            checkpointsBefore.add(getItem(saver, "CHUNK_" + uid + "#" + id, "CHUNK"));
        }

        final var tag = saver.release(thread("thread-archived"));
        assertEquals(1, tag.version().orElseThrow());

        // ACTIVE replaced by the tag: still 5 items
        assertTrue(getItem(saver, "THREAD_thread-archived", "ACTIVE").isEmpty());
        assertEquals(5, allItems(saver).size());

        // ── the tag ──
        final var stored = getItem(saver, "THREAD_thread-archived", "V#0000000001");
        assertFalse(stored.isEmpty());
        assertEquals("thread-archived", stored.get("threadName").s());
        assertEquals(uid, stored.get("threadUid").s(), "the tag keeps the uid of the released execution");
        assertEquals("1", stored.get("version").n());
        assertFalse(stored.get("isError").bool());
        assertFalse(stored.containsKey("message"), "no message on a normal release");
        assertEquals(active.get("createdAt").n(), stored.get("createdAt").n(), "createdAt copied from ACTIVE");
        assertTrue(Long.parseLong(stored.get("releasedAt").n()) >= Long.parseLong(stored.get("createdAt").n()));
        assertEquals("TAG", stored.get("GSI1PK").s());
        assertFalse(stored.containsKey("seq"), "the seq counter belongs to the live execution only");
        assertFalse(stored.containsKey("isInterrupted"));

        // ── checkpoints and chunks did not move or change ──
        final var checkpointsAfter = new ArrayList<Map<String, AttributeValue>>();
        for (var id : ids) {
            checkpointsAfter.add(getItem(saver, "CHECKPOINT_" + uid, id));
            checkpointsAfter.add(getItem(saver, "CHUNK_" + uid + "#" + id, "CHUNK"));
        }
        assertEquals(checkpointsBefore, checkpointsAfter);

        // ── the next execution of the same name gets a new uid and starts at seq 1 ──
        saver.put(thread("thread-archived"), checkpoint(3));
        final var next = getItem(saver, "THREAD_thread-archived", "ACTIVE");
        assertNotEquals(uid, next.get("threadUid").s());
        assertEquals("1", next.get("seq").n());
    }

    @Test
    void testInterruptionFlagsResetOnNextCheckpoint() throws Exception {
        final var saver = freshSaver(StateSerializerEnum.JSON.stateSerializer);
        final var config = thread("thread-flags");
        twoNodeGraph(StateSerializerEnum.JSON).compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("agent_2")
                        .build())
                .invoke(GraphInput.noArgs(), config);

        final var interrupted = getItem(saver, "THREAD_thread-flags", "ACTIVE");
        assertTrue(interrupted.get("isInterrupted").bool());
        assertTrue(interrupted.get("message").s().contains("agent_2"));

        // the execution moves on: the flag is reset and the reason removed (not set to empty)
        saver.put(config, checkpoint(9));

        final var resumed = getItem(saver, "THREAD_thread-flags", "ACTIVE");
        assertFalse(resumed.get("isInterrupted").bool());
        assertFalse(resumed.containsKey("message"));
        assertEquals(interrupted.get("threadUid").s(), resumed.get("threadUid").s(), "same execution");
    }

    @Test
    void testTtlOnEveryItem() throws Exception {
        final long ttlSeconds = 3600;
        final var saver = DynamoDBSaverV2.builder()
                .tableName(TABLE_NAME)
                .dynamoDbClient(client())
                .stateSerializer(StateSerializerEnum.JSON.stateSerializer)
                .ttlSeconds(ttlSeconds)
                .dropTableFirst(true)
                .build();
        final var config = thread("thread-ttl");
        final long start = Instant.now().getEpochSecond();

        saver.put(config, checkpoint(1));
        final long firstActiveTtl = Long.parseLong(getItem(saver, "THREAD_thread-ttl", "ACTIVE").get("ttl").n());

        Thread.sleep(1_100);   // ttl has a 1-second resolution
        saver.put(config, checkpoint(2));
        final long secondActiveTtl = Long.parseLong(getItem(saver, "THREAD_thread-ttl", "ACTIVE").get("ttl").n());
        assertTrue(secondActiveTtl > firstActiveTtl, "the live thread refreshes its ttl on every checkpoint");

        saver.release(config);
        final long end = Instant.now().getEpochSecond();

        // tag + 2 checkpoint metadata + 2 chunks, each with ttl = write time + ttlSeconds
        final var items = allItems(saver);
        assertEquals(5, items.size());
        for (var item : items) {
            final var where = item.get("PK").s() + " / " + item.get("SK").s();
            assertTrue(item.containsKey("ttl"), "ttl missing on " + where);
            final long ttl = Long.parseLong(item.get("ttl").n());
            assertTrue(ttl >= start + ttlSeconds && ttl <= end + ttlSeconds, "ttl out of range on " + where);
        }
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testDeleteThreadRemovesEveryExecution(StateSerializerEnum param) throws Exception {
        final var saver = freshSaver(param.stateSerializer);
        final var config = thread("thread-delete");

        final var released = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .build());
        final var kept = singleNodeGraph(param).compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build());

        released.invoke(GraphInput.args(Map.of("input", "1")), config);   // tag 1
        released.invoke(GraphInput.args(Map.of("input", "2")), config);   // tag 2
        kept.invoke(GraphInput.args(Map.of("input", "3")), config);       // ACTIVE
        kept.invoke(GraphInput.args(Map.of("input", "x")), thread("other-thread"));

        final long otherItems = allItems(saver).stream()
                .filter(item -> !item.get("PK").s().equals("THREAD_thread-delete"))
                .count();
        assertTrue(otherItems > 0);

        saver.deleteThread("thread-delete");

        assertEquals(0, countItems(saver, "THREAD_thread-delete"));
        assertTrue(saver.tag(config, 1).isEmpty());
        assertTrue(saver.list(config).isEmpty());
        // only the other thread is left: its ACTIVE item, checkpoints and chunks
        assertFalse(saver.list(thread("other-thread")).isEmpty());
        final var remaining = allItems(saver);
        final var otherUid = remaining.stream()
                .filter(item -> item.get("PK").s().equals("THREAD_other-thread"))
                .findFirst().orElseThrow()
                .get("threadUid").s();
        assertTrue(remaining.stream().allMatch(item -> {
            final var pk = item.get("PK").s();
            return pk.equals("THREAD_other-thread") || pk.contains(otherUid);
        }));

        // deleting a missing thread is a no-op
        saver.deleteThread("thread-delete");
    }
}
