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

import java.io.IOException;
import java.util.*;
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

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @BeforeAll
    static void init() throws IOException {
        try (var is = DynamoDBSaverTest.class.getResourceAsStream("/logging.properties")) {
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

    // ─── Saver factory ───────────────────────────────────────────────────────────

    /**
     * Builds a {@link DynamoDBSaver} pointed at the test container.
     * {@code dropTableFirst=true} ensures a clean table for every test.
     */
    DynamoDBSaver buildSaver(StateSerializerEnum param) {
        String endpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        return DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .region("us-east-1")   // required by the SDK even for local DynamoDB
            .endpointUrl(endpoint)
            .credentialsProvider(  // DynamoDB Local accepts any non-null credentials
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")))
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

        return DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .region("us-east-1")
            .endpointUrl(endpoint)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")))
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

        var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

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
        assertEquals(4, fullHistory.size());

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

        var saver = DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .region("us-east-1")
            .endpointUrl(endpoint)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
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

    /**
     * Verify that a pre-built {@link software.amazon.awssdk.services.dynamodb.DynamoDbClient}
     * injected via {@code .dynamoDbClient(...)} works correctly (e.g. for shared clients
     * managed by the application container).
     */
    @Test
    void testWithInjectedDynamoDbClient() throws Exception {
        String endpoint = "http://" + dynamoContainer.getHost()
                        + ":" + dynamoContainer.getMappedPort(DYNAMODB_PORT);

        // Build the client externally
        var externalClient = software.amazon.awssdk.services.dynamodb.DynamoDbClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .endpointOverride(java.net.URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
            .build();

        var saver = DynamoDBSaver.builder()
            .tableName(TABLE_NAME)
            .stateSerializer(new ObjectStreamStateSerializer<>(State::new))
            .dynamoDbClient(externalClient)  // inject — region/endpoint settings ignored
            .dropTableFirst(true)
            .build();

        var compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .releaseThread(false)
            .build();

        var runnableConfig = RunnableConfig.builder().threadId("thread-injected-client").build();
        var workflow = singleNodeGraph().compile(compileConfig);

        var result = workflow.invoke(GraphInput.args(Map.of("input", "injected-client")), runnableConfig);
        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty(), "Checkpoints must persist with injected client");
        assertEquals(2, history.size());

        saver.release(runnableConfig);

        // Caller is responsible for closing the externally managed client
        externalClient.close();
    }
}
