package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.testcontainers.containers.MinIOContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DynamoDBSaver} using DynamoDB Local via Testcontainers.
 *
 * <p>Each test method creates a fresh DynamoDB table (via {@code dropTableFirst=true}) so
 * tests are fully isolated. The container is shared across all tests in the class for speed.
 *
 * <p>Run with:
 * <pre>
 *   mvn test -pl langgraph4j-dynamodb-saver -Dtest=DynamoDBSaverTest
 * </pre>
 */
@Testcontainers
public class DynamoDBSaverTest {

    static class State extends AgentState {
        public State(Map<String, Object> initData) {
            super(initData);
        }

        public Optional<List<String>> history() {
            return this.value("history");
        }

    }
    // ─── Serializer variants ─────────────────────────────────────────────────────

    static class MyJacksonStateSerializer extends JacksonStateSerializer<State> {
        public MyJacksonStateSerializer(AgentStateFactory<State> stateFactory) {
            super(stateFactory);
        }
    }

    public enum StateSerializerEnum {
        BINARY(new ObjectStreamStateSerializer<>(State::new)),
        JSON(new MyJacksonStateSerializer(State::new));

        final StateSerializer<State> stateSerializer;

        StateSerializerEnum(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    // ─── Container setup ─────────────────────────────────────────────────────────

    private static final int DYNAMODB_PORT = 8000;
    private static final String TABLE_NAME = "lg4j-test-checkpoints";

    @Container
    static final FixedHostPortGenericContainer<?> dynamoContainer =
        new FixedHostPortGenericContainer<>("amazon/dynamodb-local:latest")
            .withFixedExposedPort(8344, DYNAMODB_PORT)
            .waitingFor(Wait.forLogMessage(".*Initializing DynamoDB Local.*\\n", 1));

    @Container
    static final MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
        .withUserName("minioadmin")
        .withPassword("minioadmin");

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @BeforeAll
    static void init() throws IOException {
        try (var is = DynamoDBSaverTest.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        }
        assertTrue(dynamoContainer.isRunning(), "DynamoDB Local container should be running");
        assertTrue(minioContainer.isRunning(), "MinIO container should be running");
    }

    @AfterAll
    static void shutdown() {
        dynamoContainer.close();
        minioContainer.close();
    }

    // ─── Saver factory ───────────────────────────────────────────────────────────

    /**
     * Builds a {@link DynamoDBSaver} pointed at the test container.
     * {@code dropTableFirst=true} ensures a clean table for every test.
     */
    DynamoDBSaver buildSaver(StateSerializerEnum param) {
        String endpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        var client = software.amazon.awssdk.services.dynamodb.DynamoDbClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
            .httpClientBuilder(ApacheHttpClient.builder()
                // Prevent connection reset errors by aggressively expiring idle connections
                .connectionMaxIdleTime(Duration.ofSeconds(1))
                .connectionTimeToLive(Duration.ofSeconds(10))
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(5)))
            .overrideConfiguration(b -> b
                .apiCallAttemptTimeout(Duration.ofSeconds(6))
                .apiCallTimeout(Duration.ofSeconds(20)))
            .build();

        return DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .dynamoDbClient(client)
            .stateSerializer(param.stateSerializer)
            .dropTableFirst(true)
            .build();
    }

    /**
     * Builds a second saver that reuses the existing table (no drop).
     * Used to verify that checkpoints survive across independent saver instances
     * (i.e., they are truly persisted, not just held in memory).
     */
    DynamoDBSaver buildSaverReuse(StateSerializerEnum param) {
        String endpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        var client = software.amazon.awssdk.services.dynamodb.DynamoDbClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(endpoint))
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

        return DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .dynamoDbClient(client)
            .stateSerializer(param.stateSerializer)
            .createTableIfNotExists(false)  // table already exists
            .build();
    }

    // ─── Test graph helper ───────────────────────────────────────────────────────

    static StateGraph<State> singleNodeGraph() throws Exception {
        NodeAction<State> agent1 = state -> Map.of("agent_1:prop1", "agent_1:test");
        return new StateGraph<>(State::new)
            .addNode("agent_1", node_async(agent1))
            .addEdge(START, "agent_1")
            .addEdge("agent_1", END);
    }

    static StateGraph<State> chatGraph() throws Exception {
        NodeAction<State> chatbot = state -> {
            String userInput = (String) state.value("user_input").orElse("");
            List<String> history = new ArrayList<>(state.history().orElseGet(List::of));

            history.add("User: " + userInput);

            String aiResponse = "I don't know";
            if (userInput.toLowerCase().contains("hi")) {
                aiResponse = "Hi there";
            } else if (userInput.toLowerCase().contains("weather")) {
                aiResponse = "It is bright and sunny here in California";
            }
            history.add("AI: " + aiResponse);

            return Map.of("history", history);
        };

        return new StateGraph<>(State::new)
                .addNode("chatbot", node_async(chatbot))
                .addEdge(START, "chatbot")
                .addEdge("chatbot", END);
    }

