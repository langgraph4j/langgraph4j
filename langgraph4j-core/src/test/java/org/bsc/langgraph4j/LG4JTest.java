package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.hook.*;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.*;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for simple App.
 */
public class LG4JTest implements LG4JLoggable {

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        int steps() {
            return this.<Integer>value("steps").orElse(0);
        }

    }

    static class NodeActionBuilder {
        String nodeId;
        GraphPath basePath;

        public NodeActionBuilder nodeId(String nodeId ) {
            this.nodeId = nodeId;
            return this;
        }
        public NodeActionBuilder path(GraphPath path ) {
            this.basePath = path;
            return this;
        }

        public Node.ActionFactory<State> build() {
            assertNotNull( nodeId );
            return ( CompileConfig compileConfig ) ->

                (state,config) -> {

                    assertEquals(nodeId, config.nodeId());

                    if( basePath != null ) {
                        log.info("nodePath: {}", config.nodePath());
                        assertEquals( basePath, config.nodePath().root() );
                    }

                    if(  compileConfig.graphId().isPresent() ) {
                        log.info("graphId: {} config.graphId: {}", compileConfig.graphId().get(), config.graphId().orElse("<NONE>>"));
                        assertTrue( config.graphId().isPresent() );
                        assertEquals(compileConfig.graphId().get(), config.graphId().get() );
                    }

                    return completedFuture( Map.of("messages", nodeId ));

                };

        }
    }

    private NodeActionBuilder actionBuilder() {
        return new NodeActionBuilder();
    }


    private AsyncNodeActionWithConfig<State> makeNode(String id) {
        return node_async((state,config) -> {
            log.info("call node {}", id);
            return Map.of("messages", id);
        });
    }


    public static <T> List<Map.Entry<String, T>> sortMap(Map<String, T> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
    }

    @Test
    void completableFutureException() {

        var future = CompletableFuture.supplyAsync( () -> "test" )
                .thenApply( v -> { throw new RuntimeException(v); } );

        var ex = assertThrowsExactly( CompletionException.class, future::join );

        assertInstanceOf( RuntimeException.class, ex.getCause() );
    }

    @Test
    void testValidation() throws Exception {

        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new);
        GraphStateException exception = assertThrows(GraphStateException.class, workflow::compile);
        System.out.println(exception.getMessage());
        assertEquals("missing Entry Point", exception.getMessage());

        workflow.addEdge(START, "agent_1");

        exception = assertThrows(GraphStateException.class, workflow::compile);
        assertEquals("edge sourceId 'agent_1' refers to undefined node!", exception.getMessage());

        workflow.addNode("agent_1", node_async((state,config) -> {
            log.info("agent_1 {}", state);
            return Map.of("prop1", "test");
        }));

        assertNotNull(workflow.compile());

        workflow.addEdge("agent_1", END);

        assertNotNull(workflow.compile());

        exception = assertThrows(GraphStateException.class, () ->
                workflow.addEdge(END, "agent_1"));
        log.info("{}", exception.getMessage());

        workflow.addNode("agent_2", node_async((state,config) -> {
            log.info("agent_2\n{}", state);
            return Map.of("prop2", "test");
        }));

        workflow.addEdge("agent_2", "agent_3");

        exception = assertThrows(GraphStateException.class, workflow::compile);
        log.info("{}", exception.getMessage());

        exception = assertThrows(GraphStateException.class, () ->
                workflow.addConditionalEdges("agent_1", edge_async(state -> "agent_3"), Map.of())
        );
        log.info("{}", exception.getMessage());

    }

    @Test
    public void testRunningOneNode() throws Exception {

        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async( (state, config) -> {

                    assertEquals( "agent_1", config.nodeId());

                    log.info("agent_1 {}", state);
                    return Map.of("prop1", "test");
                }))
                .addEdge("agent_1", END);

        CompiledGraph<AgentState> app = workflow.compile();

        Optional<AgentState> result = app.invoke(
                        GraphInput.args(Map.of("input", "test1")),
                        RunnableConfig.empty());
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1", "prop1", "test");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));

    }

    @Test
    public void testRunnableConfigMetadata() throws Exception {

        var agent = AsyncNodeActionWithConfig.node_async((state, config) -> {

            var currentNode = config.nodeId();
            assertEquals( "agent_1", currentNode);
            assertTrue(config.metadata("configData").isPresent());

            log.info("{} {}", currentNode, state);
            return Map.of("prop1", "test");
        });

        var workflow = new StateGraph<>(AgentState::new)
                .addEdge(START, "agent_1")
                .addNode("agent_1", agent)
                .addEdge("agent_1", END);

        var app = workflow.compile();

        var config = RunnableConfig.builder()
                .addMetadata("configData", "test")
                .build();

        var result = app.invoke(GraphInput.args(Map.of("input", "test1")), config);
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1", "prop1", "test");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));

    }

    @Test
    public void testRunningOneNodeOneRemoveByNull() throws Exception {

        Map<String, Channel<?>> schema = Map.of("prop1", Channels.base(null, null));

        StateGraph<AgentState> workflow = new StateGraph<>(schema, AgentState::new)
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async((state,config) -> {
                    var currentNode = config.nodeId();
                    assertEquals( "agent_1", currentNode);

                    log.info("{} {}", currentNode, state);

                    return Map.of("prop1", MARK_FOR_REMOVAL);

                }))
                .addEdge("agent_1", END);

        CompiledGraph<AgentState> app = workflow.compile();

        Optional<AgentState> result = app.invoke(
                GraphInput.args(Map.of("input", "test1", "prop1", "test")),
                RunnableConfig.empty());
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));
        //assertDictionaryOfAnyEqual( expected, result.data )

    }

    @Test
    void testWithAppender() throws Exception {

        StateGraph<State> workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent_1", node_async((state,config) -> {

                    var currentNode = config.nodeId();
                    assertEquals( "agent_1", currentNode);

                    log.info( "{}", currentNode );
                    return Map.of("messages", "message1");
                }))
                .addNode("agent_2", node_async((state,config) -> {
                    var currentNode = config.nodeId();
                    assertEquals( "agent_2", currentNode);

                    log.info( "{}", currentNode );
                    return Map.of("messages", new String[]{"message2"});
                }))
                .addNode("agent_3", node_async((state,config) -> {
                    var currentNode = config.nodeId();
                    assertEquals( "agent_3", currentNode);

                    log.info( "{}", currentNode );
                    int steps = state.messages().size() + 1;
                    return Map.of("messages", "message3", "steps", steps);
                }))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END);

        CompiledGraph<State> app = workflow.compile();

        Optional<State> result = app.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        log.info( "{}",result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(3, result.get().messages().size());
        assertIterableEquals(List.of("message1", "message2", "message3"), result.get().messages());

    }

    @Test
    void testWithAppenderOneRemove() throws Exception {

        StateGraph<State> workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent_1", node_async((state,config) -> {
                    log.info("agent_1");
                    return Map.of("messages", "message1");
                }))
                .addNode("agent_2", node_async((state,config) -> {
                    log.info("agent_2");
                    return Map.of("messages", new String[]{"message2"});
                }))
                .addNode("agent_3", node_async((state,config) -> {
                    log.info("agent_3");
                    int steps = state.messages().size() + 1;
                    return Map.of("messages", RemoveByHash.of("message2"), "steps", steps);
                }))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END);

        CompiledGraph<State> app = workflow.compile();

        Optional<State> result = app.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        log.info("{}", result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(1, result.get().messages().size());
        assertIterableEquals(List.of("message1"), result.get().messages());

    }

    @Test
    void testWithAppenderOneAppendOneRemove() throws Exception {

        StateGraph<State> workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent_1", node_async((state,config) ->
                        Map.of("messages", "message1")
                ))
                .addNode("agent_2", node_async((state,config) ->
                        Map.of("messages", new String[]{"message2"})
                ))
                .addNode("agent_3", node_async((state,config) ->
                        Map.of("messages", List.of("message3", RemoveByHash.of("message2")))
                ))
                .addNode("agent_4", node_async((state,config) -> {
                    int steps = state.messages().size() + 1;
                    return Map.of("messages", List.of("message4"), "steps", steps);

                }))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge("agent_3", "agent_4")
                .addEdge(START, "agent_1")
                .addEdge("agent_4", END);

        CompiledGraph<State> app = workflow.compile();

        Optional<State> result = app.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        System.out.println(result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(3, result.get().messages().size());
        assertIterableEquals(List.of("message1", "message3", "message4"), result.get().messages());

    }

    @Test
    void testWithParallelBranch() throws Exception {

        var workflow = new StateGraph<State>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        var app = workflow.compile();

        var runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor( "A", ForkJoinPool.commonPool() )
                .build( );

        var result = app.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);
        assertTrue(result.isPresent());
        assertIterableEquals(List.of("A", "A1", "A2", "A3", "B", "C"), result.get().messages());

        workflow = new StateGraph<>(State.SCHEMA, State::new)
                //.addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A1")
                .addEdge(START, "A2")
                .addEdge(START, "A3")
                .addEdge("C", END);

        app = workflow.compile();

        runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor( START, Executors.newSingleThreadExecutor() )
                .build( );

        result = app.stream(GraphInput.noArgs(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of("A1", "A2", "A3", "B", "C"), result.get().messages());

    }

    @Test
    void testWithParallelBranchWithErrors() throws Exception {

        // ONLY ONE TARGET
        var onlyOneTarget = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "C")
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        var exception = assertThrows(GraphStateException.class, onlyOneTarget::compile);
        assertEquals("parallel node [A] must have only one target, but [B, C] have been found!", exception.getMessage());

        var noConditionalEdge = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        exception = assertThrows(GraphStateException.class, () -> noConditionalEdge.addConditionalEdges("A",
                edge_async(state -> "next"),
                Map.of("next", "A2")));
        assertEquals("conditional edge from 'A' already exist!", exception.getMessage());

        var noConditionalEdgeOnBranch = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addConditionalEdges("A3",
                        edge_async(state -> "next"),
                        Map.of("next", "B"))
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        exception = assertThrows(GraphStateException.class, noConditionalEdgeOnBranch::compile);
        assertEquals("parallel node doesn't support conditional branch, but on [A] a conditional branch on [A3] have been found!", exception.getMessage());

        var noDuplicateTarget = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("A1", makeNode("A1"))
                .addNode("A2", makeNode("A2"))
                .addNode("A3", makeNode("A3"))
                .addNode("B", makeNode("B"))
                .addNode("C", makeNode("C"))
                .addEdge("A", "A1")
                .addEdge("A", "A2")
                .addEdge("A", "A3")
                .addEdge("A", "A2")
                .addEdge("A1", "B")
                .addEdge("A2", "B")
                .addEdge("A3", "B")
                .addEdge("B", "C")
                .addEdge(START, "A")
                .addEdge("C", END);

        exception = assertThrows(GraphStateException.class, noDuplicateTarget::compile);
        assertEquals("edge [A] has duplicate targets [A2]!", exception.getMessage());

    }

    @Test
    void testGetResultFromGenerator() throws Exception {
        var workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addEdge(START, "agent_1")
                .addNode("agent_1", makeNode("agent_1"))
                .addEdge("agent_1", END);

        var app = workflow.compile();

        var iterator = app.stream(GraphInput.noArgs(), RunnableConfig.empty());
        for (var i : iterator) {
            System.out.println(i);
        }

        var resultValue = GraphResult.from(iterator);

        assertFalse(resultValue.isEmpty());

        System.out.println(resultValue);
    }

    @Test
    void testCommandNode_Issue163() throws Exception {
        AsyncCommandAction<State> commandAction = (state, config) ->
            completedFuture( new Command("C2",
                    Map.of( "messages", "B",
                            "next_node", "C2")) );

        var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("A", makeNode("A"))
                .addNode("B", commandAction, EdgeMappings.builder()
                        .toEND()
                        .to("C1")
                        .to("C2")
                        .build())
                .addNode("C1", makeNode("C1"))
                .addNode("C2", makeNode("C2"))
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge( "C1", END )
                .addEdge( "C2", END )
                .compile();

        var steps = graph.stream(GraphInput.noArgs(), RunnableConfig.empty()).stream()
                .peek(System.out::println)
                .toList();

        assertEquals(5, steps.size());
        assertEquals( "B", steps.get(2).node());
        assertEquals( "C2", steps.get(2).state().value("next_node").orElse(null));

    }

    @Test
    public void testNestedNodeWrapHooks() throws Exception {
        final Map<String,Channel<?>> schema = mergeMap( MessagesState.SCHEMA,
                Map.of( NestedNodeHook.HOOKS_ATTRIBUTE, new RegisterHookChannel() ));

        var workflow = new StateGraph<>(schema, State::new)
                .addWrapCallNodeHook( NestedNodeHook.<State>of("wrap-global-1").applyWrapHook() )
                .addBeforeCallNodeHook( NestedNodeHook.<State>of("before-global-1").applyBeforeHook(schema))
                .addBeforeCallNodeHook( NestedNodeHook.<State>of("before-global-2").applyBeforeHook(schema))
                .addAfterCallNodeHook( NestedNodeHook.<State>of("after-global-1").applyAfterHook(schema) )
                .addNode("node_1", actionBuilder().nodeId("node_1").build() )
                .addNode("node_2", actionBuilder().nodeId("node_2").build() )
                .addNode("node_3", actionBuilder().nodeId("node_3").build() )
                .addNode("node_4", actionBuilder().nodeId("node_4").build() )
                .addEdge(START, "node_1")
                .addEdge("node_1", "node_2")
                .addEdge("node_2", "node_3")
                .addEdge("node_3", "node_4")
                .addEdge("node_4", END)
                .compile();

        var result = workflow.invoke(   GraphInput.args(Map.of("input", "test1")),
                RunnableConfig.empty());
        assertTrue( result.isPresent() );
        var state = result.get();
        assertIterableEquals( List.of("node_1", "node_2", "node_3", "node_4"), state.messages());
        assertTrue( state.value(NestedNodeHook.HOOKS_ATTRIBUTE).isPresent());
        assertInstanceOf( Map.class,  state.value(NestedNodeHook.HOOKS_ATTRIBUTE).get() );
        @SuppressWarnings("unchecked")
        var hooksValueMap = (Map<String, List<String>>)state.value(NestedNodeHook.HOOKS_ATTRIBUTE).get();
        assertTrue( hooksValueMap.containsKey("node_1") );
        final var traceList = List.of( "before-global-2", "before-global-1", "wrap-global-1");
        assertIterableEquals( traceList,  hooksValueMap.get("node_1") );
        assertIterableEquals( traceList,  hooksValueMap.get("node_2") );
        assertIterableEquals( traceList,  hooksValueMap.get("node_3") );
        assertIterableEquals( traceList,  hooksValueMap.get("node_4") );

    }

    @Test
    public void testNestedNodeAndEdgeWrapHooks() throws Exception {
        final Map<String,Channel<?>> schema = mergeMap( MessagesState.SCHEMA,
                Map.of( NestedNodeHook.HOOKS_ATTRIBUTE, new RegisterHookChannel(),
                        NestedEdgeHook.HOOKS_ATTRIBUTE, new RegisterHookChannel() ));

        EdgeHook.AfterCall<State> afterEdgeHookGoToEnd = ( sourceId, s, c, lastResult ) -> {
            assertEquals( sourceId, c.nodeId());
            return completedFuture(new Command(END, lastResult.update()));
        };

        var workflow = new StateGraph<>(schema, State::new)
                .addWrapCallNodeHook( NestedNodeHook.<State>of("wrap-global-1").applyWrapHook())
                .addBeforeCallNodeHook( NestedNodeHook.<State>of("before-global-1").applyBeforeHook(schema))
                .addBeforeCallNodeHook( NestedNodeHook.<State>of("before-global-2").applyBeforeHook(schema))
                .addAfterCallNodeHook( NestedNodeHook.<State>of("after-global-1").applyAfterHook(schema))
                .addAfterCallEdgeHook( "node_2", afterEdgeHookGoToEnd)
                .addNode("node_1", actionBuilder().nodeId("node_1").build() )
                .addNode("node_2", actionBuilder().nodeId("node_2").build() )
                .addNode("node_3", actionBuilder().nodeId("node_3").build() )
                .addNode("node_4", actionBuilder().nodeId("node_4").build() )
                .addEdge(START, "node_1")
                .addEdge("node_1", "node_2")
                .addConditionalEdges("node_2",
                        command_async( ( s, c ) -> new Command("node_3")),
                        EdgeMappings.builder()
                                .to("node_3")
                                .toEND()
                                .build())
                .addEdge("node_3", "node_4")
                .addEdge("node_4", END)
                .compile();

        var result = workflow.invoke(   GraphInput.args(Map.of("input", "test1")),
                RunnableConfig.empty());
        assertTrue( result.isPresent() );
        var state = result.get();
        assertIterableEquals( List.of("node_1", "node_2"), state.messages());
        assertTrue( state.value(NestedNodeHook.HOOKS_ATTRIBUTE).isPresent());
        assertInstanceOf( Map.class,  state.value(NestedNodeHook.HOOKS_ATTRIBUTE).get() );
        @SuppressWarnings("unchecked")
        var hooksValueMap = (Map<String, List<String>>)state.value(NestedNodeHook.HOOKS_ATTRIBUTE).get();
        assertTrue( hooksValueMap.containsKey("node_1") );
        final var traceList = List.of( "before-global-2", "before-global-1", "wrap-global-1");
        assertIterableEquals( traceList,  hooksValueMap.get("node_1") );
        assertIterableEquals( traceList,  hooksValueMap.get("node_2") );
        assertNull( hooksValueMap.get("node_3") );
        assertNull( hooksValueMap.get("node_4") );

    }

    @Test
    void issue370() {

        final var sourceMap = new LinkedHashMap<String,String>(10);
        for( int i = 0; i < 10 ; ++i ) {
            final var key = "key%02d".formatted(i);
            if( i%3 == 0 ) {
                sourceMap.put( key, null );
                continue;
            }
            sourceMap.put( key, "value%02d".formatted(i));
        }

        final var result = sourceMap.entrySet().stream()
                .filter( e -> e.getKey() != null )
                .collect( LinkedHashMap::new,
                        ( map, entry ) ->
                                map.put( entry.getKey(), entry.getValue() ),
                        Map::putAll );

        assertEquals(sourceMap,result);

    }

    @Test
    void testHandleRuntimeException() throws GraphStateException {
        final var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .addNode( "node_with_exception", ( state, config ) ->
                    failedFuture(new RuntimeException("test exception"))
                )
                .addEdge(START, "node_with_exception")
                .addEdge("node_with_exception", END)
                .compile();
                ;

        try {
            workflow.invoke( GraphInput.noArgs(), RunnableConfig.builder()
                                                    .addMetadata(RunnableConfig.GRAPH_ID, "handle_exception")
                                                    .build() );
        }
        catch( Exception ex ) {

            final var runException = ExceptionUtils.findCauseByType(ex, GraphRunnerException.class);
            assertTrue( runException.isPresent() );
            final var config = runException.get().config();
            assertEquals("node_with_exception", config.nodeId() );
            assertTrue( config.graphId().isPresent() );
            assertEquals("handle_exception", config.graphId().orElse(null) );

            final var rootCause = ExceptionUtils.getRootCause( runException.get() );

            assertEquals( "test exception", rootCause.getMessage() );

        }
    }
    @Test

    void testHandleGraphRunException() throws GraphStateException {
        final var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .addNode( "node_with_exception", ( state, config ) ->
                        failedFuture(new GraphRunnerException( config, "test exception"))
                )
                .addEdge(START, "node_with_exception")
                .addEdge("node_with_exception", END)
                .compile();
        ;

        try {
            workflow.invoke( GraphInput.noArgs(), RunnableConfig.builder()
                    .addMetadata(RunnableConfig.GRAPH_ID, "handle_exception")
                    .build() );
        }
        catch( Exception ex ) {

            final var rootException = ExceptionUtils.getRootCause(ex);
            assertInstanceOf( GraphRunnerException.class, rootException);
            final var runException = (GraphRunnerException)rootException;
            final var config = runException.config();
            assertEquals("node_with_exception", config.nodeId() );
            assertTrue( config.graphId().isPresent() );
            assertEquals("handle_exception", config.graphId().orElse(null) );
            assertEquals( "test exception", runException.getMessage() );

        }
    }
}