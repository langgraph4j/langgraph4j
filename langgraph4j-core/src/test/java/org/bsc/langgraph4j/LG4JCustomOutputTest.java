package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LG4JCustomOutputTest implements LG4JTestUtil {

    static class CustomOutput extends NodeOutput<State> {

        public static CustomOutput of(String node, State state) {
            return new CustomOutput(node, state);
        }

        public CustomOutput(String node, State state) {
            super(node, state);
        }
    }

    @Test
    void testOneNodeWithDispatchAsyncCustomOutput() throws Exception {

        final AsyncNodeActionWithConfig<State> nodeWithCustomOutput = (state, config) -> {

            final var $1 = config.<State,CustomOutput>customDispatcher();

            $1.dispatchAsync(CustomOutput.of("A.START", state));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return failedFuture(e);
            }

            return completedFuture(Map.of("messages", "A"));
        };


        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", nodeWithCustomOutput)
                .addEdge(START, "A")
                .addEdge("A", END)
                .compile(CompileConfig.builder().build());


        final var expectedOutputsList = List.of(START, "A.START", "A", END);

        { // Async iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                    .forEachAsync(output -> {
                        System.out.println(output);
                        assertEquals(expectedOutputStack.pop(), output.node());
                    })
                    .join();

        }

        { // Sync iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            for (var output : graph.stream(GraphInput.noArgs(), RunnableConfig.empty())) {
                System.out.println(output);
                assertEquals(expectedOutputStack.pop(), output.node());
            }
        }
    }

    @Test
    void testOneNodeWithDispatchSyncCustomOutput() throws Exception {
        final AsyncNodeActionWithConfig<State> nodeWithCustomOutput = (state, config) -> {

            final var $1 = config.<State,CustomOutput>customDispatcher();

            try {
                $1.dispatchSync(CustomOutput.of("A.START", state));

                Thread.sleep(1000);
            } catch (Exception e) {
                return failedFuture(e);
            }

            return completedFuture(Map.of("messages", "A"));

        };


        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", nodeWithCustomOutput)
                .addEdge(START, "A")
                .addEdge("A", END)
                .compile(CompileConfig.builder().build());

        final var expectedOutputsList = List.of(START, "A.START", "A", END);

        { // Async iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);
            graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                    .forEachAsync(output -> {
                        System.out.println(output);
                        assertEquals(expectedOutputStack.pop(), output.node());
                    })
                    .join();

        }

        { // Sync iteration
            final var expectedOutputStack = new ConcurrentLinkedDeque<>(expectedOutputsList);

            for (var output : graph.stream(GraphInput.noArgs(), RunnableConfig.empty())) {
                System.out.println(output);
                assertEquals(expectedOutputStack.pop(), output.node());
            }
        }

    }

}
