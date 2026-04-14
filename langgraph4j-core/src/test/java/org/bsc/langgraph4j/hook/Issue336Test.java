package org.bsc.langgraph4j.hook;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Issue #336: AfterHook should be called after streaming completes
 *
 * Problem: When a Node returns an AsyncGenerator (streaming), the AfterHook.applyAfter()
 * was called immediately when the generator was created, before any streaming had occurred.
 *
 * Solution: Skip AfterHook in applyActionWithHooks when AsyncGenerator is detected,
 * and call AfterHook in embedGenerator callback after streaming completes.
 */
public class Issue336Test implements LG4JLoggable {


    static class State extends AgentState {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        List<String> values() {
            return this.<List<String>>value("VALUE").orElseThrow();
        }

        Optional<String> auditHook() {
            return value("AuditHook" );
        }
    }

    public enum StreamNodeEnum {
        SYNC(Issue336Test::createSyncStreamingNode),
        ASYNC(Issue336Test::createAsyncStreamingNode);

        final BiFunction<String, List<String>, AsyncNodeActionWithConfig<State>> actionFactory;

        StreamNodeEnum(BiFunction<String, List<String>, AsyncNodeActionWithConfig<State>> actionFactory) {
            this.actionFactory = actionFactory;
        }
    }

    /**
     * A hook that records when it was called and what result it received.
     */
    static class AuditHook<State extends AgentState> implements NodeHook.AfterCall<State> {

        final AtomicReference<String> calledAtNode = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> capturedResult = new AtomicReference<>();
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public CompletableFuture<Map<String, Object>> applyAfter(
                String nodeId,
                State state,
                RunnableConfig config,
                Map<String, Object> result) {

            calledAtNode.set(nodeId);
            capturedResult.set(result);
            callCount.incrementAndGet();

            return completedFuture( mergeMap(result, Map.of("AuditHook", "called")));
        }

    }

    private static AsyncNodeActionWithConfig<State> createAsyncStreamingNode(String nodeId, List<String> tokens) {
        return (state, config) -> {
            BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue = new LinkedBlockingQueue<>();

            // Start streaming in a separate thread
            CompletableFuture.runAsync(() -> {
                try {
                    for (String token : tokens) {
                        queue.add(AsyncGenerator.Data.of(new StreamingOutput<>(token, nodeId, state, null)));
                        Thread.sleep(10);
                    }
                    // Send completion with final result
                    queue.add(AsyncGenerator.Data.done(Map.of("VALUE", "streaming_completed")));
                } catch (InterruptedException e) {
                    queue.add(AsyncGenerator.Data.error(e));
                }
            });

            var generator = new AsyncGeneratorQueue.Generator<>(queue);
            return completedFuture(Map.of("content", generator));
        };
    }

    /**
     * Creates a streaming node using AsyncGeneratorQueue.Generator.
     * Simulates LLM streaming by pushing tokens to a queue.
     */
    private static AsyncNodeActionWithConfig<State> createSyncStreamingNode(String nodeId, List<String> tokens) {
        return (state, config) -> {

            final var iterator = tokens.iterator();
            final var generator = new AsyncGenerator.Base<StreamingOutput<State>>() {
                @Override
                public Data<StreamingOutput<State>> next() {
                    if (iterator.hasNext()) {
                        return AsyncGenerator.Data.of(new StreamingOutput<>(iterator.next(), nodeId, state, null));
                    }
                    return AsyncGenerator.Data.done(Map.of("VALUE", "streaming_completed"));
                }

            };
            return completedFuture(Map.of("content", generator));
        };
    }

    /**
     * Creates a blocking node that returns immediately.
     */
    private AsyncNodeActionWithConfig<State> createBlockingNode( String value ) {
        return (state, config) -> completedFuture(Map.of("VALUE", value));
    }

    @Test
    public void testBlockingNodeAfterHookCalledImmediately() throws Exception {
        // Given
        final Map<String, Channel<?>> schema = Map.of("VALUE", Channels.appender(ArrayList::new));
        final var auditHook = new AuditHook<State>();

        var workflow = new StateGraph<>(schema, State::new)
                .addAfterCallNodeHook(auditHook)
                .addNode("blocking_node", createBlockingNode( "test_value"))
                .addEdge(START, "blocking_node")
                .addEdge("blocking_node", END)
                .compile();

        // When
        final var result = workflow.invoke( GraphInput.noArgs(), RunnableConfig.builder().build());

        // Then
        assertTrue(result.isPresent());
        assertTrue(result.get().auditHook().isPresent());
        assertEquals( "called", result.get().auditHook().get());
        assertEquals(1, auditHook.callCount.get());
        assertEquals("blocking_node", auditHook.calledAtNode.get());
        assertNotNull(auditHook.capturedResult.get());
        // The VALUE in the hook result is the raw value before channel merging
        assertEquals("test_value", auditHook.capturedResult.get().get("VALUE"));
    }

