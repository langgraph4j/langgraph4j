package org.bsc.langgraph4j;


import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Issue104Test {

    private AsyncNodeAction<MessagesState<String>> makeNode( String message ) {
        return node_async(state -> Map.of( "messages", message ) );
    }

    @Test
    public void testParallelBranchesWithCompiledSubGraph() throws GraphStateException {


        var subgraphA3 = new MessagesStateGraph<String>()
                .addNode("A3.1", makeNode("A3.1"))
                .addNode("A3.2", makeNode("A3.2"))
                .addEdge(START, "A3.1")
                .addEdge( "A3.1", "A3.2")
                .addEdge("A3.2", END)
                .compile();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", subgraphA3)
                .addNode("B", makeNode("B"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge(START, "A")
                .addEdge("B", END)
                .compile();

        var result = workflow.stream( GraphInput.args(Map.of("input", "test")), RunnableConfig.empty())
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("A", "A1", "A2", "A3.1", "A3.2", "B"), result.get().messages());

    }

    @Test
    public void testParallelBranchesWithMoreCompiledSubGraph() throws GraphStateException {

        var subgraphA3 = new MessagesStateGraph<String>()
                .addNode("A3.1", makeNode("A3.1"))
                .addNode("A3.2", makeNode("A3.2"))
                .addEdge(START, "A3.1")
                .addEdge( "A3.1", "A3.2")
                .addEdge("A3.2", END)
                .compile();
        var subgraphA1 = new MessagesStateGraph<String>()
                .addNode("A1.1", makeNode("A1.1"))
                .addNode("A1.2", makeNode("A1.2"))
                .addEdge(START, "A1.1")
                .addEdge( "A1.1", "A1.2")
                .addEdge("A1.2", END)
                .compile();
        var workflow = new MessagesStateGraph<String>()
                .addNode("A", makeNode("A"))
                .addNode("A1", subgraphA1)
                .addNode("A2", makeNode("A2"))
                .addNode("A3", subgraphA3)
                .addNode("B", makeNode("B"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge(START, "A")
                .addEdge("B", END)
                .compile();

        var result = workflow.stream( GraphInput.args(Map.of("input", "test")), RunnableConfig.empty())
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("A", "A1.1", "A1.2", "A2", "A3.1", "A3.2", "B"), result.get().messages());

    }

    @Test
    public void testParallelBranchesWithAllCompiledSubGraph() throws GraphStateException {

        var subgraphA3 = new MessagesStateGraph<String>()
                .addNode("A3.1", makeNode("A3.1"))
                .addNode("A3.2", makeNode("A3.2"))
                .addEdge(START, "A3.1")
                .addEdge( "A3.1", "A3.2")
                .addEdge("A3.2", END)
                .compile();
        var subgraphA2 = new MessagesStateGraph<String>()
                .addNode("A2.1", makeNode("A2.1"))
                .addNode("A2.2", makeNode("A2.2"))
                .addEdge(START, "A2.1")
                .addEdge( "A2.1", "A2.2")
                .addEdge("A2.2", END)
                .compile();
        var subgraphA1 = new MessagesStateGraph<String>()
                .addNode("A1.1", makeNode("A1.1"))
                .addNode("A1.2", makeNode("A1.2"))
                .addEdge(START, "A1.1")
                .addEdge( "A1.1", "A1.2")
                .addEdge("A1.2", END)
                .compile();
        var workflow = new MessagesStateGraph<String>()
                .addNode("A", makeNode("A"))
                .addNode("A1", subgraphA1 )
                .addNode("A2", subgraphA2 )
                .addNode("A3", subgraphA3 )
                .addNode("B", makeNode("B"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge(START, "A")
                .addEdge("B", END)
                .compile();

        var result = workflow.stream( GraphInput.args(Map.of("input", "test")), RunnableConfig.empty())
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("A", "A1.1", "A1.2", "A2.1", "A2.2", "A3.1", "A3.2", "B"), result.get().messages());

    }

}
