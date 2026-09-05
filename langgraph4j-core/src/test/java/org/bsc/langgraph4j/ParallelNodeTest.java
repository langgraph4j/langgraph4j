package org.bsc.langgraph4j;


import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.TryConsumer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

public class ParallelNodeTest {

    static class State extends AgentState {
        public static final Map<String, Channel<?>> SCHEMA = Map.of(
                "task", Channels.appender(ArrayList::new)
        );

        public static State of(Map<String, Object> data) {
            return new State(data);
        }

        public State( Map<String, Object> initData ) {
            super( initData );
        }

        public List<String> completedTasks() {
            return this.<List<String>>value("task")
                    .orElseGet( List::of);
        }
    }
    private <T> T measureTime(Supplier<T> runnable, Consumer<Duration> consumer) {
        final var start = Instant.now();

        var result = runnable.get();

        final var end = Instant.now();

        consumer.accept(Duration.between(start, end));

        return result;

    }
    private static AsyncNodeActionWithConfig<State> createSyncAction(int taskId ) {

        return ( state, config ) -> {
                    long waitMills = (long) (Math.random() * 1000);
                    try {
                        // Simulate work
                        Thread.sleep(waitMills);
                    } catch (InterruptedException e) {
                        throw new CompletionException(e);
                    }

                    final var value =  "TASK [%d] COMPLETED in [%d] MILLS BY [%s]".formatted(taskId, waitMills, Thread.currentThread().getName() );

                    System.out.println(value);

                    return completedFuture(Map.of(
                            "task",
                            value )); // return some result
                };
    }

