package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
public class RedisSaverTest extends AbstractCheckpointSaverTest {

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

    @Override
    protected BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception {
        return RedisSaver.builder()
                .host(redisContainer.getHost())
                .port(redisContainer.getMappedPort(REDIS_PORT))
                .stateSerializer( stateSerializer )
                .ttl(30, TimeUnit.MINUTES)
                .build();
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

        NodeAction<AgentState> agent_1 = state -> Map.of("test", "data");

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
