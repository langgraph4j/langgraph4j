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
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RedisSaver using Testcontainers.
 */
@Testcontainers
public class RedisSaverTest {
    static class MyJacksonStateSerializer extends JacksonStateSerializer<AgentState> {
        public MyJacksonStateSerializer(AgentStateFactory<AgentState> stateFactory ) {
            super(stateFactory);
        }
    }

    public enum StateSerializerEnum {
        BINARY( new ObjectStreamStateSerializer<>( AgentState::new ) ),
        JSON( new MyJacksonStateSerializer( AgentState::new) );

        final StateSerializer<AgentState> stateSerializer;

        StateSerializerEnum(StateSerializer<AgentState> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }

    protected static final int REDIS_PORT = 6379;
    protected static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @Container
    protected static GenericContainer<?> redisContainer = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT);

    @BeforeAll
    public static void setup() {
        // Testcontainers automatically starts the container
        assertTrue(redisContainer.isRunning());
    }

    @AfterAll
    public static void tearDown() {
        // Testcontainers automatically handles cleanup
        redisContainer.close();
    }

    protected RedisSaver createRedisSaver() {
        return RedisSaver.builder()
                .host(redisContainer.getHost())
                .port(redisContainer.getMappedPort(REDIS_PORT))
                .build();
    }

    protected RedisSaver createRedisSaverWithTTL() {
        return RedisSaver.builder()
                .host(redisContainer.getHost())
                .port(redisContainer.getMappedPort(REDIS_PORT))
                .ttl(30, TimeUnit.MINUTES)
                .build();
    }

    protected RedisSaver createRedisSaverWithStateSerializer( StateSerializerEnum param ) {
        return RedisSaver.builder()
                .host(redisContainer.getHost())
                .port(redisContainer.getMappedPort(REDIS_PORT))
                .stateSerializer( param.stateSerializer )
                .build();
    }

    @Test
    public void testCheckpointWithReleasedThread() throws Exception {
        var saver = createRedisSaver();

        NodeAction<AgentState> agent_1 = state ->
                Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(true)
                .build();

        var runnableConfig = RunnableConfig.builder()
                .build();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke( GraphInput.args(inputs), runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertTrue(history.isEmpty());
    }

    @Test
    public void testCheckpointWithNotReleasedThread() throws Exception {
        var saver = createRedisSaver();

        NodeAction<AgentState> agent_1 = state ->
                Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.empty();
        var workflow = graph.compile(compileConfig);

        Map<String, Object> inputs = Map.of("input", "test1");

        var result = workflow.invoke( GraphInput.args(inputs), runnableConfig);

        assertTrue(result.isPresent());

        var history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        var lastSnapshot = workflow.lastStateOf(runnableConfig);

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());

        // UPDATE STATE
        final var updatedConfig = workflow.updateState(
                lastSnapshot.get().config(),
                Map.of("update", "update test"));

        var updatedSnapshot = workflow.stateOf(updatedConfig);
        assertTrue(updatedSnapshot.isPresent());
        assertEquals("agent_1", updatedSnapshot.get().node());
        assertTrue(updatedSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", updatedSnapshot.get().state().value("update").get());
        assertEquals(END, lastSnapshot.get().next());

        // Test checkpoints reloading from Redis
        var saver2 = createRedisSaver();

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver2)
                .releaseThread(false)
                .build();

        runnableConfig = RunnableConfig.empty();
        workflow = graph.compile(compileConfig);

        history = workflow.getStateHistory(runnableConfig);

        assertFalse(history.isEmpty());
        assertEquals(2, history.size());

        lastSnapshot = workflow.stateOf(updatedConfig);

        assertTrue(lastSnapshot.isPresent());
        assertEquals("agent_1", lastSnapshot.get().node());
        assertEquals(END, lastSnapshot.get().next());
        assertTrue(lastSnapshot.get().state().value("update").isPresent());
        assertEquals("update test", lastSnapshot.get().state().value("update").get());

        saver2.release(runnableConfig);
    }

    @Test
    public void testWithInjectedRedissonClient() throws Exception {
        int mappedPort = redisContainer.getMappedPort(REDIS_PORT);
        String redisHost = redisContainer.getHost();

        // Create RedissonClient directly
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + mappedPort);

        RedissonClient redissonClient = org.redisson.Redisson.create(config);
        var saver = RedisSaver.builder().redissonClient(redissonClient).build();

        NodeAction<AgentState> agent_1 = state ->
                Map.of("test", "data");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var workflow = graph.compile(compileConfig);
        workflow.invoke( GraphInput.args(Map.of("input", "test1")), RunnableConfig.empty());

        var history = workflow.getStateHistory(RunnableConfig.empty());
        assertFalse(history.isEmpty(), "Checkpoint should be persisted");

        // User manages the redissonClient lifecycle
        redissonClient.shutdown();
    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointWithStateSerializer( StateSerializerEnum param ) throws Exception {
        var saver = createRedisSaverWithStateSerializer( param );

        NodeAction<AgentState> agent_1 = state -> Map.of("agent_1:prop1", "agent_1:test");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.empty();
        var workflow = graph.compile(compileConfig);

        workflow.invoke( GraphInput.args(Map.of("input", "test-serializer")), runnableConfig);

        var history = workflow.getStateHistory(runnableConfig);
        assertFalse(history.isEmpty());

        var saver2 = createRedisSaverWithStateSerializer( param );
        var workflow2 = graph.compile(CompileConfig.builder().checkpointSaver(saver2).releaseThread(false).build());

        var history2 = workflow2.getStateHistory(runnableConfig);
        assertFalse(history2.isEmpty());

        saver2.release(runnableConfig);
    }

    @Test
    public void testWithCustomKeyNamingStrategy() throws Exception {
        int mappedPort = redisContainer.getFirstMappedPort();
        String redisHost = redisContainer.getHost();

        // Create custom key naming strategy
        KeyNamingStrategy customNaming = new KeyNamingStrategy() {
            @Override
            public String threadKey(String threadId) {
                return "myapp:threads:" + threadId;
            }

            @Override
            public String threadNameKey(String threadName) {
                return "myapp:thread_names:" + threadName;
            }

            @Override
            public String checkpointKey(String checkpointId) {
                return "myapp:checkpoints:" + checkpointId;
            }

            @Override
            public String checkpointsKey(String threadId) {
                return "myapp:thread_checkpoints:" + threadId;
            }

            @Override
            public String keyPrefix() {
                return "myapp:";
            }
        };

        var saver = RedisSaver.builder()
                .host(redisHost)
                .port(mappedPort)
                .keyNamingStrategy(customNaming)
                .build();

        NodeAction<AgentState> agent_1 = state ->
                Map.of("test", "custom_key");

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var workflow = graph.compile(compileConfig);
        workflow.invoke( GraphInput.args(Map.of("input", "test1")), RunnableConfig.empty());

        var history = workflow.getStateHistory(RunnableConfig.empty());
        assertFalse(history.isEmpty(), "Checkpoint with custom keys should be persisted");

        saver.release(RunnableConfig.empty());
    }
}
