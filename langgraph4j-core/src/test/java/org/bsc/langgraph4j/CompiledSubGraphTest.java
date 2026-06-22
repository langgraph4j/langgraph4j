package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.FileSystemSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.exception.SubGraphInterruptionException;
import org.bsc.langgraph4j.hook.LogNodeHook;
import org.bsc.langgraph4j.hook.WrapCallHookSubgraphAware;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
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

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

public class CompiledSubGraphTest implements LG4JLoggable {

    static class MyState extends MessagesState<String> {

        public MyState(Map<String, Object> initData) {
            super(initData);
        }

        boolean resumeSubgraph() {
            return this.<Boolean>value("resume_subgraph")
                    .orElse(false);
        }
    }

    static final  StateSerializer<MyState> jsonStateSerializer = new JacksonStateSerializer<>(MyState::new) {};
    static final  StateSerializer<MyState> binStateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

    public enum InterruptionTypeEnum {
        DECLARED_INTERRUPTION_WITH_VALUES_JSON( jsonStateSerializer, CompiledGraph.StreamMode.VALUES ),
        DECLARED_INTERRUPTION_WITH_VALUES_BIN( binStateSerializer, CompiledGraph.StreamMode.VALUES ),
        INTERRUPTABLE_ACTION_WITH_VALUES_JSON( jsonStateSerializer, CompiledGraph.StreamMode.VALUES  ),
        INTERRUPTABLE_ACTION_WITH_VALUES_BIN( binStateSerializer, CompiledGraph.StreamMode.VALUES  ),
        DECLARED_INTERRUPTION_WITH_SNAPSHOT_JSON( jsonStateSerializer, CompiledGraph.StreamMode.SNAPSHOTS ),
        DECLARED_INTERRUPTION_WITH_SNAPSHOT_BIN( binStateSerializer, CompiledGraph.StreamMode.SNAPSHOTS ),
        INTERRUPTABLE_ACTION_WITH_SNAPSHOT_JSON( jsonStateSerializer, CompiledGraph.StreamMode.SNAPSHOTS  ),
        INTERRUPTABLE_ACTION_WITH_SNAPSHOT_BIN( binStateSerializer, CompiledGraph.StreamMode.SNAPSHOTS  )
        ;

        final StateSerializer<MyState> stateSerializer;
        final CompiledGraph.StreamMode streamMode;

