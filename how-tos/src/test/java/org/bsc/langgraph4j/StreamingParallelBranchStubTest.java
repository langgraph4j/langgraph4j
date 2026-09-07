package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic coverage for the streaming parallel branch how-to.
 */
public class StreamingParallelBranchStubTest {

    static class ResearchState extends AgentState {
        static final String RESULTS = "results";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                RESULTS, Channels.appender(ArrayList::new)
        );

        ResearchState(Map<String, Object> initData) {
            super(initData);
        }

        List<String> results() {
            return this.<List<String>>value(RESULTS).orElseGet(List::of);
        }
    }

    @Test
    void streamingBranchesEmitProgressAndMergeTheirFinalResults() throws Exception {
        var progress = new CopyOnWriteArrayList<String>();
        var workflow = workflow(progress::add);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            var config = RunnableConfig.builder()
                    .addParallelNodeExecutor(START, executor)
                    .build();

            var result = workflow.invoke(GraphInput.noArgs(), config).orElseThrow();

            assertEquals(List.of(
                    "web_search: LangGraph4j documentation",
                    "database: 3 matching records",
                    "api: service healthy"
            ), result.results());
            assertEquals(6, progress.size());
            assertTrue(progress.containsAll(List.of(
                    "web_search: searching docs",
                    "web_search: ranking matches",
                    "database: opening connection",
                    "database: reading rows",
                    "api: sending request",
                    "api: parsing response"
            )));
        } finally {
            executor.shutdownNow();
        }
    }

    private CompiledGraph<ResearchState> workflow(Consumer<String> onChunk)
            throws GraphStateException {
        return new StateGraph<>(ResearchState.SCHEMA, ResearchState::new)
                .addNode("web_search", streamingNode(
                        "web_search",
                        List.of("searching docs", "ranking matches"),
                        "web_search: LangGraph4j documentation",
                        onChunk))
                .addNode("database", streamingNode(
                        "database",
                        List.of("opening connection", "reading rows"),
                        "database: 3 matching records",
                        onChunk))
                .addNode("api", streamingNode(
                        "api",
                        List.of("sending request", "parsing response"),
                        "api: service healthy",
                        onChunk))
                .addNode("summarize", AsyncNodeAction.<ResearchState>node_async(state -> Map.of()))
                .addEdge(START, "web_search")
                .addEdge(START, "database")
                .addEdge(START, "api")
                .addEdge("web_search", "summarize")
                .addEdge("database", "summarize")
                .addEdge("api", "summarize")
                .addEdge("summarize", END)
                .compile();
    }

    private AsyncNodeAction<ResearchState> streamingNode(
            String nodeId,
            List<String> chunks,
            String finalResult,
            Consumer<String> onChunk) {
        return state -> {
            var generator = AsyncGeneratorFlow.<StreamingOutput<ResearchState>>create(dispatcher -> {
                for (var chunk : chunks) {
                    onChunk.accept("%s: %s".formatted(nodeId, chunk));
                    dispatcher.dispatchAsync(AsyncGenerator.Data.of(
                            new StreamingOutput<>(chunk, nodeId, state, null)));
                }
                dispatcher.dispatchAsync(AsyncGenerator.Data.done(
                        Map.of(ResearchState.RESULTS, finalResult)));
            });

            return java.util.concurrent.CompletableFuture.completedFuture(
                    Map.of("%s_stream".formatted(nodeId), generator)
            );
        };
    }
}
