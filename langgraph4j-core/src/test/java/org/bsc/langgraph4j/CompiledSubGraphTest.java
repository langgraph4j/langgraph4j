package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.LogNodeHook;
import org.bsc.langgraph4j.hook.WrapCallHookSubgraphAware;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.subgraph.SubGraphOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

public class CompiledSubGraphTest implements LG4JTestUtil {

    public enum InterruptionTypeEnum {
        DECLARED_INTERRUPTION_WITH_VALUES_JSON( StateSerializerEnum.JSON.stateSerializer, CompiledGraph.StreamMode.VALUES ),
        DECLARED_INTERRUPTION_WITH_VALUES_BIN( StateSerializerEnum.BINARY.stateSerializer, CompiledGraph.StreamMode.VALUES ),
        INTERRUPTABLE_ACTION_WITH_VALUES_JSON( StateSerializerEnum.JSON.stateSerializer, CompiledGraph.StreamMode.VALUES  ),
        INTERRUPTABLE_ACTION_WITH_VALUES_BIN( StateSerializerEnum.BINARY.stateSerializer, CompiledGraph.StreamMode.VALUES  ),
        DECLARED_INTERRUPTION_WITH_SNAPSHOT_JSON( StateSerializerEnum.JSON.stateSerializer, CompiledGraph.StreamMode.SNAPSHOTS ),
        DECLARED_INTERRUPTION_WITH_SNAPSHOT_BIN( StateSerializerEnum.BINARY.stateSerializer, CompiledGraph.StreamMode.SNAPSHOTS ),
        INTERRUPTABLE_ACTION_WITH_SNAPSHOT_JSON( StateSerializerEnum.JSON.stateSerializer, CompiledGraph.StreamMode.SNAPSHOTS  ),
        INTERRUPTABLE_ACTION_WITH_SNAPSHOT_BIN( StateSerializerEnum.BINARY.stateSerializer, CompiledGraph.StreamMode.SNAPSHOTS  )
        ;

        final StateSerializer<State> stateSerializer;
        final CompiledGraph.StreamMode streamMode;

        InterruptionTypeEnum( StateSerializer<State> stateSerializer,
                              CompiledGraph.StreamMode streamMode) {
            this.stateSerializer = stateSerializer;
            this.streamMode = streamMode;
        }
    }

    public enum GraphCompileEnum {
        GRAPH_WITHOUT_ID( CompileConfig.builder().build() ),
        GRAPH_WITH_ID( CompileConfig.builder().graphId("graph01").build() );

        final CompileConfig config;

        GraphCompileEnum( CompileConfig config ) {
            this.config = config;
        }
    }

    public enum ResumeOptionEnum {
        UPDATE_STATE,
        GRAPH_RESUME;
    }

