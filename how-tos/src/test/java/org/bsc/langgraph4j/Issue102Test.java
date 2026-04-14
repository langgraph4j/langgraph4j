package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Issue102Test {

    @Test
    public void testWithStream() throws GraphStateException {

        var graph =  new StateGraph<>(AgentState::new)
        .addNode("node1", node_async( agentState -> Map.of("node1_result", "111") ) )
        .addNode("node2", node_async( agentState ->
             Map.of("node2_result", new AsyncGenerator.Base<StreamingOutput<?>>() {

                final StringBuffer sb = new StringBuffer();

                int i = 0;

                @Override
                public Data<StreamingOutput<?>> next() {

                    if (i == 3) {
                        //The context will be lost starting from here.
                        return Data.done( Map.of("node2_stream_result", sb.toString()) );
                        // return Data.done( mergeMap(agentState.data(), Map.of("node2_stream_result", sb.toString())) );
                    }

                    sb.append(i);

                    return Data.of(new StreamingOutput<>("%d".formatted(i++), "node2", agentState, null));
                }
            })))
        .addNode("node3", node_async( agentState -> Map.of("node3_result", "333") ) )
        .addEdge(START, "node1")
        .addEdge("node1", "node2")
        .addEdge("node2", "node3")
        .addEdge("node3", END)
        .compile()
        ;

        var result = graph.stream(Map.of("input", "test"))
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("node1_result", "input", "node3_result", "node2_stream_result"), result.get().data().keySet());
        assertIterableEquals(List.of("111", "test", "333", "012"), result.get().data().values());

    }

    @Test
    public void testWithSubgraph() throws GraphStateException {

        var subGraph =  new StateGraph<>(AgentState::new)
                                .addNode( "sb:node1", node_async(agentState -> Map.of("sb:node1_result", "222") ) )
                                .addEdge( START, "sb:node1" )
                                .addEdge( "sb:node1", END )
                                .compile()
                                ;

        var graph = new StateGraph<>(AgentState::new)
                .addNode("node1", node_async(agentState -> Map.of("node1_result", "111") ))
                .addNode("node2",subGraph )
                .addNode("node3", node_async( agentState -> Map.of("node3_result", "333") ) )
                .addEdge(START, "node1")
                .addEdge("node1", "node2")
                .addEdge("node2", "node3")
                .addEdge("node3", END)
                .compile()
                ;

        var result = graph.stream(Map.of("input", "test"))
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("node1_result", "input", "node3_result", "sb:node1_result"), result.get().data().keySet());
        assertIterableEquals(List.of("111", "test", "333", "222"), result.get().data().values());

    }

    @Test
    public void testWithSubgraphWithAppender() throws GraphStateException {

        var subGraph =  new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addNode( "sb:node1", node_async(agentState -> Map.of("messages", "sb:222") ) )
                .addEdge( START, "sb:node1" )
                .addEdge( "sb:node1", END )
                .compile()
                ;

        var graph = new StateGraph<MessagesState<String>>(MessagesState.SCHEMA, MessagesState::new)
                .addNode("node1", node_async(agentState -> Map.of("messages", "111") ))
                .addNode("node2",subGraph )
                .addNode("node3", node_async( agentState -> Map.of("messages", "333") ) )
                .addEdge(START, "node1")
                .addEdge("node1", "node2")
                .addEdge("node2", "node3")
                .addEdge("node3", END)
                .compile()
                ;

        var result = graph.stream(Map.of("input", "test"))
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("111", "sb:222", "333"), result.get().messages());
    }

}
