package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

class RecursionLimitTest {

    private CompiledGraph<AgentState> loopingGraph(int recursionLimit) throws Exception {
        return new StateGraph<>(AgentState::new)
                .addNode("loop", AsyncNodeAction.node_async(state -> Map.of()))
                .addEdge(START, "loop")
                .addEdge("loop", "loop")
                .compile(CompileConfig.builder().recursionLimit(recursionLimit).build());
    }

    private void assertRecursionLimit(Throwable exception, int recursionLimit) {
        final var cause = ExceptionUtils.getRootCause(exception);
        assertEquals("Maximum number of iterations (%d) reached!".formatted(recursionLimit), cause.getMessage());
    }

    @Test
    void compileConfigRecursionLimitIsUsedWithoutAnOverride() throws Exception {
        loopingGraph(3)
                .stream( GraphInput.noArgs(), RunnableConfig.empty())
                .toCompletableFuture()
                .whenComplete((ex, exception) -> {
                    assertNotNull(exception);
                    assertRecursionLimit(exception, 3);
                });

    }

    @Test
    void runnableConfigRecursionLimitOverridesTheGraphDefault() throws Exception {
        loopingGraph(2)
                .stream(GraphInput.noArgs(), RunnableConfig.builder().recursionLimit(4).build())
                .toCompletableFuture()
                .whenComplete((ex, exception) -> {
                    assertNotNull(exception);
                    assertRecursionLimit(exception, 4);
                });
    }

    @Test
    @SuppressWarnings("removal")
    void runnableConfigRecursionLimitOverridesLegacyMaxIterations() throws Exception {
        var graph = loopingGraph(3);


        graph.stream(GraphInput.noArgs(), RunnableConfig.empty())
                .toCompletableFuture()
                        .whenComplete((ex, legacyException) -> {
                            assertNotNull(legacyException);
                            assertRecursionLimit(legacyException, 3);
                        });

        graph.stream(GraphInput.noArgs(), RunnableConfig.builder().recursionLimit(4).build())
                        .toCompletableFuture()
                        .whenComplete((ex, overrideException) -> {
                            assertNotNull(overrideException);
                            assertRecursionLimit(overrideException, 4);
                        });
    }

    @Test
    void recursionLimitIsPropagatedToCompiledSubgraphs() throws Exception {
        var subgraph = new StateGraph<>(AgentState::new)
                .addNode("node", AsyncNodeActionWithConfig.node_async((state, config) -> {
                    assertEquals(Optional.of(7), config.recursionLimit());
                    return Map.of();
                }))
                .addEdge(START, "node")
                .addEdge("node", StateGraph.END)
                .compile();

        var graph = new StateGraph<>(AgentState::new)
                .addNode("subgraph", subgraph)
                .addEdge(START, "subgraph")
                .addEdge("subgraph", StateGraph.END)
                .compile();

        graph.stream(GraphInput.noArgs(), RunnableConfig.builder().recursionLimit(7).build())
            .toCompletableFuture()
                .whenComplete((result, exception) -> {
                    assertNull(exception);
                    assertNotNull(result);
                });
    }
}