    static class WrapCallHook extends WrapCallHookSubgraphAware<State> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                                State state,
                                                                RunnableConfig config,
                                                                AsyncNodeActionWithConfig<State> action) {

            isSubgraphEnded( config ).ifPresent(
                    item -> log.info("{} ended", item));

            log.info("{} start", nodeId);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

                if( ex != null ) {
                    return;
                }

                isSubgraphRequested( nodeId, config, result ).ifPresentOrElse(
                        item -> log.info("subgraph requested: [{}]", item),
                        () -> log.info("{} end", nodeId));
            });
        }
    }


    private CustomNodeAction.Builder actionBuilder() {
        return CustomNodeAction.builder();
    }

    private Node.ActionFactory<State> buildActionFactory(String nodeId) {
        return actionBuilder().message( nodeId ).buildAsFactory();
    }

    private Node.ActionFactory<State> buildActionFactory(String nodeId, String attributeKey) {
        return actionBuilder().message( nodeId ).attributeKey( attributeKey ).buildAsFactory();
    }

    private CompiledGraph<State> subGraphWithInterruption( BaseCheckpointSaver saver, StateSerializer<State> stateSerializer, boolean asInterruptable) throws Exception {

        final var compileConfigBuilder = CompileConfig.builder()
                .checkpointSaver(saver)
                ;

        if( !asInterruptable ) {
            compileConfigBuilder.interruptAfter("NODE3.2");
        }

        final var compileConfig = compileConfigBuilder.build();

        return new StateGraph<>(State.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", actionBuilder().message("NODE3.1").build())
                .addNode("NODE3.2", actionBuilder().message("NODE3.2").build())
                .addNode("NODE3.3", actionBuilder().message("NODE3.3").interruptable(asInterruptable).build())
                .addNode("NODE3.4", actionBuilder().message("NODE3.4").attributeKey("newAttribute").build())
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    private CompiledGraph<State> subGraphWithException( BaseCheckpointSaver saver, StateSerializer<State> stateSerializer) throws Exception {

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final Node.ActionFactory<State> nodeWithExceptionFactory = ( $1 ) ->
                (state, config) -> {
                    if( config.isResumeSubgraph() ) {
                        return completedFuture(Map.of("messages", "%s".formatted( config.nodeId() )));
                    }
                    return failedFuture( new GraphInterruptException( config,
                            "interruption in subgraph: %s on node: %s".formatted(
                                    config.nodePath().rootElement().orElseThrow(),
                                    config.nodePath().lastElement().orElseThrow()) ));


                };

        return new StateGraph<>(State.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", actionBuilder().message("NODE3.1").build())
                .addNode("NODE3.2", nodeWithExceptionFactory)
                .addNode("NODE3.3", actionBuilder().message("NODE3.3").build())
                .addNode("NODE3.4", actionBuilder().message("NODE3.4").attributeKey("newAttribute").build())
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    @Test
    public void testCompiledSubGraphSimple() throws Exception {

        AsyncNodeActionWithConfig<State> childStep1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step1"));

        AsyncNodeActionWithConfig<State> childStep2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step2"));

        AsyncNodeActionWithConfig<State> childStep3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step3"));

        var workflowChild = new StateGraph<>(State.SCHEMA, State::new)
                .addBeforeCallNodeHook(LogNodeHook.applyBeforeHook() )
                .addAfterCallNodeHook(LogNodeHook.applyAfterHook() )
                .addNode("child:step_1", childStep1)
                .addNode("child:step_2", childStep2)
                .addNode("child:step_3", childStep3)
                .addEdge(START, "child:step_1")
                .addEdge("child:step_1", "child:step_2")
                .addEdge("child:step_2", "child:step_3")
                .addEdge("child:step_3", END)
                .compile()
                ;
        AsyncNodeActionWithConfig<State> step1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step1"));

        AsyncNodeActionWithConfig<State> step2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step2"));

        AsyncNodeActionWithConfig<State> step3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step3"));

        var workflowParent = new StateGraph<>(State.SCHEMA, State::new)
                .addBeforeCallNodeHook(LogNodeHook.applyBeforeHook() )
                .addAfterCallNodeHook(LogNodeHook.applyAfterHook() )
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

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class )
    void testCompiledSubGraphInterruptionUsingException( CompiledGraph.StreamMode mode ) throws Exception {

        final var saver = new FileSystemSaver( Path.of("target", "testCompiledSubGraphInterruptionUsingException"), StateSerializerEnum.JSON.stateSerializer );

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final var subGraph = subGraphWithException(
                saver,
                StateSerializerEnum.JSON.stateSerializer); // create subgraph

        var parentGraph =  new StateGraph<>(State.SCHEMA, StateSerializerEnum.JSON.stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", buildActionFactory("NODE1"))
                .addNode("NODE2", buildActionFactory("NODE2"))
                //.addNode("NODE3", buildSubgraphAction("NODE3", subGraph))
                .addNode("NODE3", subGraph )
                .addNode("NODE4", buildActionFactory("NODE4"))
                .addNode("NODE5", actionBuilder().message("NODE5").attributeKey("newAttribute").build())
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
                GraphResult result = parentGraph.stream(input, runnableConfig)
                        .reduce((a, b) -> b)
                        .thenApply(output ->
                                GraphResult.from(output.resultValue())
                        )
                        .join();

                if (result.isInterruptionMetadata()) {
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

                    final var interruptionMetadata = result.<State>asInterruptionMetadata();
                    assertIterableEquals(List.of(
                            "NODE1",
                            "NODE2",
                            "NODE3.1"), interruptionMetadata.state().messages());
                    var nodeBeforeSubgraph = "NODE2";
                    runnableConfig = parentGraph.updateState(runnableConfig,
                            interruptionMetadata.state().data(),
                            nodeBeforeSubgraph);

                    input = GraphInput.resume( Map.of("newAttribute", "<myNewValue>") );

                    log.info("RESUME GRAPH FROM END OF NODE: {}", nodeBeforeSubgraph);

                    continue;
                }
                if (result.isStateDataOrCheckpointSaverTag()) {

                    final var stateData = new State(result.asStateDataOrLastCheckpointStateData());

                    assertIterableEquals(List.of(
                            "NODE1",
                            "NODE2",
                            "NODE3.1",
                            "NODE3.2",
                            "NODE3.3",
                            "NODE3.4<myNewValue>",
                            "NODE4",
                            "NODE5<myNewValue>"), stateData.messages());

                    break;
                }

                fail("expected GraphInterruptException or StateDataOrCheckpointSaverTag, but got: %s ".formatted(result));
            }
            catch( Throwable ex ) {
                saver.release( runnableConfig );
                fail(ex);
                break;
            }
        } while( true );

    }

    @ParameterizedTest
    @EnumSource( value = InterruptionTypeEnum.class )
    public void testCompiledSubGraphInterruptionSharingSaver( InterruptionTypeEnum mode ) throws Exception {

        final var asInterruptable = switch( mode ) {
            case INTERRUPTABLE_ACTION_WITH_SNAPSHOT_JSON,
                 INTERRUPTABLE_ACTION_WITH_SNAPSHOT_BIN,
                 INTERRUPTABLE_ACTION_WITH_VALUES_JSON,
                 INTERRUPTABLE_ACTION_WITH_VALUES_BIN -> true;
            case DECLARED_INTERRUPTION_WITH_VALUES_JSON,
                 DECLARED_INTERRUPTION_WITH_VALUES_BIN,
                 DECLARED_INTERRUPTION_WITH_SNAPSHOT_JSON,
                 DECLARED_INTERRUPTION_WITH_SNAPSHOT_BIN -> false;
        };

        final var saver = new FileSystemSaver(
                Paths.get( "target", "testCompiledSubGraphInterruptionSharingSaver") ,
                mode.stateSerializer);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var runnableConfig = RunnableConfig.builder()
                .threadId("thread01")
                .streamMode(mode.streamMode)
                .build();

        var subGraph = subGraphWithInterruption(
                saver,
                mode.stateSerializer,
                asInterruptable); // create subgraph

        var parentGraph =  new StateGraph<>(State.SCHEMA, mode.stateSerializer)
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


        var input = GraphInput.noArgs();

        try {
            parentGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept(output -> {
                        assertFalse(output.result().isEND());
                        assertInstanceOf(SubGraphOutput.class, output.result());

                        assertIterableEquals(List.of(
                                "NODE1",
                                "NODE2",
                                "NODE3.1",
                                "NODE3.2"), output.result().state().messages());

                        var iteratorResult = GraphResult.from(output.resultValue());

                        assertFalse(iteratorResult.isEmpty());
                        assertTrue(iteratorResult.isInterruptionMetadata());

                    })
                    .join();


            input = (asInterruptable) ?
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>", State.RESUME, true)) :
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>"));

            parentGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept(output -> {
                        assertTrue(output.result().isEND());
                        assertIterableEquals(List.of(
                                "NODE1",
                                "NODE2",
                                "NODE3.1",
                                "NODE3.2",
                                "NODE3.3",
                                "NODE3.4<myNewValue>",
                                "NODE4",
                                "NODE5<myNewValue>"), output.result().state().messages());

                    })
                    .join();
        }
        catch( Exception e ) {
            log.error("testCompiledSubGraphInterruptionSharingSaver", e);
            saver.release(runnableConfig);
        }
    }

    @ParameterizedTest
    @EnumSource( InterruptionTypeEnum.class     )
    public void testCompiledSubGraphInterruptionWithDifferentSaver( InterruptionTypeEnum mode ) throws Exception {

        final var asInterruptable = switch (mode) {
            case INTERRUPTABLE_ACTION_WITH_SNAPSHOT_JSON,
                 INTERRUPTABLE_ACTION_WITH_SNAPSHOT_BIN,
                 INTERRUPTABLE_ACTION_WITH_VALUES_JSON,
                 INTERRUPTABLE_ACTION_WITH_VALUES_BIN -> true;
            case DECLARED_INTERRUPTION_WITH_VALUES_JSON,
                 DECLARED_INTERRUPTION_WITH_VALUES_BIN,
                 DECLARED_INTERRUPTION_WITH_SNAPSHOT_JSON,
                 DECLARED_INTERRUPTION_WITH_SNAPSHOT_BIN -> false;
        };

        final var parentSaver = new FileSystemSaver(
                Paths.get("target", "testCompiledSubGraphInterruptionWithDifferentSaver"),
                mode.stateSerializer);

        final var childSaver = new MemorySaver();

        var subGraph = subGraphWithInterruption(
                childSaver,
                mode.stateSerializer,
                asInterruptable); // create subgraph

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(parentSaver)
                .build();

        var parentGraph = new StateGraph<>(State.SCHEMA, mode.stateSerializer)
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
                .streamMode(mode.streamMode)
                .build();

        var input = GraphInput.noArgs();

        try {
            var graphIterator = parentGraph.stream(input, runnableConfig);

            var output = graphIterator.stream()
                    //.peek( out -> log.info("output: {}", out) )
                    .reduce((a, b) -> b);

            assertTrue(output.isPresent());

            assertFalse(output.get().isEND());
            assertInstanceOf(SubGraphOutput.class, output.get());

            assertIterableEquals(List.of(
                    "NODE1",
                    "NODE2",
                    "NODE3.1",
                    "NODE3.2"), output.get().state().messages());

            var iteratorResult = GraphResult.from(graphIterator);

            assertFalse(iteratorResult.isEmpty());
            assertTrue(iteratorResult.isInterruptionMetadata());

            input = GraphInput.resume(Map.of("newAttribute", "<myNewValue>"));

            parentGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept( reduceResult -> {
                        assertTrue(reduceResult.result().isEND());
                        assertIterableEquals(List.of(
                                "NODE1",
                                "NODE2",
                                "NODE3.1",
                                "NODE3.2",
                                "NODE3.3",
                                "NODE3.4<myNewValue>",
                                "NODE4<myNewValue>",
                                "NODE5"), reduceResult.result().state().messages());

                    })
                    .join();

        } catch( Exception e ) {
            log.error("testCompiledSubGraphInterruptionWithDifferentSaver", e);
            parentSaver.release(runnableConfig);
        }
    }

    @ParameterizedTest
    @EnumSource( CompiledGraph.StreamMode.class     )
    public void testNestedCompiledSubgraphFormIssue216( CompiledGraph.StreamMode mode ) throws Exception {

        var subSubGraph = new StateGraph<>(State::new)
                .addNode("foo1", buildActionFactory("foo1"))
                .addNode("foo2", buildActionFactory("foo2"))
                .addNode("foo3", buildActionFactory("foo3"))
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile();

        var subGraph = new StateGraph<>(State::new)
                .addNode("bar1", buildActionFactory("bar1"))
                .addNode("subgraph2", subSubGraph)
                .addNode("bar2", buildActionFactory("bar2"))
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subgraph2")
                .addEdge("subgraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile();

        var parentGraph = new StateGraph<>(State::new)
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

        var output = parentGraph.stream(input, runnableConfig)
                .reduce((a, b) -> b)
                .join();


    }

    @ParameterizedTest
    @EnumSource( GraphCompileEnum.class     )
    public void testCompiledSubGraphTracking( GraphCompileEnum graphCompile ) throws Exception {

        final var subGraphNodeId = "subgraph1";
        final var subSubGraphNodeId = "subgraph2" ;

        final var saver = new FileSystemSaver(
                Paths.get("target", "testCompiledSubGraphTracking"),
                StateSerializerEnum.JSON.stateSerializer);

        var subSubGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("foo1", actionBuilder().message("foo1").build())
                .addNode("foo2", actionBuilder().message("foo2").build())
                .addNode("foo3", actionBuilder().message("foo3").build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .graphId("subSubGraph")
                        .build());

        var subGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("bar1", actionBuilder().message("bar1").build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().message("bar2").build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .graphId("subGraph")
                        .build());

        var stateGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("main1", actionBuilder().message("main1").build())
                .addNode(subGraphNodeId, subGraph)
                .addNode("main2",  actionBuilder().message("main2").build())
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", subGraphNodeId)
                .addEdge(subGraphNodeId, "main2")
                .addEdge("main2", StateGraph.END)
                .compile( CompileConfig.builder(graphCompile.config)
                            .checkpointSaver(saver)
                            .build());

        var runnableConfig = RunnableConfig.builder()
                .streamMode(CompiledGraph.StreamMode.VALUES)
                .build();

        var input = GraphInput.noArgs();

        try {
            var output = stateGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .join();


            assertNotNull(output.result());
            assertTrue(output.result().isEND());
            final var state = output.result().state();

            assertIterableEquals(List.of(
                    "main1",
                    "bar1",
                    "foo1",
                    "foo2",
                    "foo3",
                    "bar2",
                    "main2"), state.messages());
        } catch (Exception e) {
            log.error("testCompiledSubGraphTracking", e);
            saver.release(runnableConfig);
        }
    }

    @Test
    public  void testCompiledSubGraphHookTest() throws Exception {

        final var saver = new FileSystemSaver(
                Paths.get("target", "testCompiledSubGraphHookTest"),
                StateSerializerEnum.JSON.stateSerializer);

        final var graphCompile = GraphCompileEnum.GRAPH_WITH_ID;

        final var subGraphNodeId = "subgraph1";
        final var subSubGraphNodeId = "subgraph2" ;

        var subSubGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("foo1", actionBuilder().enableLog(false).message("foo1").build())
                .addNode("foo2", actionBuilder().enableLog(false).message("foo2").build())
                .addNode("foo3", actionBuilder().enableLog(false).message("foo3").build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        var subGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("bar1", actionBuilder().enableLog(false).message("bar1").build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().enableLog(false).message("bar2").build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        var stateGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("main1", actionBuilder().enableLog(false).message("main1").build())
                .addNode(subGraphNodeId, subGraph)
                .addNode("main2",  actionBuilder().enableLog(false).message("main2").build())
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", subGraphNodeId)
                .addEdge(subGraphNodeId, "main2")
                .addEdge("main2", StateGraph.END)
                .compile( CompileConfig.builder(graphCompile.config)
                            .checkpointSaver(saver)
                            .build());

        var runnableConfig = RunnableConfig.builder().build();

        var input = GraphInput.noArgs();

        try {
            stateGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept(output -> {
                        assertNotNull(output.result());
                        assertTrue(output.result().isEND());
                        final var state = output.result().state();

                        assertIterableEquals(List.of(
                                "main1",
                                "bar1",
                                "foo1",
                                "foo2",
                                "foo3",
                                "bar2",
                                "main2"), state.messages());

                    })
                    .join();
        }
        catch (Exception e) {
            log.error("testCompiledSubGraphHookTest", e);
            saver.release(runnableConfig);
        }

    }

    /**
     * Test for issue <a href="https://github.com/langchain4j/langgraph4j/issues/326">#326</a>:
     * Check that when a subgraph is resumed after an interruption, the state updates are correctly applied and the subgraph execution continues as expected.
     *
     */
    @ParameterizedTest
    @EnumSource( ResumeOptionEnum.class )
    public void testIssue326(ResumeOptionEnum resumeOption ) throws Exception {

        final Map<String, Channel<?>> schema = Map.of(
                "logs", Channels.appender(ArrayList::new)
        );

        final var saver = new FileSystemSaver(
                Paths.get("target", "testIssue326"),
                StateSerializerEnum.JSON.stateSerializer);

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

        try {
            parentGraph.stream(GraphInput.noArgs(), config)
                    .toCompletableFuture()
                    .join();

            // Clear logs field
            final Map<String, Object> updates = Map.of("logs", AgentState.MARK_FOR_RESET); // MARK_FOR_RESET or ReplaceAllWith.of(List.of())

            var result = Optional.<AgentState>empty();

            if (resumeOption == ResumeOptionEnum.GRAPH_RESUME) {
                result = parentGraph.invoke(GraphInput.resume(updates), config);
            } else {
                var newConfig = parentGraph.updateState(config, updates);
                result = parentGraph.invoke(GraphInput.resume(), newConfig);
            }

            assertTrue(result.isPresent());
            var logs = result.get().<List<String>>value("logs");
            assertTrue(logs.isPresent());
            assertFalse(logs.get().isEmpty());
            assertEquals(1, logs.get().size());
            assertEquals("Log from nodeB", logs.get().get(0));
        }
        catch( Exception e ) {
            log.error("testIssue326", e);
            saver.release(config);
        }

    }
}