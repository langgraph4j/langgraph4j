package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.bsc.langgraph4j.utils.TrySupplier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AsyncNodeTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AsyncNodeTest.class);

    AsyncNodeActionWithConfig<MessagesState<String>> node_async(NodeActionWithConfig<MessagesState<String>> action, Executor executor ) {

        return (state, config) ->
            CompletableFuture.supplyAsync(
                    TrySupplier.Try(() -> action.apply( state, config )),
                    executor );
    }

    private AsyncNodeActionWithConfig<MessagesState<String>> makeNode(String id, Executor executor) {
        return node_async( (state, config) -> {
            log.info("call node {}", id);
            return Map.of("messages", id);
        }, executor);
    }


    @Test
    public void testAsyncNode() throws Exception {

            var executor = ForkJoinPool.commonPool();

            AsyncCommandAction<MessagesState<String>> commandAction =
                    command_async( (state, config) ->
                        new Command("C2",
                                Map.of( "messages", "B",
                                        "next_node", "C2")) );

            var graph = new StateGraph<MessagesState<String>>( MessagesState.SCHEMA, MessagesState::new )
                    .addNode("A", makeNode("A", executor))
                    .addNode("B", commandAction, EdgeMappings.builder()
                            .toEND()
                            .to("C1")
                            .to("C2")
                            .build())
                    .addNode("C1", makeNode("C1", executor))
                    .addNode("C2", makeNode("C2", executor))
                    .addEdge(START, "A")
                    .addEdge("A", "B")
                    .addEdge( "C1", END )
                    .addEdge( "C2", END )
                    .compile();

            var steps = graph.stream(GraphInput.noArgs(), RunnableConfig.empty()).stream()
                    .peek(System.out::println)
                    .toList();

            assertEquals(5, steps.size());
            assertEquals( "B", steps.get(2).node());
            assertEquals( "C2", steps.get(2).state().value("next_node").orElse(null));

    }
}
