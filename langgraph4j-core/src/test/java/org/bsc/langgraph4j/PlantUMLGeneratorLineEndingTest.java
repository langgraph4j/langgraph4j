package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rendered diagram is a text document: its line terminators must not depend on the platform
 * the graph is rendered on. Using {@code %n} resolves to CRLF on Windows and to LF on
 * Linux/macOS, so the very same graph produces different documents depending on the machine
 * that rendered it - which breaks golden-file comparisons and makes the output unstable.
 */
public class PlantUMLGeneratorLineEndingTest {

    private static StateGraph<AgentState> nestedGraph() throws Exception {
        final AsyncNodeAction<AgentState> action = AsyncNodeAction.node_async(ignored -> Map.of());

        final var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", action)
                .addNode("bar2", action)
                .addEdge(START, "bar1")
                .addEdge("bar1", "bar2")
                .addEdge("bar2", END)
                .compile();

        return new StateGraph<>(AgentState::new)
                .addNode("main1", action)
                .addNode("subgraph1", subGraph)
                .addEdge(START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", END);
    }

    @Test
    public void plantUmlShouldOnlyUseLfAsLineSeparator() throws Exception {

        final var content = nestedGraph()
                .getGraph(GraphRepresentation.Type.PLANTUML, "line-ending", false)
                .content();

        assertFalse(content.contains("\r"),
                () -> "PlantUML output must not contain CR, got:\n" + content);

        // lines emitted for a sub graph are the ones that used to embed the platform separator
        assertTrue(content.contains("package subgraph1 {\n"),
                () -> "sub graph declaration is not LF terminated, got:\n" + content);

        assertTrue(content.endsWith("@enduml\n"),
                () -> "document is not LF terminated, got:\n" + content);
    }
}
