package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link GraphRunnerException} surfaces the failing node id so callers can
 * react to execution failures without inspecting the underlying {@link RunnableConfig}
 * metadata directly, and without the {@code orElseThrow} of {@link RunnableConfig#nodeId()}.
 *
 * @see <a href="https://github.com/langgraph4j/langgraph4j/issues/90">Issue #90</a>
 */
public class GraphRunnerExceptionTest {

    @Test
    public void nodeIdIsResolvedFromConfigMetadata() {
        var config = RunnableConfig.builder()
                .addMetadata(RunnableConfig.NODE_ID, "agent")
                .build();

        var exception = new GraphRunnerException(config, "boom");

        assertEquals(Optional.of("agent"), exception.nodeId());
    }

    @Test
    public void nodeIdIsEmptyWhenContextIsAbsent() {
        var exception = new GraphRunnerException(RunnableConfig.builder().build(), "boom");

        assertTrue(exception.nodeId().isEmpty());
    }

    @Test
    public void failingNodeIsReportedOnGraphExecution() throws Exception {
        var workflow = new StateGraph<>(AgentState::new)
                .addNode("A", AsyncNodeAction.node_async(state -> Map.of()))
                .addNode("B", AsyncNodeAction.node_async(state -> {
                    throw new RuntimeException("boom");
                }))
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge("B", END)
                .compile();

        var thrown = assertThrows(Exception.class, () -> workflow.invoke( GraphInput.noArgs(), RunnableConfig.empty() ));

        var graphRunnerException = GraphRunnerException.of(thrown)
                .orElseThrow(() -> new AssertionError("expected a GraphRunnerException in the cause chain", thrown));

        assertEquals(Optional.of("B"), graphRunnerException.nodeId());

        var rootCause = graphRunnerException.getCause();
        assertNotNull(rootCause);
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        assertEquals(RuntimeException.class, rootCause.getClass());
        assertEquals("boom", rootCause.getMessage());
    }
}
