package org.bsc.langgraph4j;


import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;

public class ParallelNodeTest {

    static class State extends AgentState {
        public static final Map<String, Channel<?>> SCHEMA = Map.of(
                "task", Channels.appender(ArrayList::new)
        );
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

                    final var value = format( "TASK [%d] COMPLETED in [%d] MILLS BY [%s]", taskId, waitMills, Thread.currentThread().getName() );

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

        var parallelNodeAction = parallelNode.actionFactory().apply(CompileConfig.builder().build());

        Map<String, Object> initialData = Map.of("item1", "test1", "task-2", "test2");

        var agentState = new State(new LinkedHashMap<>(initialData));

        var runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor( "parallelNodeTest", ForkJoinPool.commonPool() )
                .build();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Sync Action managed by graph Took: " + duration.toMillis() + " ms") );


        var newState = new State(result);

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

                    final var value = format( "TASK [%d] COMPLETED in [%d]s BY [%s]", taskId, delay.toSeconds(), Thread.currentThread().getName() );

                    System.out.println(value);

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

        var parallelNodeAction = parallelNode.actionFactory().apply(CompileConfig.builder().build());

        Map<String, Object> initialData = Map.of("item1", "test1");

        var agentState = new State(new LinkedHashMap<>(initialData));

        var runnableConfig = RunnableConfig.empty();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Async Action Took: " + duration.toMillis() + " ms") );

        var newState = new State(result);

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

        var parallelNodeAction = parallelNode.actionFactory().apply(CompileConfig.builder().build());

        Map<String, Object> initialData = Map.of("item1", "test1", "task-2", "test2");

        var agentState = new State(new LinkedHashMap<>(initialData));

        var runnableConfig = RunnableConfig.empty();

        var exception = assertThrows( CompletionException.class, () -> parallelNodeAction.apply(agentState, runnableConfig).join());

        assertInstanceOf( RuntimeException.class, exception.getCause() );

    }

}
