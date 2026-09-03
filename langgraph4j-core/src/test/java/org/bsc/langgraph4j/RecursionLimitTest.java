package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecursionLimitTest {

    private CompiledGraph<AgentState> loopingGraph(int recursionLimit) throws Exception {
        return new StateGraph<>(AgentState::new)
                .addNode("loop", AsyncNodeAction.node_async(state -> Map.of()))
                .addEdge(START, "loop")
                .addEdge("loop", "loop")
                .compile(CompileConfig.builder().recursionLimit(recursionLimit).build());
    }

    private void assertRecursionLimit(Exception exception, int recursionLimit) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        assertEquals("Maximum number of iterations (%d) reached!".formatted(recursionLimit), cause.getMessage());
    }

    @Test
    void compileConfigRecursionLimitIsUsedWithoutAnOverride() throws Exception {
        var graph = loopingGraph(3);

        var exception = assertThrows(Exception.class, () -> graph.invoke(Map.of()));

        assertRecursionLimit(exception, 3);
    }

    @Test
    void runnableConfigRecursionLimitOverridesTheGraphDefault() throws Exception {
        var graph = loopingGraph(2);

        var exception = assertThrows(Exception.class,
                () -> graph.invoke(Map.of(), RunnableConfig.builder().recursionLimit(4).build()));

        assertRecursionLimit(exception, 4);
    }

    @Test
    @SuppressWarnings("removal")
    void runnableConfigRecursionLimitOverridesLegacyMaxIterations() throws Exception {
        var graph = loopingGraph(2);
        graph.setMaxIterations(3);

        var legacyException = assertThrows(Exception.class, () -> graph.invoke(Map.of()));
        assertRecursionLimit(legacyException, 3);

        var overrideException = assertThrows(Exception.class,
                () -> graph.invoke(Map.of(), RunnableConfig.builder().recursionLimit(4).build()));
        assertRecursionLimit(overrideException, 4);
    }
}