    // ─── Tests ───────────────────────────────────────────────────────────────────

    /**
     * After the graph runs with {@code releaseThread=true}, state history must be empty
     * because the thread is marked as released and checkpoints are no longer returned.
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testCheckpointWithReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(true)
            .build();

        var runnableConfig = RunnableConfig.builder().threadId("thread-released").build();
        var workflow = singleNodeGraph().compile(compileConfig);

        var result = workflow.invoke( GraphInput.args(Map.of("input", "test1")), runnableConfig);

        assertTrue(result.isPresent(), "Workflow must produce a result");

        var history = workflow.getStateHistory(runnableConfig);
        assertTrue(history.isEmpty(), "History must be empty after thread is released");
    }

    /**
     * Full lifecycle test: invoke graph, verify history, update state, reload via a fresh
     * saver instance to confirm DynamoDB persistence (not just in-memory cache).
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testCheckpointWithNotReleasedThread(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var runnableConfig = RunnableConfig.builder().threadId("thread-not-released").build();
        var workflow = singleNodeGraph().compile(compileConfig);

        // ── Step 1: invoke ──
        var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);
        assertTrue(result.isPresent());

        // ── Step 2: verify history ──
        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty());
        assertEquals(2, history.size(), "Expected __START__ + agent_1 checkpoints");

        // ── Step 3: inspect last snapshot ──
        var lastSnapshot = workflow.lastStateOf(runnableConfig);
        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());

        // ── Step 4: updateState round-trip ──
        var updatedConfig = workflow.updateState(
            lastSnapshot.get().config(), Map.of("update", "update test"));

        var updatedSnapshot = workflow.stateOf(updatedConfig);
        assertTrue(updatedSnapshot.isPresent());
        assertEquals("agent_1", updatedSnapshot.get().node());
        assertTrue(updatedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", updatedSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        // ── Step 5: fresh saver reloads from DynamoDB ──
        var saver2 = buildSaverReuse(param);
        var workflow2 = singleNodeGraph().compile(
            CompileConfig.builder().checkpointSaver(saver2).releaseThread(false).build());

        var history2 = workflow2.getStateHistory(runnableConfig);
        assertFalse(history2.isEmpty());
        assertEquals(2, history2.size(), "Fresh saver instance must reload same checkpoints");

        var reloadedSnapshot = workflow2.stateOf(updatedConfig);
        assertTrue(reloadedSnapshot.isPresent());
        assertEquals("agent_1", reloadedSnapshot.get().node());
        assertEquals(END, reloadedSnapshot.get().next());
        assertTrue(reloadedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", reloadedSnapshot.get().state().value("update").get());

        saver2.release(runnableConfig);
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testMultiTurnChatbot(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var runnableConfig = RunnableConfig.builder().threadId("thread-chatbot").build();
        var workflow = chatGraph().compile(compileConfig);

        // --- Turn 1: "Hi" ---
        workflow.invoke(GraphInput.args(Map.of("user_input", "Hi")), runnableConfig);

        var state1 = workflow.lastStateOf(runnableConfig).orElseThrow();
        List<String> history1 = state1.state().history().orElseThrow();
        assertEquals(2, history1.size());
        assertEquals("User: Hi", history1.get(0));
        assertEquals("AI: Hi there", history1.get(1));

        // --- Turn 2: "how's the weather" ---
        workflow.invoke(GraphInput.args(Map.of("user_input", "how's the weather")), runnableConfig);

        var state2 = workflow.lastStateOf(runnableConfig).orElseThrow();
        List<String> history2 = state2.state().history().orElseThrow();
        assertEquals(4, history2.size());
        // Verify accumulation
        assertEquals("User: Hi", history2.get(0));
        assertEquals("AI: Hi there", history2.get(1));
        assertEquals("User: how's the weather", history2.get(2));
        assertEquals("AI: It is bright and sunny here in California", history2.get(3));

        // Verify history depth (2 turns * 2 nodes per turn = 4 checkpoints)
        var fullHistory = workflow.getStateHistory(runnableConfig);
        assertEquals(4,  fullHistory.size());

        saver.release(runnableConfig);
    }

    /**
     * Verify that a saver configured with a TTL still writes and reads checkpoints correctly
     * within the TTL window.
     */
    @Test
    void testWithTTL() throws Exception {
        String endpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        var client = software.amazon.awssdk.services.dynamodb.DynamoDbClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
            .build();

        var saver = DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .dynamoDbClient(client)
            .stateSerializer(new ObjectStreamStateSerializer<>(State::new))
            .dropTableFirst(true)
            .ttlSeconds(3600) // 1 hour — items will be available throughout the test
            .build();

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var runnableConfig = RunnableConfig.builder().threadId("thread-ttl").build();
        var workflow = singleNodeGraph().compile(compileConfig);

        var result = workflow.invoke(GraphInput.args(Map.of("input", "ttl-test")), runnableConfig);
        assertTrue(result.isPresent(), "Workflow must produce a result with TTL enabled");

        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty(), "Checkpoints must be readable within the TTL window");
        assertEquals(2, history.size());