    @Test
    public void parallelNodeTestWithSyncAction() throws Exception {

        var numberOfAsyncTask = 10;

        var actions = IntStream.range(0, numberOfAsyncTask)
                .mapToObj(ParallelNodeTest::createSyncAction)
                .toList();

        var parallelNode = new ParallelNode<>("parallelNodeTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        Map<String, Object> initialData = Map.of("item1", "test1", "task-2", "test2");

        var agentState = State.of(initialData);

        var runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor( "parallelNodeTest", ForkJoinPool.commonPool() )
                .build();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Sync Action managed by graph Took: " + duration.toMillis() + " ms") );


        final var newState = State.of(result);

        newState.data().entrySet().forEach(System.out::println);

        assertEquals(numberOfAsyncTask, newState.completedTasks().size());


    }

    private final RandomGenerator generator = RandomGenerator.getDefault();

    private Duration randomDuration() {
        return Duration.ofSeconds( generator.nextInt(1, 11) );
    }

    private AsyncNodeActionWithConfig<State> createAsyncAction(int taskId, Executor executor, Duration delay) {

        return ( state, config ) ->
                CompletableFuture.supplyAsync(() -> {
                    System.out.printf("TASK [%d] STARTING BY [%s]%n", taskId, Thread.currentThread().getName() );
                    try {
                        // Simulate work
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException e) {
                        throw new CompletionException(e);
                    }

                    final var value =  "TASK [%d] COMPLETED in [%d]s BY [%s]".formatted(taskId, delay.toSeconds(), Thread.currentThread().getName() );

                    return Map.of(
                            "task",
                            value ); // return some result
                }, executor);
    }


    @Test
    public void parallelNodeWithAsyncActionTest() throws Exception {

        var numberOfAsyncTask = 10;

        var actions = IntStream.range(0, numberOfAsyncTask)
                .mapToObj(i -> createAsyncAction(i, ForkJoinPool.commonPool(), randomDuration()))
                .toList();

        var parallelNode = new ParallelNode<>("parallelNodeTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        Map<String, Object> initialData = Map.of("item1", "test1");

        var agentState = State.of(initialData);

        var runnableConfig = RunnableConfig.builder().build();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Async Action Took: " + duration.toMillis() + " ms") );

        var newState = State.of(result);

        newState.data().entrySet().forEach(System.out::println);

        assertEquals(numberOfAsyncTask, newState.completedTasks().size());

    }

    private AsyncNodeActionWithConfig<State> createAsyncActionWithException(int taskId, Executor executor, Duration delay) {

        return ( state, config ) ->
                CompletableFuture.supplyAsync(() -> {

                    System.out.printf("TASK [%d] STARTING BY [%s]%n", taskId, Thread.currentThread().getName() );
                    try {
                        // Simulate work
                        Thread.sleep(delay.toMillis());

                    } catch (InterruptedException e) {
                        throw new CompletionException(e);
                    }

                    throw new RuntimeException("TASK [%d] raise exception in [%d]s BY [%s]"
                            .formatted( taskId, delay.toSeconds(), Thread.currentThread().getName() ));

                }, executor);
    }

    @Test
    public void parallelNodeIssue294Test() throws Exception {

        var executorService = ForkJoinPool.commonPool();

        var actions =  List.of(
                createAsyncAction(1, executorService, Duration.ofSeconds(5)),
                createAsyncAction(2, executorService, Duration.ofSeconds(2)),
                createAsyncActionWithException(3, executorService, Duration.ofSeconds(3)),
                createAsyncAction(4, executorService, Duration.ofSeconds(1)),
                createAsyncAction(5, executorService, Duration.ofSeconds(10)));

        var parallelNode = new ParallelNode<>("parallelNodeTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        var agentState = State.of(Map.of("item1", "test1", "task-2", "test2"));

        var runnableConfig = RunnableConfig.builder().build();

        var exception = assertThrows( CompletionException.class, () -> parallelNodeAction.apply(agentState, runnableConfig).join());

        assertInstanceOf( RuntimeException.class, exception.getCause() );

    }

    // streaming action carrying the final business state only in the generator result value (Data.done(...)), like StreamingChatGenerator
    private static AsyncNodeActionWithConfig<State> compileParallelNodeAction(ParallelNode<State> parallelNode) {
        return assertDoesNotThrow(() -> parallelNode.actionFactory().apply(CompileConfig.builder().build()));
    }

    private static AsyncNodeActionWithConfig<State> createSyncStreamingAction(String key, String value) {

        return ( state, config ) -> {

            final var generator = AsyncGeneratorFlow.create(TryConsumer.Try($1 -> {
                    for (String chunk : List.of("chunk-1", "chunk-2")) {
                        $1.dispatchSync(AsyncGenerator.Data.of(new StreamingOutput<>(chunk, "streaming", state, null)));
                    }
                    if (value == null) {
                            $1.dispatchSync(AsyncGenerator.Data.done());
                    } else {
                            $1.dispatchSync(AsyncGenerator.Data.done(Map.of(key, value)));
                }
            }));
            return completedFuture(Map.of("content", generator));
        };
    }

    private static AsyncNodeActionWithConfig<State> createAsyncStreamingAction(String key, String value) {

        return ( state, config ) -> {

            final var generator = AsyncGeneratorFlow.create(TryConsumer.Try($1 -> {
                    for (String chunk : List.of("chunk-1", "chunk-2")) {
                        $1.dispatchSync(AsyncGenerator.Data.of(new StreamingOutput<>(chunk, "streaming", state, null)));
                    }
                    $1.dispatchSync(AsyncGenerator.Data.done(Map.of(key, value)));
                }));
            return completedFuture(Map.of("content", generator));
        };
    }

    private static AsyncNodeActionWithConfig<State> createStreamingActionWithResult(Object resultValue) {
        return (state, config) -> {
            final var generator = AsyncGeneratorFlow.create(TryConsumer.Try($1 ->
                $1.dispatchSync(AsyncGenerator.Data.done(resultValue))
            ));
            return completedFuture(Map.of("content", generator));
        };
    }

    @Test
    public void parallelNodeWithSyncStreamingActionTest() {

        var actions = List.of(
                createSyncStreamingAction("task", "LEFT"),
                createSyncStreamingAction("task", "RIGHT"));

        var parallelNode = new ParallelNode<>("parallelStreamingTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        var agentState = State.of(Map.of("item1", "test1"));

        var result = parallelNodeAction.apply(agentState, RunnableConfig.builder().build()).join();

        var newState = State.of(result);

        assertEquals( 2, newState.completedTasks().size() );
        assertTrue( newState.completedTasks().contains("LEFT") );
        assertTrue( newState.completedTasks().contains("RIGHT") );
        assertEquals( "test1", newState.value("item1").orElseThrow() );
    }

    @Test
    public void parallelNodeWithAsyncStreamingActionTest() {

        var actions = List.of(
                createAsyncStreamingAction("task", "LEFT"),
                createAsyncStreamingAction("task", "RIGHT"));

        var parallelNode = new ParallelNode<>("parallelStreamingTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        var agentState = State.of(Map.of("item1", "test1"));

        var result = parallelNodeAction.apply(agentState, RunnableConfig.builder().build()).join();

        var newState = State.of(result);

        assertEquals( 2, newState.completedTasks().size() );
        assertTrue( newState.completedTasks().contains("LEFT") );
        assertTrue( newState.completedTasks().contains("RIGHT") );
        assertEquals( "test1", newState.value("item1").orElseThrow() );
    }

    @Test
    public void parallelNodeWithStreamingActionWithoutResultValueTest() {

        var actions = List.of(
                createSyncStreamingAction("task", "LEFT"),
                createSyncStreamingAction("task", null));

        var parallelNode = new ParallelNode<>("parallelStreamingTest", actions, State.SCHEMA);

        var parallelNodeAction = compileParallelNodeAction(parallelNode);

        var agentState = State.of(Map.of("item1", "test1"));

        var result = parallelNodeAction.apply(agentState, RunnableConfig.builder().build())
                                        .join();

        var newState = State.of(result);

        assertEquals( 1, newState.completedTasks().size() );
        assertTrue( newState.completedTasks().contains("LEFT") );
        assertEquals( "test1", newState.value("item1").orElseThrow() );
    }

    @Test
    public void parallelBranchWithStreamingNodesEndToEndTest() {

        var workflow = assertDoesNotThrow(() -> new StateGraph<State>(State::new)
                .addNode("left", createSyncStreamingAction("left", "L"))
                .addNode("right", createSyncStreamingAction("right", "R"))
                .addEdge(START, "left")
                .addEdge(START, "right")
                .addEdge("left", END)
                .addEdge("right", END)
                .compile());

        workflow.stream( GraphInput.noArgs(), RunnableConfig.empty() )
                .toCompletableFuture()
                .thenApply( GraphResult::from )
                .whenComplete((result, throwable) -> {
                    assertNotNull(throwable);
                    assertTrue(result.isStateDataOrCheckpointSaverTag());
                    final var stateData = result.asStateDataOrLastCheckpointStateData();
                    assertEquals( "L", stateData.get("left") );
                    assertEquals( "R", stateData.get("right") );
                });
    }

    @Test
    public void parallelNodeWithEmptyStreamingResultTest() {
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult(Map.of())), State.SCHEMA);

        var result = compileParallelNodeAction(parallelNode)
                .apply(State.of(Map.of("item1", "test1")), RunnableConfig.builder().build())
                .join();

        assertEquals("test1", result.get("item1"));
        assertFalse(result.containsKey("content"));
    }

    @Test
    public void parallelNodeWithCancelledStreamingResultTest() {
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult(AsyncGenerator.IsCancellable.CANCELLED)), State.SCHEMA);

        var result = compileParallelNodeAction(parallelNode)
                .apply(State.of(Map.of("item1", "test1")), RunnableConfig.builder().build())
                .join();

        assertEquals("test1", result.get("item1"));
        assertFalse(result.containsKey("content"));
    }

    @Test
    public void parallelNodeWithCheckpointStreamingResultTest() {
        var checkpoint = Checkpoint.builder()
                .state(Map.of("task", "FROM_CHECKPOINT"))
                .nodeId("streaming")
                .nextNodeId(END)
                .build();
        var checkpointTag = new BaseCheckpointSaver.Tag("thread", List.of(checkpoint));
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult(checkpointTag)), State.SCHEMA);

