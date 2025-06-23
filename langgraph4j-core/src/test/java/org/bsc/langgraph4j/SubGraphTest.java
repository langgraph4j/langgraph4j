package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.CommandAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubGraphTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SubGraphTest.class);

    @BeforeAll
    public static void initLogging() throws IOException {
        try( var is = SubGraphTest.class.getResourceAsStream("/logging.properties") ) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

    private AsyncNodeAction<MessagesState<String>> _makeNode(String id ) {
        return node_async(state ->
                Map.of("messages", id)
        );
    }

    private List<String> _execute(CompiledGraph<?> workflow,
                                  Map<String,Object> input ) throws Exception {
        return workflow.stream(input)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
    }


    @Test
    public void testMergeSubgraph01() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addEdge("B2", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                ;

        var B_B1 = SubGraphNode.formatId( "B", "B1");
        var B_B2 = SubGraphNode.formatId( "B", "B2");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                "C",
                END
        ), _execute( app, Map.of() ) );

    }

    @Test
    public void testMergeSubgraph02() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addEdge("B2", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var processed = ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 4, processed.nodes().elements.size() );
        assertEquals( 5, processed.edges().elements.size() );

        var B_B1 = SubGraphNode.formatId( "B", "B1");
        var B_B2 = SubGraphNode.formatId( "B", "B2");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                "C",
                END
        ), _execute( app, Map.of() ) );

    }

    @Test
    public void testMergeSubgraph03() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var processed = ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 5, processed.nodes().elements.size() );
        assertEquals( 6, processed.edges().elements.size() );

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( app, Map.of() ) );

    }

    @Test
    public void testMergeSubgraph03WithInterruption() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var saver = new MemorySaver();

        var withSaver = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( withSaver, Map.of()) );

        // INTERRUPT AFTER B1
        var interruptAfterB1 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B1 )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A",
                B_B1
        ), _execute( interruptAfterB1, Map.of() ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptAfterB1, null ) );

        // INTERRUPT AFTER B2
        var interruptAfterB2 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B2 )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2
        ), _execute( interruptAfterB2, Map.of() ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                "C",
                END
        ), _execute( interruptAfterB2, null ) );

        // INTERRUPT BEFORE C
        var interruptBeforeC = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "C" )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C
        ), _execute( interruptBeforeC, Map.of() ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, null ) );

        // INTERRUPT BEFORE SUBGRAPH B
         var interruptBeforeSubgraphB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeSubgraphB, Map.of() ) );

        // RESUME AFTER SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptBeforeSubgraphB, null ) );

        // INTERRUPT AFTER SUBGRAPH B
        var exception = assertThrows( GraphStateException.class,
                () -> workflowParent.compile(
                        CompileConfig.builder()
                                .checkpointSaver(saver)
                                .interruptAfter( "B" )
                                .build()));

        assertEquals( "'interruption after' on subgraph is not supported yet! consider to use 'interruption before' node: 'C'", exception.getMessage());

    }

    @Test
    public void testMergeSubgraph04() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addConditionalEdges("B",
                        edge_async(state -> "c"),
                        Map.of( "c", "C", "a", "A"/*END, END*/) )
                .addEdge("C", END)
                ;

        var processed = ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 5, processed.nodes().elements.size() );
        assertEquals( 6, processed.edges().elements.size() );

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( app, Map.of() ) );

    }
    @Test
    public void testMergeSubgraph04WithInterruption() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addNode("C1", _makeNode("C1") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addConditionalEdges("B",
                        edge_async(state -> "c"),
                        Map.of( "c", "C1", "a", "A" /*END, END*/) )
                .addEdge("C1", "C")
                .addEdge("C", END)
                ;

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var saver = new MemorySaver();

        var withSaver = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( withSaver, Map.of()) );

        // INTERRUPT AFTER B1
        var interruptAfterB1 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B1 )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A",
                B_B1
        ), _execute( interruptAfterB1, Map.of() ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB1, null ) );

        // INTERRUPT AFTER B2
        var interruptAfterB2 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B2 )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2
        ), _execute( interruptAfterB2, Map.of() ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB2, null ) );

        // INTERRUPT BEFORE C
        var interruptBeforeC = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "C" )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C1"
        ), _execute( interruptBeforeC, Map.of() ) );

        // RESUME BEFORE C
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, null ) );

        // INTERRUPT BEFORE SUBGRAPH B
        var interruptBeforeB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeB, Map.of() ) );

        // RESUME BEFORE SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptBeforeB, null ) );

        //
        // INTERRUPT AFTER SUBGRAPH B
        //
        var exception = assertThrows( GraphStateException.class,
                () -> workflowParent.compile(
                    CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( "B" )
                        .build()));

        assertEquals( "'interruption after' on subgraph is not supported yet!", exception.getMessage());

    }

    @Test
    public void testMergeSubgraph05() throws Exception {

        RunnableConfig thread_2 = RunnableConfig.builder().threadId("thread_2").build();
        var graph = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ))
                .addNode("command_node",AsyncCommandAction.node_async((state, config) -> new Command("B2",Map.of("messages","go_to_command"))), Map.of( "B2", "B2"))
                .addEdge(START, "B1")
                .addEdge("B1", "command_node")
                .addEdge("B2", END)
                ;
        CompiledGraph<MessagesState<String>> compile = graph.compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).interruptAfter("command_node").build());
        assertEquals(List.of(START,"B1","command_node"),_execute(compile, Map.of()));
        assertEquals(List.of("B2",END),_execute(compile, null));
        CompiledGraph<MessagesState<String>> compile1 = graph.compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).interruptBefore("command_node").build());
        assertEquals(List.of(START,"B1"),_execute(compile1, Map.of()));
        assertEquals(List.of("command_node","B2",END),_execute(compile1, null));
    }


    @Test
    public void testCheckpointWithSubgraph() throws Exception {

        var compileConfig = CompileConfig.builder().checkpointSaver(new MemorySaver()).build();

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode("child:step1") )
                .addNode("step_2", _makeNode("child:step2") )
                .addNode("step_3", _makeNode("child:step3") )
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "step_3")
                .addEdge("step_3", END)
                //.compile(compileConfig)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode( "step1"))
                .addNode("step_2", _makeNode("step2"))
                .addNode("step_3",  _makeNode("step3"))
                .addNode("subgraph", workflowChild)
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "subgraph")
                .addEdge("subgraph", "step_3")
                .addEdge("step_3", END)
                .compile(compileConfig);


        var result = workflowParent.stream(Map.of())
                .stream()
                .peek( n -> log.info("{}", n) )
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of(
                "step1",
                "step2",
                "child:step1",
                "child:step2",
                "child:step3",
                "step3"), result.get().messages());

    }
    @Test
    public void testCheckpointWithCommandSubgraph() throws GraphStateException {
        var compileConfig = CompileConfig.builder().checkpointSaver(new MemorySaver()).build();
        var workflowChild = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode("child:step1") )
                .addNode("step_2", _makeNode("child:step2") )
                .addNode("step_3", _makeNode("child:step3") )
                .addNode("step_4", _makeNode("child:step4"))
                .addNode("command_node", AsyncCommandAction.node_async((state, config) -> new Command("step_4", Map.of("messages","go to command"))),Map.of("step_4","step_4","step_3","step_3"))
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "command_node")
                .addEdge("step_3",END)
                .addEdge("step_4",END)
                //.compile(compileConfig)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode( "step1"))
                .addNode("step_2", _makeNode("step2"))
                .addNode("step_3",  _makeNode("step3"))
                .addNode("subgraph", workflowChild)
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "subgraph")
                .addEdge("subgraph", "step_3")
                .addEdge("step_3", END)
                .compile(compileConfig);
        String plantuml = workflowParent.getGraph(GraphRepresentation.Type.PLANTUML).content();
        String mermaid=workflowParent.getGraph(GraphRepresentation.Type.MERMAID).content();
        System.out.println("===============plantuml===============");
        System.out.println(plantuml);
        System.out.println("===============mermaid===============");
        System.out.println(mermaid);

        MessagesState<String> state = workflowParent.invoke(Map.of()).orElseThrow();
        assertIterableEquals(List.of(
                "step1",
                "step2",
                "child:step1",
                "child:step2",
                "go to command",
                "child:step4",
                "step3"), state.messages());
    }

}
