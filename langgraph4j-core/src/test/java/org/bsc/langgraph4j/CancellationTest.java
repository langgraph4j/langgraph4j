package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.bsc.langgraph4j.utils.TypeRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

public class CancellationTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test");

    private AsyncNodeActionWithConfig<MessagesState<String>> _makeWaitingNode(String id, Duration delay) {

        return ( state, config ) -> {
                log.info( "STARTING NODE: {} BY THREAD [{}]", id, Thread.currentThread().getName());

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e) {
                    return failedFuture(e);
                }
                log.info( "ENDING NODE: {} BY THREAD [{}]", id, Thread.currentThread().getName());

                return completedFuture( Map.<String,Object>of("messages", id) )
                        .thenApply( result -> {
                            config.metadata( "node_completed", new TypeRef<AtomicInteger>() {} )
                                    .ifPresent(AtomicInteger::incrementAndGet);
                            return result;
                        });
            };
    }

    private CompletableFuture<Void> requestCancelGenerator( AsyncGenerator.Cancellable<?> generator,
                                                            boolean mayInterruptIfRunning,
                                                            Duration delay ) {

        return CompletableFuture.runAsync( () -> {
            try {
                Thread.sleep(delay.toMillis());

                log.info("request cancellation of generator mayInterruptIfRunning: {}", mayInterruptIfRunning);
                var result = generator.cancel(mayInterruptIfRunning);
                log.info("cancellation executed with result: {}", result);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void graphCancelTest() throws Exception {

        var workflow = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addNode("agent_1", _makeWaitingNode("agent_1", Duration.ofSeconds(1)))
                .addNode("agent_2", _makeWaitingNode("agent_2", Duration.ofSeconds(1)))
                .addNode("agent_3", _makeWaitingNode("agent_3", Duration.ofSeconds(1)))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END)
                .compile();

        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING FOR EACH ASYNC
        //////////////////////////////////////////////////////////////
        {
            var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.empty());

            requestCancelGenerator(generator, true, Duration.ofMillis(50));

            var futureResult = generator.forEachAsync(output -> {
                log.info("iteration is on: {}", output);
            }).exceptionally(ex -> {
                assertTrue(generator.isCancelled());
                assertInstanceOf(InterruptedException.class, ExceptionUtils.getRootCause(ex));
                return AsyncGenerator.IsCancellable.CANCELLED;
            });

            final var graphResult = GraphResult.from(futureResult.get(5, TimeUnit.SECONDS));

            assertTrue(generator.isCancelled());
            assertTrue(graphResult.isCancelled());
        }
        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING ITERATOR
        //////////////////////////////////////////////////////////////
        {
            var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.empty());

            requestCancelGenerator(generator, true, Duration.ofMillis(50));

            NodeOutput<?> currentOutput = null;

            for (var output : generator) {
                log.info("iteration is on: {}", output);
                currentOutput = output;
            }

            var result = GraphResult.from(generator);

            assertNotNull(currentOutput);
            assertNotEquals(END, currentOutput.node());
            assertTrue(generator.isCancelled());
            assertTrue(result.isCancelled());
        }
    }

    @Test
    public void compiledSubGraphCancelTest() throws Exception {

        var subGraph = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                    .addNode("NODE3.1", _makeWaitingNode("NODE3.1", Duration.ofSeconds(1)))
                    .addNode("NODE3.2", _makeWaitingNode("NODE3.2", Duration.ofSeconds(1)))
                    .addNode("NODE3.3", _makeWaitingNode("NODE3.3", Duration.ofSeconds(1)))
                    .addNode("NODE3.4", _makeWaitingNode("NODE3.4", Duration.ofSeconds(1)))
                    .addEdge(START, "NODE3.1")
                    .addEdge("NODE3.1", "NODE3.2")
                    .addEdge("NODE3.2", "NODE3.3")
                    .addEdge("NODE3.3", "NODE3.4")
                    .addEdge("NODE3.4", END)
                    .compile();

        var parentGraph =  new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addEdge(START, "NODE1" )
                .addNode("NODE1", _makeWaitingNode("NODE1", Duration.ofSeconds(1)))
                .addNode("NODE2", _makeWaitingNode("NODE2", Duration.ofSeconds(1)))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", _makeWaitingNode("NODE4", Duration.ofSeconds(1)))
                .addNode("NODE5", _makeWaitingNode("NODE5", Duration.ofSeconds(1)))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile();

        //////////////////////////////////////////////////////////////////////////////////
        // NO CANCEL TEST
        //////////////////////////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.empty());

            var output = generator.stream()
                    .peek(out -> log.info("output: {}", out))
                    .reduce((a, b) -> b);

            assertFalse(generator.isCancelled());
            assertTrue(output.isPresent());
            assertTrue(output.get().isEND());
        }

        //////////////////////////////////////////////////////////////////////////////////
        // CANCEL TEST USING FOR EACH ASYNC
        //////////////////////////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.empty());

            requestCancelGenerator(generator, true, Duration.ofSeconds(3));

            var futureResult = generator.forEachAsync(out -> {
                log.info("iteration is on: {}", out);
            }).exceptionally(ex -> {
                assertTrue(generator.isCancelled());
                assertInstanceOf(InterruptedException.class, ExceptionUtils.getRootCause(ex));
                return AsyncGenerator.IsCancellable.CANCELLED;
            });

            final var graphResult = GraphResult.from(futureResult.get(5, TimeUnit.SECONDS));

            assertTrue(generator.isCancelled());
            assertTrue(graphResult.isCancelled());
        }

        //////////////////////////////////////////////////////////////
        // TEST USING ITERATOR
        //////////////////////////////////////////////////////////////
        {
            var generator = parentGraph.stream(GraphInput.noArgs(), RunnableConfig.empty());

            requestCancelGenerator(generator, true, Duration.ofSeconds(3));

            NodeOutput<?> currentOutput = null;

            for (var output : generator) {
                log.info("iteration is on: {}", output);
                currentOutput = output;
            }

            var result = GraphResult.from(generator);

            assertNotNull(currentOutput);
            assertNotEquals(END, currentOutput.node());
            assertTrue(generator.isCancelled());
            assertTrue(result.isCancelled());
        }
    }

    @Test
    public void graphWithParallelNodesCancelTest() throws Exception {


        var workflow = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addNode( "node_start", (node,config) -> {
                    log.info( "STARTING NODE: {} BY THREAD [{}]", "node_start", Thread.currentThread().getName());
                    return completedFuture(Map.of());
                })
                .addNode( "node_end", (node,config) -> {
                    log.info( "STARTING NODE: {} BY THREAD [{}]", "node_start", Thread.currentThread().getName());
                    return completedFuture(Map.of());
                })
                .addNode("agent_1", _makeWaitingNode("agent_1", Duration.ofMillis(500) ))
                .addNode("agent_2", _makeWaitingNode("agent_2", Duration.ofSeconds(1)))
                .addNode("agent_3", _makeWaitingNode("agent_3", Duration.ofMillis(500)))
                .addEdge( "node_start", "agent_1")
                .addEdge( "node_start", "agent_2")
                .addEdge( "node_start", "agent_3")
                .addEdge("agent_1", "node_end")
                .addEdge("agent_2", "node_end")
                .addEdge("agent_3", "node_end")
                .addEdge(START, "node_start")
                .addEdge("node_end", END)
                .compile();

        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING FOR EACH ASYNC
        //////////////////////////////////////////////////////////////

        {
            var parallelExecutor = Executors.newSingleThreadExecutor( task ->
                    new Thread( task, "parallel-node-executor"));

            var taskExecuted = new AtomicInteger(0);

            var generator = workflow.stream(GraphInput.noArgs(), RunnableConfig.builder()
                            .addParallelNodeExecutor( "node_start",parallelExecutor )
                            .addMetadata( "node_completed", taskExecuted )
                            .build());

            requestCancelGenerator(generator, true, Duration.ofSeconds(2))
                    .thenApply( v -> parallelExecutor.shutdownNow() )
                    ;

            var futureResult = generator.forEachAsync(output -> {
                log.info("iteration is on: {}", output);
            }).exceptionally(ex -> {
                assertTrue(generator.isCancelled());
                assertInstanceOf(GraphRunException.class, ex.getCause());
                assertInstanceOf(InterruptedException.class, ExceptionUtils.getRootCause(ex));
                assertEquals( 2, taskExecuted.get() );
                return AsyncGenerator.IsCancellable.CANCELLED;
            });

            final var graphResult = GraphResult.from(futureResult.get(5, TimeUnit.SECONDS));

            assertTrue(generator.isCancelled());
            assertTrue(graphResult.isCancelled());
        }

        //////////////////////////////////////////////////////////////
        // CANCEL TEST USING ITERATOR
        //////////////////////////////////////////////////////////////

        {
            var parallelExecutor = Executors.newSingleThreadExecutor( task ->
                    new Thread( task, "parallel-node-executor"));

            var taskExecuted = new AtomicInteger(0);

            var generator = workflow.stream(GraphInput.noArgs(),
                    RunnableConfig.builder()
                            .addParallelNodeExecutor( "node_start", parallelExecutor )
                            .addMetadata( "node_completed", taskExecuted )
                            .build());

            requestCancelGenerator(generator, true, Duration.ofSeconds(2))
                    .thenApply( v -> parallelExecutor.shutdownNow() )
            ;

           generator.toCompletableFutureAsync()
                    .exceptionally( ex ->
                        ExceptionUtils.findCauseByType(ex, InterruptedException.class)
                                .map( root -> AsyncGenerator.IsCancellable.CANCELLED )
                                .orElse(null)
                    )
                   .thenApply( GraphResult::from )
                   .thenAccept( result ->
                        assertTrue( result.isCancelled() ))
                   .join();

            assertTrue(generator.isCancelled());
            assertEquals( 2, taskExecuted.get() );

        }
    }

}
