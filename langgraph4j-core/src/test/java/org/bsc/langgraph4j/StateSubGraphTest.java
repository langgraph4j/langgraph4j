package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

public class StateSubGraphTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateSubGraphTest.class);

    static class WrapCallLogHook implements NodeHook.WrapCall<MessagesState<String>>, EdgeHook.WrapCall<MessagesState<String>> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                                MessagesState<String> state,
                                                                RunnableConfig config,
                                                                AsyncNodeActionWithConfig<MessagesState<String>> action) {

            log.info("node start: '{}'", nodeId);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                log.info("node end: '{}'", nodeId);

            });
        }

        @Override
        public CompletableFuture<Command> applyWrap(String sourceId,
                                                    MessagesState<String> state,
                                                    RunnableConfig config,
                                                    AsyncCommandAction<MessagesState<String>> action) {
            log.info("edge start from: '{}'", sourceId);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                log.info("edge end: {}", result);

            });
        }
    }

    @BeforeAll
    public static void initLogging() throws IOException {
        try( var is = StateSubGraphTest.class.getResourceAsStream("/logging.properties") ) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

    private AsyncNodeAction<MessagesState<String>> _makeNode(String id ) {
        return node_async(state ->
                Map.of("messages", id)
        );
    }

    private List<String> _execute(CompiledGraph<?> workflow,
                                  GraphInput input ) throws Exception {
        return workflow.stream(input, RunnableConfig.empty() )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
    }
    @Test
    public void testStateSubgraphSimple() throws Exception {

        AsyncNodeActionWithConfig<LG4JTest.State> childStep1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step1"));

        AsyncNodeActionWithConfig<LG4JTest.State> childStep2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step2"));

        AsyncNodeActionWithConfig<LG4JTest.State> childStep3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step3"));

        var workflowChild = new StateGraph<>(LG4JTest.State.SCHEMA, LG4JTest.State::new)
                .addNode("child:step_1", childStep1)
                .addNode("child:step_2", childStep2)
                .addNode("child:step_3", childStep3)
                .addEdge(START, "child:step_1")
                .addEdge("child:step_1", "child:step_2")
                .addEdge("child:step_2", "child:step_3")
                .addEdge("child:step_3", END)
                ;
        AsyncNodeActionWithConfig<LG4JTest.State> step1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step1"));

        AsyncNodeActionWithConfig<LG4JTest.State> step2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step2"));

        AsyncNodeActionWithConfig<LG4JTest.State> step3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step3"));

        var workflowParent = new StateGraph<>(LG4JTest.State.SCHEMA, LG4JTest.State::new)
                .addNode("step_1", step1)
                .addNode("step_2", step2)
                .addNode("step_3", step3)
                .addNode("subgraph", workflowChild)
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "subgraph")
                .addEdge("subgraph", "step_3")
                .addEdge("step_3", END)
                .compile();

        var result = workflowParent.stream(GraphInput.noArgs(), RunnableConfig.empty())
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("step1", "step2", "child:step1", "child:step2", "child:step3", "step3"), result.get().messages());

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
        ), _execute( app, GraphInput.args(Map.of()) ) );

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

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
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
        ), _execute( app, GraphInput.args(Map.of()) ) );

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

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
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
        ), _execute( app, GraphInput.args(Map.of()) ) );

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
        ), _execute( withSaver, GraphInput.args(Map.of())) );

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
        ), _execute( interruptAfterB1, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptAfterB1, GraphInput.resume() ) );

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
        ), _execute( interruptAfterB2, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                "C",
                END
        ), _execute( interruptAfterB2, GraphInput.resume() ) );

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
        ), _execute( interruptBeforeC, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, GraphInput.resume() ) );

        // INTERRUPT BEFORE SUBGRAPH B
         var interruptBeforeSubgraphB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeSubgraphB, GraphInput.args(Map.of()) ) );

        // RESUME AFTER SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptBeforeSubgraphB, GraphInput.resume() ) );

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

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 6, processed.nodes().elements.size() );
        assertEquals( 7, processed.edges().elements.size() );

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");
        var B_END_BRIDGE = SubGraphNode.formatId( "B", END);

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                B_END_BRIDGE,
                "C",
                END
        ), _execute( app, GraphInput.args(Map.of()) ) );

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
        var B_END_BRIDGE = SubGraphNode.formatId( "B", END);

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
                B_END_BRIDGE,
                "C1",
                "C",
                END
        ), _execute( withSaver, GraphInput.args(Map.of())) );

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
        ), _execute( interruptAfterB1, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                B_END_BRIDGE,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB1, GraphInput.resume() ) );

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
        ), _execute( interruptAfterB2, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                B_END_BRIDGE,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB2, GraphInput.resume() ) );

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
                B_END_BRIDGE,
                "C1"
        ), _execute( interruptBeforeC, GraphInput.args(Map.of()) ) );

        // RESUME BEFORE C
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, GraphInput.resume() ) );

        // INTERRUPT BEFORE SUBGRAPH B
        var interruptBeforeB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeB, GraphInput.args(Map.of()) ) );

        // RESUME BEFORE SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                B_END_BRIDGE,
                "C1",
                "C",
                END
        ), _execute( interruptBeforeB, GraphInput.resume() ) );

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
    public void testSubgraphWithConditionalStartEdge() throws Exception {
        // Subgraph: The START edge is the conditional edge
        var workflowChild = new MessagesStateGraph<String>()
            .addNode("B1", _makeNode("B1"))
            .addNode("B2", _makeNode("B2"))
            .addNode("B3", _makeNode("B3"))
            .addConditionalEdges(START,
                edge_async(state -> "route1"),
                Map.of("route1", "B1", "route2", "B2"))
            .addEdge("B1", "B3")
            .addEdge("B2", "B3")
            .addEdge("B3", END);

        var workflowParent = new MessagesStateGraph<String>()
            .addNode("A", _makeNode("A"))
            .addNode("B", workflowChild)
            .addNode("C", _makeNode("C"))
            .addEdge(START, "A")
            .addEdge("A", "B")
            .addEdge("B", "C")
            .addEdge("C", END);

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process(
            workflowParent,
            CompileConfig.builder().build());

        // Validate the number of nodes processed
        // Origin：START, A, B(START->B1/B2/B3->END), C, END
        // Process：START, A, B_START(empty node), B_B1, B_B2, B_B3, C, END
        assertEquals(6, processed.nodes().elements.size());
        var B_START = SubGraphNode.formatId("B", START);
        var B_B1 = SubGraphNode.formatId("B", "B1");
        var B_B2 = SubGraphNode.formatId("B", "B2");
        var B_B3 = SubGraphNode.formatId("B", "B3");
        // 验证所有格式化的子图节点都存在
        assertTrue(processed.nodes().elements.stream()
            .anyMatch(n -> n.id().equals(B_START)));
        assertTrue(processed.nodes().elements.stream()
            .anyMatch(n -> n.id().equals(B_B1)));
        assertTrue(processed.nodes().elements.stream()
            .anyMatch(n -> n.id().equals(B_B2)));
        assertTrue(processed.nodes().elements.stream()
            .anyMatch(n -> n.id().equals(B_B3)));
        var app = workflowParent.compile();

        // Test the path routed to B1
        var result1 = _execute(app, GraphInput.args(Map.of()));
        assertIterableEquals(List.of(
            START,
            "A",
            B_START,
            B_B1,
            B_B3,
            "C",
            END
        ), result1);
    }

    @Test
    public void testCheckpointWithMergeSubgraph() throws Exception {

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


        var result = workflowParent.stream(GraphInput.noArgs(), RunnableConfig.empty() )
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
    public void testSubgraphWithConditionalStartAndEndEdges() throws Exception {

        final var logHook = new WrapCallLogHook();

        // Subgraph: START edge is conditional in subgraph,
        //           END edge is conditional in parent (from subgraph END to parent nodes)
        var workflowChild = new MessagesStateGraph<String>()
                .addWrapCallNodeHook( logHook )
                .addWrapCallEdgeHook( logHook )
                .addNode("B1", _makeNode("B1"))
                .addNode("B2", _makeNode("B2"))
                .addNode("B3", _makeNode("B3"))
                .addNode("B4", _makeNode("B4"))
                .addConditionalEdges(START,
                        edge_async(state -> "B1"),
                        EdgeMappings.builder()
                                .to("B1")
                                .to("B2")
                                .build())
                .addEdge("B1", "B3")
                .addEdge("B2", "B3")
                .addEdge("B3", "B4")
                .addEdge("B4", END);
        var workflowParent = new MessagesStateGraph<String>()
                .addWrapCallNodeHook( logHook )
                .addWrapCallEdgeHook( logHook )
                .addNode("A", _makeNode("A"))
                .addNode("B", workflowChild)
                .addNode("C", _makeNode("C"))
                .addNode("D", _makeNode("D"))
                .addNode("E", _makeNode("E"))
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addConditionalEdges("B",
                        edge_async(state -> "C"),
                        EdgeMappings.builder()
                                .to("C")
                                .to("D")
                                .to("E")
                                .build())
                .addEdge("C", END)
                .addEdge("D", END)
                .addEdge("E", END);
        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process(
                workflowParent,
                CompileConfig.builder().build());
        // Validate the number of nodes processed
        // Origin: START, A, B(subgraph with conditional START), C, D, E, END
        // Process: START, A, B_START, B_B1, B_B2, B_B3, B_B4, B_END, C, D, E, END
        processed.nodes().elements.forEach(System.out::println);
        processed.edges().elements.forEach(System.out::println);
        assertEquals(10, processed.nodes().elements.size());
        var B_START = SubGraphNode.formatId("B", START);
        var B_B1 = SubGraphNode.formatId("B", "B1");
        var B_B2 = SubGraphNode.formatId("B", "B2");
        var B_B3 = SubGraphNode.formatId("B", "B3");
        var B_B4 = SubGraphNode.formatId("B", "B4");
        var B_END = SubGraphNode.formatId("B", END);
        // Verify all formatted subgraph nodes exist
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_START)));
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_B1)));
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_B2)));
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_B3)));
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_B4)));
        assertTrue(processed.nodes().elements.stream()
                .anyMatch(n -> n.id().equals(B_END)));
        // Verify START bridge has conditional edge to B1 and B2
        var edges = processed.edges().elements;
        var startBridgeEdge = edges.stream()
                .filter(e -> e.sourceId().equals(B_START))
                .filter(e -> e.target().value() != null)
                .findFirst();

        assertTrue(startBridgeEdge.isPresent(), "START bridge node should have conditional edge");
        var startMappings = startBridgeEdge.get().target().value().mappings();
        assertTrue(startMappings.containsValue(B_B1));
        assertTrue(startMappings.containsValue(B_B2));
        // Verify END bridge has conditional edge to C, D, E
        var endBridgeEdge = edges.stream()
                .filter(e -> e.sourceId().equals(B_END))
                .filter(e -> e.target().value() != null)
                .findFirst();

        assertTrue(endBridgeEdge.isPresent(), "END bridge node should have conditional edge");
        var endMappings = endBridgeEdge.get().target().value().mappings();
        assertTrue(endMappings.containsValue("C"));
        assertTrue(endMappings.containsValue("D"));
        assertTrue(endMappings.containsValue("E"));

        var app = workflowParent.compile();
        // Test the execution path - START routes to B1, END routes to C
        var result = _execute(app, GraphInput.args(Map.of()));
        assertIterableEquals(List.of(
                START,
                "A",
                B_START,
                B_B1,
                B_B3,
                B_B4,
                B_END,
                "C",
                END
        ), result);
    }



}