        var result = compileParallelNodeAction(parallelNode)
                .apply(State.of(Map.of("item1", "test1")), RunnableConfig.empty())
                .join();

        var newState = State.of(result);
        assertEquals(List.of("FROM_CHECKPOINT"), newState.completedTasks());
        assertEquals("test1", newState.value("item1").orElseThrow());
    }

    @Test
    public void parallelNodeRejectsInvalidStreamingResultValueTest() {
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult("not-state-data")), State.SCHEMA);

        var exception = assertThrows(CompletionException.class, () ->
                compileParallelNodeAction(parallelNode)
                        .apply(State.of(Map.of()), RunnableConfig.builder().build())
                        .join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Invalid result type: class java.lang.String", exception.getCause().getMessage());
    }

    @Test
    public void parallelNodeRejectsNodeOutputStreamingResultTest() {
        var nodeOutput = NodeOutput.of("streaming", State.of(Map.of()));
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult(nodeOutput)), State.SCHEMA);

        var exception = assertThrows(CompletionException.class, () ->
                compileParallelNodeAction(parallelNode)
                        .apply(State.of(Map.of()), RunnableConfig.builder().build())
                        .join());

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("Unsupported parallel branch streaming result type: NODE_OUTPUT",
                exception.getCause().getMessage());
    }

    @Test
    public void parallelNodeRejectsInterruptionStreamingResultTest() {
        var interruption = InterruptionMetadata.builder("streaming", State.of(Map.of())).build();
        var parallelNode = new ParallelNode<State>("parallelStreamingTest",
                List.of(createStreamingActionWithResult(interruption)), State.SCHEMA);

        var exception = assertThrows(CompletionException.class, () ->
                compileParallelNodeAction(parallelNode)
                        .apply(State.of(Map.of()), RunnableConfig.builder().build())
                        .join());

        assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
        assertEquals("Interruption metadata cannot be returned from a parallel branch streaming generator",
                exception.getCause().getMessage());
    }

    @Test
    public void parallelNodeWithAsyncStreamingActionAndExecutorTest() {
        var actionThread = new CompletableFuture<String>();
        AsyncNodeActionWithConfig<State> action = (state, config) -> {
            actionThread.complete(Thread.currentThread().getName());
            return createAsyncStreamingAction("task", "EXECUTOR").apply(state, config);
        };
        var parallelNode = new ParallelNode<State>("parallelStreamingTest", List.of(action), State.SCHEMA);
        var executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "parallel-streaming-test"));

        try {
            var config = RunnableConfig.builder()
                    .addParallelNodeExecutor("parallelStreamingTest", executor)
                    .build();
            var result = compileParallelNodeAction(parallelNode)
                    .apply(State.of(Map.of()), config)
                    .join();

            assertEquals("parallel-streaming-test", actionThread.join());
            assertEquals(List.of("EXECUTOR"), State.of(result).completedTasks());
        } finally {
            executor.shutdownNow();
        }
    }

}
