package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LG4JCustomOutputTest implements LG4JTestUtil {

    static final class ElapsedOutput extends NodeOutput<State> {
        private final Duration elapsed;

        public ElapsedOutput(String node, State state, Instant start) {
            super(node, state);
            this.elapsed = Duration.between(start, Instant.now());
        }

        public Duration elapsed() {
            return elapsed;
        }
    }

    static class CustomOutput extends NodeOutput<State> {

        public static CustomOutput of(String node, State state) {
            return new CustomOutput(node, state);
        }

        public CustomOutput(String node, State state) {
            super(node, state);
        }
    }

    static class ElapsedNodeHook implements NodeHook.WrapCall<State> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

            final var dispatcher = config.<State, ElapsedOutput>customDispatcher();

            final var start = Instant.now();

            return action.apply(state, config)
                    .whenComplete((result, exception) -> {

                        if (exception == null) {
                            dispatcher
                                    .dispatchAsync(new ElapsedOutput(config.nodeId(), state, start));
                        }
                    });
        }

    }

    @Test
    void testOneNodeWithDispatchAsyncCustomOutput() throws Exception {

        final AsyncNodeActionWithConfig<State> nodeWithCustomOutput = (state, config) -> {

            config.<State, CustomOutput>customDispatcher()
                    .dispatchAsync(CustomOutput.of("A.START", state));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return failedFuture(e);
            }

            return completedFuture(Map.of("messages", "A"));
        };


        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addWrapCallNodeHook(new ElapsedNodeHook())
                .addNode("A", nodeWithCustomOutput)
                .addEdge(START, "A")
                .addEdge("A", END)
                .compile(CompileConfig.builder().build());


        final var expectedOutputsList = List.of(START, "A.START", "A", END);

        { // Async iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                    .forEachAsync(output -> {

                        if (output instanceof ElapsedOutput elapsedOutput) {
                            System.out.println(("Elapsed time for node '%s': %d ms".formatted(elapsedOutput.node(), elapsedOutput.elapsed().toMillis())));
                        } else {
                            System.out.println(output);
                            assertEquals(expectedOutputStack.pop(), output.node());
                        }
                    })
                    .join();

        }

        { // Sync iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            for (var output : graph.stream(GraphInput.noArgs(), RunnableConfig.empty())) {
                if (output instanceof ElapsedOutput elapsedOutput) {
                    System.out.println(("Elapsed time for node '%s': %d ms".formatted(elapsedOutput.node(), elapsedOutput.elapsed().toMillis())));
                } else {
                    System.out.println(output);
                    assertEquals(expectedOutputStack.pop(), output.node());
                }
            }
        }
    }

    @Test
    void testOneNodeWithDispatchSyncCustomOutput() throws Exception {
        final AsyncNodeActionWithConfig<State> nodeWithCustomOutput = (state, config) -> {

            try {
                config.<State, CustomOutput>customDispatcher()
                        .dispatchSync(CustomOutput.of("A.START", state));

                Thread.sleep(1000);
            } catch (Exception e) {
                return failedFuture(e);
            }

            return completedFuture(Map.of("messages", "A"));

        };


        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addWrapCallNodeHook(new ElapsedNodeHook())
                .addNode("A", nodeWithCustomOutput)
                .addEdge(START, "A")
                .addEdge("A", END)
                .compile(CompileConfig.builder().build());

        final var expectedOutputsList = List.of(START, "A.START", "A", END);

        { // Async iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);
            graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                    .forEachAsync(output -> {
                        if (output instanceof ElapsedOutput elapsedOutput) {
                            System.out.println(("Elapsed time for node '%s': %d ms".formatted(elapsedOutput.node(), elapsedOutput.elapsed().toMillis())));
                        } else {

                            System.out.println(output);
                            assertEquals(expectedOutputStack.pop(), output.node());
                        }
                    })
                    .join();

        }

        { // Sync iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            for (var output : graph.stream(GraphInput.noArgs(), RunnableConfig.empty())) {
                if (output instanceof ElapsedOutput elapsedOutput) {
                    System.out.println(("Elapsed time for node '%s': %d ms".formatted(elapsedOutput.node(), elapsedOutput.elapsed().toMillis())));
                } else {

                    System.out.println(output);
                    assertEquals(expectedOutputStack.pop(), output.node());
                }
            }
        }

    }

}