        InterruptionTypeEnum( StateSerializer<MyState> stateSerializer,
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

    static class WrapCallHook extends WrapCallHookSubgraphAware<MyState> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                                MyState state,
                                                                RunnableConfig config,
                                                                AsyncNodeActionWithConfig<MyState> action) {

            isSubgraphEnded( config ).ifPresent(
                    item -> System.out.printf("[%s] ended%n", item));

            System.out.printf("[%s] start%n", nodeId);

            return action.apply( state, config ).whenComplete( (result, ex ) -> {

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
                    if( enableLog ) log.info("nodePath: {}", config.nodePath());
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

        static abstract class InterruptableNodeAction implements AsyncNodeActionWithConfig<MyState>, InterruptableAction<MyState> {

            @Override
            public Optional<InterruptionMetadata<MyState>> interrupt(String nodeId, MyState state, RunnableConfig config) {
                if( state.<Boolean>value("interrupt_subgraph").orElse(false) ) {
                    return Optional.of( InterruptionMetadata.builder( nodeId, state).build() );
                }
                return Optional.empty();
            }
        }

        public Node.ActionFactory<MyState> buildInterruptable() {
            assertNotNull( nodeId );
            return ( CompileConfig compileConfig ) ->

                new InterruptableNodeAction()  {

                    @Override
                    public CompletableFuture<Map<String,Object>> apply( MyState state, RunnableConfig config) {

                        assertEquals(nodeId, config.nodeId());

                        if( basePath != null ) {
                            if( enableLog ) log.info("nodePath: {}", config.nodePath());
                            assertEquals( basePath, config.nodePath().root() );
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

                }};

        }

        public Node.ActionFactory<MyState> build( boolean asInterruptable ) {
            if( asInterruptable ) {
                return buildInterruptable();
            }
            return build();
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

    private CompiledGraph<MyState> subGraphWithInterruption( BaseCheckpointSaver saver, StateSerializer<MyState> stateSerializer, boolean asInterruptable) throws Exception {

        final var compileConfigBuilder = CompileConfig.builder()
                .checkpointSaver(saver)
                ;

        if( !asInterruptable ) {
            compileConfigBuilder.interruptAfter("NODE3.2");
        }

        final var compileConfig = compileConfigBuilder.build();

        return new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", actionBuilder().nodeId("NODE3.1").build())
                .addNode("NODE3.2", actionBuilder().nodeId("NODE3.2").build())
                .addNode("NODE3.3", actionBuilder().nodeId("NODE3.3").build(asInterruptable))
                .addNode("NODE3.4", actionBuilder().nodeId("NODE3.4").attributeKey("newAttribute").build())
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    private CompiledGraph<MyState> subGraphWithException( BaseCheckpointSaver saver, StateSerializer<MyState> stateSerializer) throws Exception {

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final Node.ActionFactory<MyState> nodeWithExceptionFactory = ( $1 ) ->
                (state, config) -> {
                    if( config.isResumeSubgraph() ) {
                        return completedFuture(Map.of("messages", "[%s]".formatted( config.nodeId() )));
                    }
                    return failedFuture(new SubGraphInterruptionException(config,
                            config.nodePath().rootElement().orElseThrow(),
                            config.nodePath().lastElement().orElseThrow(),
                            state.data()));
                };

        return new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", actionBuilder().nodeId("NODE3.1").build())
                .addNode("NODE3.2", nodeWithExceptionFactory)
                .addNode("NODE3.3", actionBuilder().nodeId("NODE3.3").build())
                .addNode("NODE3.4", actionBuilder().nodeId("NODE3.4").attributeKey("newAttribute").build())
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    @Test
    public void testCompiledSubGraphSimple() throws Exception {

        AsyncNodeActionWithConfig<MyState> childStep1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step1"));

        AsyncNodeActionWithConfig<MyState> childStep2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step2"));

        AsyncNodeActionWithConfig<MyState> childStep3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "child:step3"));

        var workflowChild = new StateGraph<>(MyState.SCHEMA, MyState::new)
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
        AsyncNodeActionWithConfig<MyState> step1 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step1"));

        AsyncNodeActionWithConfig<MyState> step2 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step2"));

        AsyncNodeActionWithConfig<MyState> step3 =
                AsyncNodeActionWithConfig.node_async((state, config) ->
                        Map.of("messages", "step3"));

        var workflowParent = new StateGraph<>(MyState.SCHEMA, MyState::new)
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

        final var saver = new FileSystemSaver( Path.of("target", "testCompiledSubGraphInterruptionUsingException"), jsonStateSerializer );

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        final var subGraph = subGraphWithException(
                saver,
                jsonStateSerializer); // create subgraph

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, jsonStateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", buildActionFactory("NODE1"))
                .addNode("NODE2", buildActionFactory("NODE2"))
                //.addNode("NODE3", buildSubgraphAction("NODE3", subGraph))
                .addNode("NODE3", subGraph )
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
                parentGraph.stream(input, runnableConfig)
                        .reduce( (a,b) -> b )
                        .thenAccept(output -> {
                            assertTrue(output.result().isEND());
                            assertIterableEquals(List.of(
                                    "[NODE1]",
                                    "[NODE2]",
                                    "[NODE3.1]",
                                    "[NODE3.2]",
                                    "[NODE3.3]",
                                    "[NODE3.4<myNewValue>]",
                                    "[NODE4]",
                                    "[NODE5<myNewValue>]"), output.result().state().messages());

                        })
                        .join();
                break;
            }
            catch( Exception ex ) {
                Optional<SubGraphInterruptionException> interruptException = SubGraphInterruptionException.of(ex);
                if( interruptException.isPresent() ) {

                    log.info("SubGraphInterruptionException: {}", interruptException.get().getMessage());
                    var interruptionState = new MyState(interruptException.get().state());

                    assertIterableEquals(List.of(
                            "[NODE1]",
                            "[NODE2]",
                            "[NODE3.1]"), interruptionState.messages());

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
                    runnableConfig = parentGraph.updateState( runnableConfig, interruptionState.data(), nodeBeforeSubgraph );
                    input = GraphInput.resume();

                    log.info( "RESUME GRAPH FROM END OF NODE: {}", nodeBeforeSubgraph);
                    continue;
                }

                saver.release( runnableConfig );
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

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, mode.stateSerializer)
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


        var input = (asInterruptable) ?
                            GraphInput.args(Map.of("interrupt_subgraph", true)) :
                            GraphInput.noArgs();

        try {
            parentGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept(output -> {
                        assertFalse(output.result().isEND());
                        assertInstanceOf(SubGraphOutput.class, output.result());

                        assertIterableEquals(List.of(
                                "[NODE1]",
                                "[NODE2]",
                                "[NODE3.1]",
                                "[NODE3.2]"), output.result().state().messages());

                        var iteratorResult = GraphResult.from(output.resultValue());

                        assertFalse(iteratorResult.isEmpty());
                        assertTrue(iteratorResult.isInterruptionMetadata());

                    })
                    .join();


            input = (asInterruptable) ?
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>", "interrupt_subgraph", false)) :
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>"));

            parentGraph.stream(input, runnableConfig)
                    .reduce((a, b) -> b)
                    .thenAccept(output -> {
                        assertTrue(output.result().isEND());
                        assertIterableEquals(List.of(
                                "[NODE1]",
                                "[NODE2]",
                                "[NODE3.1]",
                                "[NODE3.2]",
                                "[NODE3.3]",
                                "[NODE3.4<myNewValue>]",
                                "[NODE4]",
                                "[NODE5<myNewValue>]"), output.result().state().messages());

                    })
                    .join();
        }
        finally {
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

        var parentGraph = new StateGraph<>(MyState.SCHEMA, mode.stateSerializer)
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

        var input = (asInterruptable) ?
                GraphInput.args(Map.of("interrupt_subgraph", true)) :
                GraphInput.noArgs();

        try {
            var graphIterator = parentGraph.stream(input, runnableConfig);

            var output = graphIterator.stream()
                    //.peek( out -> log.info("output: {}", out) )
                    .reduce((a, b) -> b);

            assertTrue(output.isPresent());

            assertFalse(output.get().isEND());
            assertInstanceOf(SubGraphOutput.class, output.get());

            assertIterableEquals(List.of(
                    "[NODE1]",
                    "[NODE2]",
                    "[NODE3.1]",
                    "[NODE3.2]"), output.get().state().messages());

            var iteratorResult = GraphResult.from(graphIterator);

            assertFalse(iteratorResult.isEmpty());
            assertTrue(iteratorResult.isInterruptionMetadata());

            input = (asInterruptable) ?
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>", "interrupt_subgraph", false)) :
                    GraphInput.resume(Map.of("newAttribute", "<myNewValue>"));

            graphIterator = parentGraph.stream(input, runnableConfig);

            output = graphIterator.stream()
                    //.peek( out -> log.info("output: {}}", out) )
                    .reduce((a, b) -> b);

            assertTrue(output.isPresent());
            assertTrue(output.get().isEND());

            assertIterableEquals(List.of(
                    "[NODE1]",
                    "[NODE2]",
                    "[NODE3.1]",
                    "[NODE3.2]",
                    "[NODE3.3]",
                    "[NODE3.4<myNewValue>]",
                    "[NODE4<myNewValue>]",
                    "[NODE5]"), output.get().state().messages());

        } finally {
            parentSaver.release(runnableConfig);
        }
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

        var parentGraph = new StateGraph<>(MyState::new)
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
                jsonStateSerializer);

        var subSubGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addNode("foo1", actionBuilder().nodeId("foo1").build())
                .addNode("foo2", actionBuilder().nodeId("foo2").build())
                .addNode("foo3", actionBuilder().nodeId("foo3").build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .graphId("subSubGraph")
                        .build());

        var subGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addNode("bar1", actionBuilder().nodeId("bar1").build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().nodeId("bar2").build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
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
                    "[main1]",
                    "[bar1]",
                    "[foo1]",
                    "[foo2]",
                    "[foo3]",
                    "[bar2]",
                    "[main2]"), state.messages());
        }
        finally {
            saver.release(runnableConfig);
        }
    }

    @Test
    public  void testCompiledSubGraphHookTest() throws Exception {

        final var saver = new FileSystemSaver(
                Paths.get("target", "testCompiledSubGraphHookTest"),
                jsonStateSerializer);

        final var graphCompile = GraphCompileEnum.GRAPH_WITH_ID;

        final var subGraphNodeId = "subgraph1";
        final var subSubGraphNodeId = "subgraph2" ;

        var subSubGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("foo1", actionBuilder().enableLog(false).nodeId("foo1").build())
                .addNode("foo2", actionBuilder().enableLog(false).nodeId("foo2").build())
                .addNode("foo3", actionBuilder().enableLog(false).nodeId("foo3").build())
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        var subGraph = new StateGraph<>(MyState.SCHEMA, MyState::new)
                .addWrapCallNodeHook( new WrapCallHook() )
                .addNode("bar1", actionBuilder().enableLog(false).nodeId("bar1").build())
                .addNode(subSubGraphNodeId, subSubGraph)
                .addNode("bar2", actionBuilder().enableLog(false).nodeId("bar2").build())
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", subSubGraphNodeId)
                .addEdge(subSubGraphNodeId, "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile( CompileConfig.builder()
                        .checkpointSaver(saver)
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
                                "[main1]",
                                "[bar1]",
                                "[foo1]",
                                "[foo2]",
                                "[foo3]",
                                "[bar2]",
                                "[main2]"), state.messages());

                    })
                    .join();
        }
        finally {
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
                jsonStateSerializer);

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
        finally {
            saver.release(config);
        }

    }
}