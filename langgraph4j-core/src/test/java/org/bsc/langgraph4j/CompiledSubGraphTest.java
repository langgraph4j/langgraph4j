package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeResult;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.exception.SubGraphInterruptionException;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.hook.WrapCallHookSubgraphAware;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.subgraph.SubGraphOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

public class CompiledSubGraphTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CompiledSubGraphTest.class);

    static class MyState extends MessagesState<String> {

        public MyState(Map<String, Object> initData) {
            super(initData);
        }

        boolean resumeSubgraph() {
            return this.<Boolean>value("resume_subgraph")
                    .orElse(false);
        }
    }

    static class WrapCallHook extends WrapCallHookSubgraphAware<MyState> {
        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, MyState state, RunnableConfig config, AsyncNodeActionWithConfig<MyState> action) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public CompletableFuture<NodeResult> applyWrapWithResult(String nodeId,
                                                                 MyState state,
                                                                 RunnableConfig config,
                                                                 AsyncNodeActionWithConfig<MyState> action) {

            isSubgraphEnded( config ).ifPresent(
                    item -> System.out.printf("[%s] ended%n", item.nodeId()));

            System.out.printf("[%s] start%n", nodeId);

            return action.applyWithResult( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                isSubgraphRequested( nodeId, config, result ).ifPresentOrElse(
                        item -> System.out.printf( "subgraph requested: [%s]%n", item ),
                        () -> System.out.printf("[%s] end%n", nodeId));
            });
        }
    }

    static class NodeActionBuilder {
        String nodeId;
        GraphPath basePath;
        String attributeKey;
        boolean enableLog = true;

        public NodeActionBuilder nodeId( String nodeId ) {
            this.nodeId = nodeId;
            return this;
        }
        public NodeActionBuilder path(GraphPath path ) {
            this.basePath = path;
            return this;
        }
        public NodeActionBuilder attributeKey(String attributeKey ) {
            this.attributeKey = attributeKey;
            return this;
        }
        public NodeActionBuilder enableLog( boolean enable ) {
            this.enableLog = enable;
            return this;
        }

        public Node.ActionFactory<MyState> build() {
            assertNotNull( nodeId );
            return ( CompileConfig compileConfig ) ->

             (state,config) -> {

                assertEquals(nodeId, config.nodeId());

                if( basePath != null ) {
                    if( enableLog ) log.info("graphPath: {}", config.graphPath());
                    assertEquals( basePath, config.graphPath() );
                }

                if(  compileConfig.graphId().isPresent() ) {
                    if( enableLog ) log.info("graphId: {} config.graphId: {}", compileConfig.graphId().get(), config.graphId().orElse("<NONE>>"));
                    assertTrue( config.graphId().isPresent() );
                    assertEquals(compileConfig.graphId().get(), config.graphId().get() );
                }

                if( attributeKey != null ) {
                    var attributeValue = state.value(attributeKey).orElse("");
                    return completedFuture(Map.of("messages", "[%s%s]".formatted( nodeId, attributeValue )));
                }

                return completedFuture(Map.of("messages", "[%s]".formatted( nodeId )));

            };

        }
    }

    private NodeActionBuilder actionBuilder() {
        return new NodeActionBuilder();
    }

    private Node.ActionFactory<MyState> buildActionFactory(String nodeId) {
        return actionBuilder().nodeId( nodeId ).build();
    }

    private Node.ActionFactory<MyState> buildActionFactory(String nodeId, String attributeKey) {
        return actionBuilder().nodeId( nodeId ).attributeKey( attributeKey ).build();
    }

    private AsyncNodeActionWithConfig<MyState> buildSubgraphAction(String parentNodeId, CompiledGraph<MyState> subGraph) {
        final var runnableConfig = RunnableConfig.builder()
                .threadId(format("%s_subgraph", parentNodeId))
                .build();
        return node_async((state,config) -> {

            var input = (state.resumeSubgraph()) ?
                    GraphInput.resume() :
                    GraphInput.args(state.data());

            var output = subGraph.stream(input, runnableConfig).stream()
                    .reduce((a, b) -> b)
                    .orElseThrow();

            if (!output.isEND()) {
                throw new SubGraphInterruptionException(parentNodeId,
                        output.node(),
                        mergeMap(output.state().data(), Map.of("resume_subgraph", true)));
            }
            return mergeMap(output.state().data(), Map.of("resume_subgraph", AgentState.MARK_FOR_REMOVAL));
        });
    }

    private CompiledGraph<MyState> subGraphWithInterruption( GraphPath graphPath, BaseCheckpointSaver saver) throws Exception {

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter("NODE3.2")
                .build();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        return new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", actionBuilder().nodeId("NODE3.1").path(graphPath).build())
                .addNode("NODE3.2", actionBuilder().nodeId("NODE3.2").path(graphPath).build())
                .addNode("NODE3.3", actionBuilder().nodeId("NODE3.3").path(graphPath).build())
                .addNode("NODE3.4", actionBuilder().nodeId("NODE3.4")
                                                        .path(graphPath)
                                                        .attributeKey("newAttribute").build())
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class     )
    private void testCompileSubGraphInterruptionUsingException( CompiledGraph.StreamMode mode ) throws Exception {

        var saver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var subGraph = subGraphWithInterruption( GraphPath.of("NODE3"), saver); // create subgraph

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", buildActionFactory("NODE1"))
                .addNode("NODE2", buildActionFactory("NODE2"))
                .addNode("NODE3", buildSubgraphAction("NODE3", subGraph))
                .addNode("NODE4", buildActionFactory("NODE4"))
                .addNode("NODE5", buildActionFactory("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);

        var runnableConfig = RunnableConfig.builder()
                                .streamMode(mode)
                                .build();

        var input = GraphInput.args(Map.of());

        do {
            try {
                for (var output : parentGraph.stream(input, runnableConfig)) {
                    // log.info("output: {}", output);
                }
                break;
            }
            catch( Exception ex ) {
                var interruptException = SubGraphInterruptionException.from(ex);
                if( interruptException.isPresent() ) {

                    log.info("SubGraphInterruptionException: {}", interruptException.get().getMessage());
                    var interruptionState = interruptException.get().state();


                    // ==== METHOD 1 =====
                    // FIND NODE BEFORE SUBGRAPH AND RESUME
                    /*
                    StateSnapshot<?> lastNodeBeforeSubGraph = workflow.getStateHistory(runnableConfig).stream()
                                                                .skip(1)
                                                                .findFirst()
                                                                .orElseThrow( () -> new IllegalStateException("lastNodeBeforeSubGraph is null"));
                    var nodeBeforeSubgraph = lastNodeBeforeSubGraph.node();
                    runnableConfig = workflow.updateState( lastNodeBeforeSubGraph.config(), interruptionState );
                    */

                    // ===== METHOD 2 =======
                    // UPDATE STATE ASSUMING TO BE ON NODE BEFORE SUBGRAPH ('NODE2') AND RESUME
                    var nodeBeforeSubgraph = "NODE2";
                    runnableConfig = parentGraph.updateState( runnableConfig, interruptionState, nodeBeforeSubgraph );
                    input = GraphInput.resume();

                    log.info( "RESUME GRAPH FROM END OF NODE: {}", nodeBeforeSubgraph);
                    continue;
                }

                throw ex;
            }
        } while( true );

    }

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class )
    public void testCompileSubGraphInterruptionSharingSaver(  CompiledGraph.StreamMode mode ) throws Exception {

        var saver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var subGraph = subGraphWithInterruption(GraphPath.of("NODE3"), saver); // create subgraph

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", buildActionFactory("NODE1"))
                .addNode("NODE2", buildActionFactory("NODE2"))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", buildActionFactory("NODE4"))
                .addNode("NODE5", buildActionFactory("NODE5", "newAttribute"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);

        var runnableConfig = RunnableConfig.builder()
                .threadId("1")
                .streamMode(mode)
                .build();

        var input = GraphInput.args(Map.of());

        var graphIterator = parentGraph.stream(input, runnableConfig);

        var output = graphIterator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );

        assertFalse( output.get().isEND() );
        assertInstanceOf(SubGraphOutput.class,  output.get() );

        var iteratorResult = GraphResult.from(graphIterator);

        assertFalse( iteratorResult.isEmpty() );
        assertTrue(iteratorResult.isInterruptionMetadata());

        // runnableConfig = parentGraph.updateState( runnableConfig, Map.of( "newAttribute", "<myNewValue>") );
        //input = GraphInput.resume();

        input = GraphInput.resume(Map.of( "newAttribute", "<myNewValue>"));

        graphIterator = parentGraph.stream(input, runnableConfig);

        output = graphIterator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );

        assertIterableEquals(List.of(
                "[NODE1]",
                "[NODE2]",
                "[NODE3.1]",
                "[NODE3.2]",
                "[NODE3.3]",
                "[NODE3.4<myNewValue>]",
                "[NODE4]",
                "[NODE5<myNewValue>]"), output.get().state().messages() );
    }

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class     )
    public void testCompileSubGraphInterruptionWithDifferentSaver( CompiledGraph.StreamMode mode ) throws Exception {

        var parentSaver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        BaseCheckpointSaver childSaver = new MemorySaver();

        var subGraph = subGraphWithInterruption( GraphPath.of("NODE3"), childSaver ); // create subgraph

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(parentSaver)
                .build();

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", buildActionFactory("NODE1"))
                .addNode("NODE2", buildActionFactory("NODE2"))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", buildActionFactory("NODE4", "newAttribute"))
                .addNode("NODE5", buildActionFactory("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);

        var runnableConfig = RunnableConfig.builder()
                                .streamMode(mode)
                                .build();

        var input = GraphInput.args(Map.of());

        var graphIterator = parentGraph.stream(input, runnableConfig);

        var output = graphIterator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );

        assertFalse( output.get().isEND() );
        assertInstanceOf( SubGraphOutput.class, output.get() );

        var iteratorResult = GraphResult.from(graphIterator);

        assertFalse( iteratorResult.isEmpty() );
        assertTrue( iteratorResult.isInterruptionMetadata() );

        // runnableConfig = parentGraph.updateState( runnableConfig, Map.of( "newAttribute", "<myNewValue>") );
        // input = GraphInput.resume();

        input = GraphInput.resume( Map.of( "newAttribute", "<myNewValue>") );
        graphIterator = parentGraph.stream(input, runnableConfig);

        output = graphIterator.stream()
                //.peek( out -> log.info("output: {}}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );

        assertIterableEquals(List.of(
                "[NODE1]",
                "[NODE2]",
                "[NODE3.1]",
                "[NODE3.2]",
                "[NODE3.3]",
                "[NODE3.4<myNewValue>]",
                "[NODE4<myNewValue>]",
                "[NODE5]"), output.get().state().messages() );
    }

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class     )
    public void testNestedCompiledSubgraphFormIssue216( CompiledGraph.StreamMode mode ) throws Exception {

        var subSubGraph = new StateGraph<>(MyState::new)
                .addNode("foo1", buildActionFactory("foo1"))
                .addNode("foo2", buildActionFactory("foo2"))
                .addNode("foo3", buildActionFactory("foo3"))
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile();

        var subGraph = new StateGraph<>(MyState::new)
                .addNode("bar1", buildActionFactory("bar1"))
                .addNode("subgraph2", subSubGraph)
                .addNode("bar2", buildActionFactory("bar2"))
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subgraph2")
                .addEdge("subgraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile();

        var stateGraph = new StateGraph<>(MyState::new)
                .addNode("main1", buildActionFactory("main1"))
                .addNode("subgraph1", subGraph)
                .addNode("main2", buildActionFactory("main2"))
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                .compile();

        var runnableConfig = RunnableConfig.builder()
                                .streamMode(mode)
                                .build();

        var input = GraphInput.args(Map.of());

        var graphIterator = stateGraph.stream(input, runnableConfig);

        var output = graphIterator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

    }

    public enum GraphCompileEnum {
        GRAPH_WITHOUT_ID( CompileConfig.builder().build() ),
        GRAPH_WITH_ID( CompileConfig.builder().graphId("graph01").build() );

        final CompileConfig config;

        GraphCompileEnum( CompileConfig config ) {
            this.config = config;
        }
    }

    @ParameterizedTest
    @EnumSource( GraphCompileEnum.class     )
    public  void compiledSubGraphTrackingTest( GraphCompileEnum graphCompile ) throws Exception {

        final var subGraphNodeId = "subgraph1";
        final var subSubGraphNodeId = "subgraph2" ;

        var subGraphBasePath = graphCompile.config.graphId()
                .map( graphId ->  GraphPath.of( graphId, subGraphNodeId ) )
                .orElseGet( () -> GraphPath.of( subGraphNodeId ) );
        var subSubGraphBasePath = subGraphBasePath.append( subSubGraphNodeId );

        var subSubGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addNode("foo1", actionBuilder().nodeId("foo1").path(subSubGraphBasePath).build())
                .addNode("foo2", actionBuilder().nodeId("foo2").path(subSubGraphBasePath).build())
                .addNode("foo3", actionBuilder().nodeId("foo3").path(subSubGraphBasePath).build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .graphId("subSubGraph")
                        .build());

        var subGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addNode("bar1", actionBuilder().nodeId("bar1").path(subGraphBasePath).build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().nodeId("bar2").path(subGraphBasePath).build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .graphId("subGraph")
                        .build());

        var stateGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addNode("main1", actionBuilder().nodeId("main1").build())
                .addNode(subGraphNodeId, subGraph)
                .addNode("main2",  actionBuilder().nodeId("main2").build())
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", subGraphNodeId)
                .addEdge(subGraphNodeId, "main2")
                .addEdge("main2", StateGraph.END)
                .compile( graphCompile.config );

        var runnableConfig = RunnableConfig.builder()
                .streamMode(CompiledGraph.StreamMode.VALUES)
                .build();

        var input = GraphInput.args(Map.of());

        var generator = stateGraph.stream(input, runnableConfig);

        var output = generator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );
        final var state = output.get().state();

        assertIterableEquals(List.of(
                        "[main1]",
                        "[bar1]",
                        "[foo1]",
                        "[foo2]",
                        "[foo3]",
                        "[bar2]",
                        "[main2]"), state.messages() );
    }

    @Test
    public  void compiledSubGraphHookTest() throws Exception {

        final var graphCompile = GraphCompileEnum.GRAPH_WITH_ID;

        final var subGraphNodeId = "subgraph1";
        final var subSubGraphNodeId = "subgraph2" ;

        var subGraphBasePath = graphCompile.config.graphId()
                .map( graphId ->  GraphPath.of( graphId, subGraphNodeId ) )
                .orElseGet( () -> GraphPath.of( subGraphNodeId ) );
        var subSubGraphBasePath = subGraphBasePath.append( subSubGraphNodeId );

        var subSubGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("foo1", actionBuilder().enableLog(false).nodeId("foo1").path(subSubGraphBasePath).build())
                .addNode("foo2", actionBuilder().enableLog(false).nodeId("foo2").path(subSubGraphBasePath).build())
                .addNode("foo3", actionBuilder().enableLog(false).nodeId("foo3").path(subSubGraphBasePath).build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .graphId("subSubGraph")
                        .build());

        var subGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("bar1", actionBuilder().enableLog(false).nodeId("bar1").path(subGraphBasePath).build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().enableLog(false).nodeId("bar2").path(subGraphBasePath).build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .graphId("subGraph")
                        .build());

        var stateGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("main1", actionBuilder().enableLog(false).nodeId("main1").build())
                .addNode(subGraphNodeId, subGraph)
                .addNode("main2",  actionBuilder().enableLog(false).nodeId("main2").build())
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", subGraphNodeId)
                .addEdge(subGraphNodeId, "main2")
                .addEdge("main2", StateGraph.END)
                .compile( graphCompile.config );

        var runnableConfig = RunnableConfig.builder()
                    .build();

        var input = GraphInput.args(Map.of());

        var generator = stateGraph.stream(input, runnableConfig);

        var output = generator.stream()
                //.peek( out -> log.info("output: {}", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );
        final var state = output.get().state();

        assertIterableEquals(List.of(
                "[main1]",
                "[bar1]",
                "[foo1]",
                "[foo2]",
                "[foo3]",
                "[bar2]",
                "[main2]"), state.messages() );
    }

    static class ResetLogsInSubgraphHook implements NodeHook.WrapCall<AgentState> {
        final Map<String, Channel<?>> schema;

        public ResetLogsInSubgraphHook(Map<String, Channel<?>> schema ) {
            this.schema = requireNonNull(schema);
        }

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, AgentState state, RunnableConfig config, AsyncNodeActionWithConfig<AgentState> action) {

            log.info("\nnode '{}' start with config: {} and state: {}", nodeId, config, state);

            return action.apply(state, config).thenApply( result -> {

                if( config.isResumeSubgraph() & state.<List<String>>value("logs").orElseGet( List::of ).isEmpty() ) {
                    return Map.of( "logs", AgentState.MARK_FOR_RESET );
                }

                return result;
            });
        }
    }

    public enum ResumeOptionEnum {
        UPDATE_STATE,
        GRAPH_RESUME;
    }


    @ParameterizedTest
    @EnumSource( ResumeOptionEnum.class     )
    public void issue326Test( ResumeOptionEnum resumeOption ) throws Exception {

        Map<String, Channel<?>> schema = Map.of(
                "logs", Channels.appender(ArrayList::new)
        );

        var saver = new MemorySaver();
        // Subgraph with two nodes
        var subGraph = new StateGraph<>(schema, AgentState::new)
            .addNode("nodeA", node_async((state,config) ->
                    Map.of("logs", List.of("Log from nodeA"))))
            .addNode("nodeB", node_async((state, config) ->
                    Map.of("logs", List.of("Log from nodeB"))))
            .addEdge("nodeA", "nodeB")
            .addEdge(START, "nodeA")
            .addEdge("nodeB", END)
            .compile( CompileConfig.builder()
                    .interruptAfter("nodeA")
                    .checkpointSaver(saver)
                    .build() );

        // Parent graph with subgraph as node
        var parentGraph = new StateGraph<>(schema, AgentState::new)
                                    .addNode("subgraph", subGraph)
                                    .addEdge( START, "subgraph")
                                    .addEdge("subgraph", END)
                                    .compile(CompileConfig.builder()
                                            .checkpointSaver(saver)
                                            .build());

        // Execute and interrupt after nodeA
        var config = RunnableConfig.builder()
                                    .threadId("test-thread")
                                    .build();
        parentGraph.invoke( GraphInput.noArgs(), config);

        // Clear logs field
        final Map<String, Object> updates = Map.of("logs", AgentState.MARK_FOR_RESET); // MARK_FOR_RESET or ReplaceAllWith.of(List.of())

        var result = Optional.<AgentState>empty();

        if( resumeOption == ResumeOptionEnum.GRAPH_RESUME ) {
            result = parentGraph.invoke(GraphInput.resume(updates), config);
        }
        else {
            var newConfig = parentGraph.updateState(config, updates);
            result = parentGraph.invoke(GraphInput.resume(), newConfig);
        }

        assertTrue( result.isPresent() );
        var logs = result.get().<List<String>>value("logs");
        assertTrue(logs.isPresent());
        assertFalse(logs.get().isEmpty());
        assertEquals( 1, logs.get().size());
        assertEquals("Log from nodeB", logs.get().get(0));

    }
}