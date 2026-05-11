package org.bsc.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

public class Issue100Test {

    private <S extends AgentState> NodeOutput<S> evaluateWithConfig( StateGraph<S> graph, CompileConfig config ) throws GraphStateException {
        var workflow = graph.compile(config);

        var result = workflow.stream( GraphInput.noArgs(), RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                ;

        assertTrue( result.isPresent() );
        return result.get();
    }

    @Test
    public void interruptAfterNodeAfterSTARTTest() throws GraphStateException {

        // Main Graph Definition
        var graph = new StateGraph<>( AgentState::new )
                .addNode("A", node_async( state -> Map.of("step", "A")))
                .addNode("B", node_async( state -> Map.of("step", "B")))
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge("B", END)
                ;

        var result = evaluateWithConfig( graph,
                CompileConfig.builder()
                        .interruptBefore("A")
                        .build() );
        assertEquals( START, result.node() );
        assertNull( result.state().data().get("step") );

        result = evaluateWithConfig( graph,
                CompileConfig.builder()
                .interruptAfter("A")
                .build() );

        assertEquals( "A",result.state().data().get("step") );

        result = evaluateWithConfig( graph,
                CompileConfig.builder()
                .interruptBefore("B")
                .build() );

        assertEquals( "A",result.state().data().get("step") );

        result = evaluateWithConfig( graph,
                CompileConfig.builder()
                        .interruptAfter("B")
                        .build() );
        assertEquals( END, result.node() );
        assertEquals( "B",result.state().data().get("step") );

    }


}