        saver.release(runnableConfig);
    }

    // ─── Phase 1.5 Tests ─────────────────────────────────────────────────────

    /**
     * Verify that {@code deleteThread()} removes all checkpoint metadata,
     * payload chunks, and the released marker from DynamoDB.
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testDeleteThread(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var threadId = "thread-delete-test";
        var runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        var workflow = singleNodeGraph().compile(compileConfig);

        // Run the graph to create checkpoints
        workflow.invoke(GraphInput.args(Map.of("input", "test-delete")), runnableConfig);

        // Verify checkpoints exist
        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty(), "Checkpoints should exist before delete");

        // Delete the thread
        saver.deleteThread(threadId);

        // Verify all checkpoints are gone
        var historyAfter = workflow.getStateHistory(runnableConfig);
        assertTrue(historyAfter.isEmpty(), "All checkpoints must be removed after deleteThread");

        // Verify via raw DynamoDB scan that no items remain for this thread
        var scanResponse = saver.getDynamoDbClient().scan(r -> r
                .tableName(TABLE_NAME)
                .filterExpression("contains(PK, :tid)")
                .expressionAttributeValues(Map.of(
                        ":tid", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                                .s(threadId).build()
                ))
        );
        assertEquals(0, scanResponse.count(),
                "No DynamoDB items should remain for the deleted thread");
    }

    /**
     * Verify that calling {@code deleteThread()} on a non-existent thread
     * is a no-op (idempotent).
     */
    @Test
    void testDeleteThreadIdempotent() throws Exception {
        var saver = buildSaver(StateSerializerEnum.BINARY);

        // Should not throw
        assertDoesNotThrow(() -> saver.deleteThread("non-existent-thread-id"));
    }

    /**
     * Verify that checkpoint metadata items carry the correct
     * {@code ref_loc} and {@code ref_key} attributes.
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testRefLocRefKeyAttributes(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var threadId = "thread-refkey-test";
        var runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        var workflow = singleNodeGraph().compile(compileConfig);

        workflow.invoke(GraphInput.args(Map.of("input", "refkey-test")), runnableConfig);

        // Query checkpoint metadata items directly
        var queryResponse = saver.getDynamoDbClient().query(r -> r
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                                .s("CHECKPOINT_" + threadId).build()
                ))
        );

        assertFalse(queryResponse.items().isEmpty(), "Should have checkpoint metadata items");

        for (var item : queryResponse.items()) {
            // Verify ref_loc is present and set to DYNAMODB
            assertTrue(item.containsKey("ref_loc"), "Item should have ref_loc attribute");
            assertEquals("DYNAMODB", item.get("ref_loc").s());

            // Verify ref_key is present and follows CHUNK_{threadId}#{checkpointId} pattern
            assertTrue(item.containsKey("ref_key"), "Item should have ref_key attribute");
            String refKey = item.get("ref_key").s();
            assertTrue(refKey.startsWith("CHUNK_" + threadId + "#"),
                    "ref_key should follow CHUNK_{threadId}#{checkpointId} pattern, got: " + refKey);
        }

        saver.deleteThread(threadId);
    }

    /**
     * Verify that the {@code parentCheckpointId} attribute tracks lineage
     * correctly across multiple graph executions.
     */
    @org.junit.jupiter.api.Disabled("Pending upstream langgraph4j-core support for checkpoint lineage")
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testParentCheckpointIdLineage(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var threadId = "thread-lineage-test";
        var runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        var workflow = chatGraph().compile(compileConfig);

        // Run two turns to create a chain of checkpoints
        workflow.invoke(GraphInput.args(Map.of("user_input", "Hi")), runnableConfig);
        workflow.invoke(GraphInput.args(Map.of("user_input", "weather")), runnableConfig);

        // Query all checkpoint metadata items, sorted by savedAt
        var queryResponse = saver.getDynamoDbClient().query(r -> r
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
                                .s("CHECKPOINT_" + threadId).build()
                ))
        );

        var items = new ArrayList<>(queryResponse.items());
        assertTrue(items.size() >= 2, "Should have at least 2 checkpoints from 2 turns");

        // Sort by savedAt to establish chronological order
        items.sort((a, b) -> {
            long aTime = Long.parseLong(a.get("savedAt").n());
            long bTime = Long.parseLong(b.get("savedAt").n());
            return Long.compare(aTime, bTime);
        });

        // First checkpoint should have no parent
        var firstItem = items.get(0);
        assertFalse(firstItem.containsKey("parentCheckpointId"),
                "First checkpoint should not have a parentCheckpointId");

        // Subsequent checkpoints should have a parentCheckpointId
        boolean foundParent = false;
        for (int i = 1; i < items.size(); i++) {
            if (items.get(i).containsKey("parentCheckpointId")) {
                foundParent = true;
                String parentId = items.get(i).get("parentCheckpointId").s();
                assertNotNull(parentId, "parentCheckpointId should not be null");
                assertFalse(parentId.isEmpty(), "parentCheckpointId should not be empty");
            }
        }
        assertTrue(foundParent, "At least one non-first checkpoint should have a parentCheckpointId");

        saver.deleteThread(threadId);
    }

    /**
     * Verify that conditional writes prevent silent overwrites during inserts,
     * but allow updates when explicitly requested.
     */
    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    void testConditionalWrites(StateSerializerEnum param) throws Exception {
        var saver = buildSaver(param);
        var threadId = "thread-conditional-write";
        var config = RunnableConfig.builder().threadId(threadId).build();

        var state1 = Map.<String, Object>of("key", "value1");
        var state2 = Map.<String, Object>of("key", "value2");

        var checkpoint = Checkpoint.builder()
            .id("fixed-checkpoint-id")
            .nodeId("node1")
            .nextNodeId("node2")
            .state(state1)
            .build();

        // 1. Insert checkpoint (routes to insertedCheckpoint, allowOverwrite=false)
        saver.put(config, checkpoint);

        var getConfig = RunnableConfig.builder()
                .threadId(threadId)
                .checkPointId("fixed-checkpoint-id")
                .build();

        // Verify state is value1
        var loaded = saver.get(getConfig);
        assertTrue(loaded.isPresent());
        assertEquals("value1", loaded.get().getState().get("key"));

        // 2. Try to insert same checkpoint ID with different state
        var modifiedCheckpoint = Checkpoint.builder()
            .id("fixed-checkpoint-id") // same ID!
            .nodeId("node1")
            .nextNodeId("node2")
            .state(state2) // modified state
            .build();

        // config has no checkpointId, routes to insertedCheckpoint (allowOverwrite=false)
        saver.put(config, modifiedCheckpoint);

        // Verify state is still value1, because the conditional write prevented overwrite
        var loaded2 = saver.get(getConfig);
        assertEquals("value1", loaded2.get().getState().get("key"));

        // 3. Now test updatedCheckpoint (allowOverwrite=true)
        // config with checkpointId routes to updatedCheckpoint
        saver.put(getConfig, modifiedCheckpoint);

        // Verify state is now value2
        var loaded3 = saver.get(getConfig);
        assertEquals("value2", loaded3.get().getState().get("key"));

        saver.deleteThread(threadId);
    }

    /**
     * Verify that payloads larger than 350KB are automatically offloaded to S3.
     */
    @Test
    void testWithS3Offloading() throws Exception {
        String dynamoEndpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        var dynamoClient = software.amazon.awssdk.services.dynamodb.DynamoDbClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(dynamoEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
            .build();

        var s3Client = S3Client.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(minioContainer.getS3URL()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minioContainer.getUserName(), minioContainer.getPassword())))
            .forcePathStyle(true) // Required for MinIO
            .build();

        String s3Bucket = "test-checkpoints-bucket";
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(s3Bucket).build());
            } else {
                throw e;
            }
        }

        var saver = DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .dynamoDbClient(dynamoClient)
            .s3Bucket(s3Bucket)
            .s3Client(s3Client)
            .stateSerializer(new ObjectStreamStateSerializer<>(State::new))
            .dropTableFirst(true)
            .build();

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var threadId = "thread-s3-offload";
        var runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        var workflow = singleNodeGraph().compile(compileConfig);

        // Create a large payload > 350KB to trigger offload
        byte[] largeData = new byte[400 * 1024];
        Arrays.fill(largeData, (byte) 'a');
        String largeString = new String(largeData, StandardCharsets.UTF_8);

        var result = workflow.invoke(GraphInput.args(Map.of("input", largeString)), runnableConfig);
        assertTrue(result.isPresent());

        // Verify history is retrievable (meaning it successfully fetches from S3)
        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty(), "History must be retrievable");
        
        // Ensure state contains largeString
        var lastState = workflow.lastStateOf(runnableConfig).orElseThrow();
        assertEquals(largeString, lastState.state().value("input").orElse(""));

        // Cleanup
        saver.deleteThread(threadId);
        s3Client.close();
    }
}
