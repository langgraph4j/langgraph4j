package org.bsc.langgraph4j.annotation;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.RemoveByHash;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for simple App.
 */
public class LG4JAnnotationTest implements LG4JLoggable {

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        int steps() {
            return this.<Integer>value("steps").orElse(0);
        }

    }

    public static <T> List<Map.Entry<String, T>> sortMap(Map<String, T> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
    }



    @Test
    public void testRunningOneNode() throws Exception {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentAction( AgentState state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);

                log.info( "{}", currentNode );
                return Map.of("prop1", "test");
            }

        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var graph = new StateGraph<>(AgentState::new)
                .add( repo.node("agent_1") )
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END)
                .compile();

        final var result = graph.invoke(
                        GraphInput.args(Map.of("input", "test1")),
                        RunnableConfig.empty());
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1", "prop1", "test");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));

    }

    @Test
    public void testRunnableConfigMetadata() throws Exception {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentConfigMetadata( AgentState state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);
                assertTrue(config.metadata("configData").isPresent());

                log.info( "{}", currentNode );
                return Map.of("prop1", "test");
            }
        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        var graph = new StateGraph<>(AgentState::new)
                .add(repo.node("agent_1"))
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END)
                .compile();

        var config = RunnableConfig.builder()
                .addMetadata("configData", "test")
                .build();

        var result = graph.invoke(GraphInput.args(Map.of("input", "test1")), config);
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1", "prop1", "test");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));

    }

    @Test
    public void testRunningOneNodeAndMarkForRemoval() throws Exception {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentMarkForRemoval( AgentState state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);

                log.info( "{}", currentNode );
                return Map.of("prop1", MARK_FOR_REMOVAL);
            }
        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var schema = Map.<String, Channel<?>>of("prop1", Channels.base(null, null));

        final var graph = new StateGraph<>(schema, AgentState::new)
                .add( repo.node("agent_1") )
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END)
                .compile();

        Optional<AgentState> result = graph.invoke(
                GraphInput.args(Map.of("input", "test1", "prop1", "test")),
                RunnableConfig.empty());
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1");

        assertIterableEquals(sortMap(expected), sortMap(result.get().data()));
        //assertDictionaryOfAnyEqual( expected, result.data )

    }

    @Test
    void testWithAppender() throws Exception {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentAction( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", "message1");
            }

            @NodeAction(name = "agent_2")
            public Map<String,Object> agentConfigMetadata( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_2", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", new String[]{"message2"});
            }

            @NodeAction(name = "agent_3")
            public Map<String,Object> agentMarkForRemoval( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_3", currentNode);

                log.info( "{}", currentNode );
                int steps = state.messages().size() + 1;
                return Map.of("messages", "message3", "steps", steps);
            }

        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .add(repo.node("agent_1"))
                .add(repo.node("agent_2"))
                .add(repo.node("agent_3"))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END)
                .compile();

        final var result = graph.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        log.info( "{}",result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(3, result.get().messages().size());
        assertIterableEquals(List.of("message1", "message2", "message3"), result.get().messages());

    }

    @Test
    void testWithAppenderOneRemove() throws Exception {
        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentAction( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", "message1");
            }

            @NodeAction(name = "agent_2")
            public Map<String,Object> agentConfigMetadata( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_2", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", new String[]{"message2"});
            }

            @NodeAction(name = "agent_3")
            public Map<String,Object> agentMarkForRemoval( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_3", currentNode);

                log.info( "{}", currentNode );
                int steps = state.messages().size() + 1;
                return Map.of("messages", RemoveByHash.of("message2"), "steps", steps);
            }
        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .add( repo.node("agent_1") )
                .add( repo.node("agent_2") )
                .add( repo.node("agent_3") )
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge(START, "agent_1")
                .addEdge("agent_3", END)
                .compile();

        final var result = graph.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        log.info("{}", result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(1, result.get().messages().size());
        assertIterableEquals(List.of("message1"), result.get().messages());

    }

    @Test
    void testWithAppenderOneAppendOneRemove() throws Exception {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "agent_1")
            public Map<String,Object> agentAction( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_1", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", "message1");
            }

            @NodeAction(name = "agent_2")
            public Map<String,Object> agentConfigMetadata( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_2", currentNode);

                log.info( "{}", currentNode );
                return Map.of("messages", new String[]{"message2"});
            }

            @NodeAction(name = "agent_3")
            public Map<String,Object> agentMarkForRemoval( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_3", currentNode);

                log.info( "{}", currentNode );
                int steps = state.messages().size() + 1;
                return Map.of("messages", List.of("message3", RemoveByHash.of("message2")), "steps", steps);
            }

            @NodeAction(name = "agent_4")
            public Map<String,Object> agentMarkForRemoval2( State state, RunnableConfig config ) {
                var currentNode = config.nodeId();
                assertEquals( "agent_4", currentNode);

                log.info( "{}", currentNode );
                int steps = state.messages().size() + 1;
                return Map.of("messages", List.of("message4"), "steps", steps);
            }


        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .add( repo.node("agent_1"))
                .add( repo.node("agent_2"))
                .add( repo.node("agent_3"))
                .add( repo.node("agent_4"))
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", "agent_3")
                .addEdge("agent_3", "agent_4")
                .addEdge(START, "agent_1")
                .addEdge("agent_4", END)
                .compile();

        Optional<State> result = graph.invoke(GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue(result.isPresent());
        System.out.println(result.get().data());
        assertEquals(3, result.get().steps());
        assertEquals(3, result.get().messages().size());
        assertIterableEquals(List.of("message1", "message3", "message4"), result.get().messages());

    }


    @Test
    public void testNestedNodeAndEdgeWrapHooks() throws Exception {
        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "action")
            public Map<String, Object> nodeAction(AgentState state, RunnableConfig config) {
                log.info("call node {}", config.nodeId());
                return Map.of("messages", config.nodeId());
            }

            @NodeAction(name = "node_1")
            public NodeActionWithConfig<AgentState> nodeActionFactory(CompileConfig compileConfig ) {
                return (AgentState state, RunnableConfig config) -> {
                    log.info("call node {}", config.nodeId());
                    return Map.of("messages", config.nodeId());
                };
            }

            @EdgeCondition( /*name = "route_to_node"*/ )
            public Command route_to_node() {
                return new Command("node_4" );
            }
        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var graph = new StateGraph<>(State.SCHEMA, State::new)
                .add( repo.node("node_1") )
                .addNode("node_2", repo.nodeAction("action") )
                .addNode("node_3", repo.nodeAction("action") )
                .addNode("node_4", repo.nodeAction("action") )
                .addEdge(START, "node_1")
                .addEdge("node_1", "node_2")
                .add( repo.edge("node_2",
                        "route_to_node",
                                EdgeMappings.builder()
                                    .to("node_3")
                                    .to("node_4")
                                    .toEND()
                                    .build()) )
                .addEdge("node_3", "node_4")
                .addEdge("node_4", END)
                .compile();

        final State state = graph.stream(
                    GraphInput.args(Map.of("input", "test1")),
                    RunnableConfig.empty())
                .toCompletableFuture()
                .thenApply(GraphResult::from)
                .thenApply(GraphResult::<State>asState)
                .join();

        assertIterableEquals( List.of("node_1", "node_2", "node_4"), state.messages());

    }

    @Test
    void testHandleRuntimeException() throws GraphStateException {

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "node_with_exception")
            public CompletableFuture<Map<String, Object>> nodeAction(State state, RunnableConfig config) {
                return failedFuture(new RuntimeException("test exception"));
            }

        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .add( repo.node("node_with_exception") )
                .addEdge(START, "node_with_exception")
                .addEdge("node_with_exception", END)
                .compile();
                ;

        try {
            workflow.invoke( GraphInput.noArgs(),
                                RunnableConfig.builder()
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

        class MyActionCatalog implements ActionCatalog {

            @NodeAction(name = "node_with_exception")
            public CompletableFuture<Map<String, Object>> nodeAction(State state, RunnableConfig config) {
                return failedFuture(new GraphRunnerException(config, "test exception"));
            }

        }

        final var repo = ActionCatalogRepository.of( new MyActionCatalog() );

        final var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .add( repo.node("node_with_exception") )
                .addEdge(START, "node_with_exception")
                .addEdge("node_with_exception", END)
                .compile();
        ;

        try {
            workflow.invoke(GraphInput.noArgs(),
                            RunnableConfig.builder()
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