    @ParameterizedTest
    @EnumSource( StreamNodeEnum.class )
    public void testStreamingNodeAfterHookCalledAfterCompletion( StreamNodeEnum streamNodeEnum ) throws Exception {
        // Given
        Map<String, Channel<?>> schema = Map.of("VALUE", Channels.appender(ArrayList::new));
        var auditHook = new AuditHook<State>();

        var workflow = new StateGraph<>(schema, State::new)
                .addAfterCallNodeHook(auditHook)
                .addNode("streaming_node", streamNodeEnum.actionFactory.apply("streaming_node", List.of("Hello", " ", "World")))
                .addEdge(START, "streaming_node")
                .addEdge("streaming_node", END)
                .compile();

        // When - consume the streaming generator
        var stream = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder().build());

        // Consume all streaming output
        List<String> streamingChunks = new ArrayList<>();
        for (var output : stream) {
            if (output instanceof StreamingOutput<?> streamingOutput) {
                log.info("Streaming chunk output {}", streamingOutput.chunk());
                streamingChunks.add(streamingOutput.chunk());
            }
        }

        final var result = GraphResult.from(stream);
        assertFalse( result.isEmpty() );
        assertTrue( result.isStateData() );
        assertEquals( "called", result.asStateData().get("AuditHook"));

        // Then - verify streaming completed and AfterHook was called with final result
        assertIterableEquals(List.of("Hello", " ", "World"), streamingChunks);
        assertEquals(1, auditHook.callCount.get());
        assertEquals("streaming_node", auditHook.calledAtNode.get());
        assertNotNull(auditHook.capturedResult.get());

        // CRITICAL: Verify AfterHook received the final result, NOT the AsyncGenerator
        // In the old buggy code, AfterHook would receive Map.of("content", AsyncGenerator)
        // In the fixed code, AfterHook receives Map.of("VALUE", "streaming_completed")
        Object capturedValue = auditHook.capturedResult.get().get("VALUE");
        assertNotNull(capturedValue, "AfterHook should receive the final result with VALUE key");

        // Make sure we didn't receive the generator itself
        Object contentValue = auditHook.capturedResult.get().get("content");
        assertFalse(contentValue instanceof AsyncGenerator, "BUG: AfterHook received AsyncGenerator instead of final result! This means the fix is not working or not applied.");
        assertEquals("streaming_completed", capturedValue);
    }

    @ParameterizedTest
    @EnumSource( StreamNodeEnum.class )
    public void testMixedBlockingAndStreamingNodes( StreamNodeEnum streamNodeEnum ) throws Exception {
        // Given
        Map<String, Channel<?>> schema = Map.of("VALUE", Channels.appender(ArrayList::new));
        var auditHook = new AuditHook<State>();

        var workflow = new StateGraph<>(schema, State::new)
                .addAfterCallNodeHook(auditHook)
                .addNode("blocking_node", createBlockingNode("blocking_result"))
                .addNode("streaming_node", streamNodeEnum.actionFactory.apply("streaming_node", List.of("A", "B", "C")))
                .addEdge(START, "blocking_node")
                .addEdge("blocking_node", "streaming_node")
                .addEdge("streaming_node", END)
                .compile();

        // When
        var stream = workflow.stream(Map.of(), RunnableConfig.builder().build());

        List<String> streamingChunks = new ArrayList<>();
        String lastNode = null;
        for (var output : stream) {
            if (output instanceof StreamingOutput<?> streamingOutput) {
                log.info("Streaming chunk output {}", streamingOutput.chunk());
                streamingChunks.add(streamingOutput.chunk());
            }
            log.info("Node ID {}", output.node());
            lastNode = output.node();
        }

        final var result = GraphResult.from(stream);
        assertFalse( result.isEmpty() );
        assertTrue( result.isStateData() );
        assertEquals( "called", result.asStateData().get("AuditHook"));

        // Then
        assertEquals(END, lastNode);
        assertEquals(List.of("A", "B", "C"), streamingChunks);

        // Both hooks should have been called
        assertEquals(2, auditHook.callCount.get());

        // CRITICAL: The last call should be for streaming_node with the final result
        // (not the AsyncGenerator)
        assertEquals("streaming_node", auditHook.calledAtNode.get());
        Object capturedValue = auditHook.capturedResult.get().get("VALUE");
        assertNotNull(capturedValue, "Streaming node AfterHook should receive final result with VALUE key, not AsyncGenerator");
        assertEquals("streaming_completed", capturedValue);
    }


}
