package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.junit.jupiter.api.Test;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;

/**
 * test issue <a href="https://github.com/bsorrentino/langgraph4j/issues/99">#99</a>
 */
public class Issue99Test {

    static class State extends AgentState {
        public static Map<String, Channel<?>> SCHEMA = Map.of();

        public State(Map<String, Object> initData) {
            super(initData);
        }

        public Optional<String> intent() {
            return  value("intent");
        }
    }

    static class IntentRecognizeNode implements NodeAction<State> {

        String intent;

        public void setIntent( String intent ) {
            this.intent = intent;
        }

        public String getIntent() {
            return intent;
        }

        @Override
        public Map<String, Object> apply(State state) {
            return Map.of( "intent", intent );
        }

    }

    private StateGraph<State> subGraph1() throws GraphStateException {
        // SubGraph1 Definition
        return new StateGraph<>( State::new )
                .addNode("work", node_async(state -> Map.of("step", "work1")))
                .addEdge(START, "work")
                .addEdge("work", END)
                ;

    }

    private StateGraph<State> subGraph2() throws GraphStateException {
        // SubGraph2 Definition
        return new StateGraph<>( State::new )
                .addNode("work", node_async(state -> Map.of("step", "work2")))
                .addEdge(START, "work")
                .addEdge("work", END)
                ;

    }

    @Test
    public void compliedSubgraphTest() throws GraphStateException {

        var subGraph1 = subGraph1().compile();
        var subGraph2 = subGraph2().compile();

        var intentRecognizeNode = new IntentRecognizeNode();

        // MainGraph Definition
        var graph = new StateGraph<>( State::new )
                .addNode("intent_recognize", node_async(intentRecognizeNode))
                .addNode("subAgent1", subGraph1)
                .addNode("subAgent2", subGraph2)

                .addEdge(START, "intent_recognize")
                .addConditionalEdges("intent_recognize",
                        edge_async( state ->
                                state.intent().orElseThrow() ),
                        Map.of("explain", "subAgent1",
                                "query", "subAgent2"
                        )
                )
                .addEdge("subAgent1", END)
                .addEdge("subAgent2", END)
                ;


        var workflow = graph.compile();

        // EXPLAIN
        intentRecognizeNode.setIntent("explain");
        var result = workflow.stream( GraphInput.args(Map.of("input", "explain me")), RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);
        assertTrue( result.isPresent() );
        assertEquals( "work1",result.get().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.get().intent().orElseThrow() );

        // QUERY
        intentRecognizeNode.setIntent("query");
        result = workflow.stream( GraphInput.args(Map.of("input", "search for")), RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);
        assertTrue( result.isPresent() );
        assertEquals( "work2",result.get().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.get().intent().orElseThrow() );
    }

    @Test
    public void stateSubgraphTest() throws GraphStateException {

        var subGraph1 = subGraph1();
        var subGraph2 = subGraph2();

        var intentRecognizeNode = new IntentRecognizeNode();

        // MainGraph Definition
        var graph = new StateGraph<>( State::new )
                .addNode("intent_recognize", node_async(intentRecognizeNode))
                .addNode("subAgent1", subGraph1)
                .addNode("subAgent2", subGraph2)
                .addEdge(START, "intent_recognize")
                .addConditionalEdges("intent_recognize",
                        edge_async( state ->
                                state.intent().orElseThrow() ),
                        Map.of("explain", "subAgent1",
                                "query", "subAgent2"
                        )
                )
                .addEdge("subAgent1", END)
                .addEdge("subAgent2", END)
                ;

        var workflow = graph.compile();

        // System.out.println( workflow.getGraph( GraphRepresentation.Type.PLANTUML, "", false ));

        // EXPLAIN
        intentRecognizeNode.setIntent("explain");
        var result = workflow.stream( GraphInput.args(Map.of("input", "explain me")), RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);
        assertTrue( result.isPresent() );
        assertEquals( "work1",result.get().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.get().intent().orElseThrow() );

        // QUERY
        intentRecognizeNode.setIntent("query");
        result = workflow.stream( GraphInput.args(Map.of("input", "search for")), RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);
        assertTrue( result.isPresent() );
        assertEquals( "work2",result.get().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.get().intent().orElseThrow() );



    }


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
    public void stateSubgraphWithInterruptionTest() throws GraphStateException {

        var subGraph1 = subGraph1();
        var subGraph2 = subGraph2();

        var intentRecognizeNode = new IntentRecognizeNode();

        // MainGraph Definition
        var graph = new StateGraph<>( State::new )
                .addNode("intent_recognize", node_async(intentRecognizeNode))
                .addNode("subAgent1", subGraph1)
                .addNode("subAgent2", subGraph2)
                .addEdge(START, "intent_recognize")
                .addConditionalEdges("intent_recognize",
                        edge_async( state ->
                                state.intent().orElseThrow() ),
                        Map.of("explain", "subAgent1",
                                "query", "subAgent2"
                        )
                )
                .addEdge("subAgent1", END)
                .addEdge("subAgent2", END)
                ;

        // EXPLAIN
        intentRecognizeNode.setIntent("explain");
        var result = evaluateWithConfig( graph,
                CompileConfig.builder()
                        .interruptBefore("subAgent1")
                        .build() );

        assertNull( result.state().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.state().intent().orElseThrow() );

        result = evaluateWithConfig( graph,
                CompileConfig.builder()
                        .interruptAfter( SubGraphNode.formatId("subAgent1","work") )
                        .build() );
        assertEquals( "work1",result.state().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.state().intent().orElseThrow() );

        // QUERY
        intentRecognizeNode.setIntent("query");
        result = evaluateWithConfig( graph,
                CompileConfig.builder()
                        .interruptAfter( SubGraphNode.formatId("subAgent1","work") )
                        .build() );
        assertEquals( "work2",result.state().data().get("step") );
        assertEquals( intentRecognizeNode.getIntent() ,result.state().intent().orElseThrow() );

    }

}
