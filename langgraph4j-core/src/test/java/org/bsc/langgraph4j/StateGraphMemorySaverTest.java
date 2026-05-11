package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.VersionedMemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for simple App.
 */
public class StateGraphMemorySaverTest
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateGraphMemorySaverTest.class);
    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super( initData  );
        }

        int steps() {
            return this.<Integer>value("steps").orElse(0);
        }

    }

    NodeAction<State> agent_whether = state -> {
        String lastMessage = state.lastMessage().orElseThrow( () -> new IllegalStateException("No last message!") );

        if( lastMessage.contains( "temperature")) {
            return Map.of("messages", "whether in Naples is sunny");
        }
        if( lastMessage.contains( "whether")) {
            return Map.of("messages", "tool_calls");
        }
        if( lastMessage.contains( "bartolo")) {
            return Map.of("messages", "Hi bartolo, nice to meet you too! How can I assist you today?");
        }
        if(state.messages().stream().anyMatch(m -> m.contains("bartolo"))) {
            return Map.of("messages", "Hi, bartolo welcome back?");
        }
        throw new IllegalStateException( "unknown message!" );
    };

    // Simulate LLM agent
    NodeAction<State> tool_whether = state ->
            Map.of( "messages", "temperature in Napoli is 30 degree");

    EdgeAction<State> shouldContinue_whether = state ->
            state.lastMessage().filter( m -> m.equals("tool_calls")  )
                    .map( m -> "tools" )
                    .orElse(END);

    @BeforeAll
    public static void initLogging() throws IOException {
        try( var is = StateGraphMemorySaverTest.class.getResourceAsStream("/logging.properties") ) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

    @Test
    public void testCheckpointInitialState() throws Exception {
        NodeAction<AgentState> agent_1 = state -> {
            log.info( "agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var workflow = new StateGraph<>(AgentState::new)
            .addNode("agent_1", node_async( agent_1 ))
            .addEdge( START,"agent_1")
            .addEdge( "agent_1",  END)
        ;

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

        var runnableConfig = RunnableConfig.empty();
        var app = workflow.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "input", "test1");

        var initState = app.cloneState( app.initialState( inputs, runnableConfig ) );

        assertEquals( 1, initState.data().size() );
        assertTrue(  initState.value("input").isPresent() );
        assertEquals( "test1", initState.value("input").get() );

        //
        // Test checkpoint not override inputs
        //
        var newState = new AgentState( Map.of( "input", "test2") );

        saver.put( runnableConfig, Checkpoint.builder()
                .state( newState.data() )
                .nodeId(START)
                .nextNodeId("agent_1")
                .build() ) ;

        app = workflow.compile( compileConfig );
        initState = app.cloneState( app.initialState( inputs, runnableConfig ) );

        assertEquals( 1, initState.data().size() );
        assertTrue(  initState.value("input").isPresent() );
        assertEquals( "test1", initState.value("input").get() );

        // Test checkpoints are saved
        newState = new AgentState( Map.of( "input", "test2", "agent_1:prop1", "agent_1:test") );
        saver.put( runnableConfig, Checkpoint.builder()
                .state( newState )
                .nodeId( "agent_1")
                .nextNodeId(END)
                .build() ) ;
        app = workflow.compile( compileConfig );
        initState = app.cloneState( app.initialState( inputs, runnableConfig ) );

        assertEquals( 2, initState.data().size() );
        assertTrue(  initState.value("input").isPresent() );
        assertEquals( "test1", initState.value("input").get() );
        assertTrue(  initState.value("agent_1:prop1").isPresent() );
        assertEquals( "agent_1:test", initState.value("agent_1:prop1").get() );

        var checkpoints = saver.list(runnableConfig);
        assertEquals( 2, checkpoints.size() );
        Optional<Checkpoint> last = saver.get(runnableConfig);
        assertTrue( last.isPresent() );
        assertEquals( "agent_1", last.get().getNodeId() );
        assertNotNull( last.get().getState().get("agent_1:prop1") );
        assertEquals( "agent_1:test", last.get().getState().get("agent_1:prop1") );

        var tag = saver.release(runnableConfig);

        assertIterableEquals( checkpoints, tag.checkpoints() );

        var checkpointsAfterTag = saver.list(runnableConfig);
        assertTrue( checkpointsAfterTag.isEmpty() );
    }


    @Test
    public void testCheckpointSaverResubmit() throws Exception {
        int expectedSteps = 5;

        NodeAction<State> agent_1 = state -> {
            int steps = state.steps() + 1;
            log.info( "agent_1: step: {}", steps );
            return Map.of("steps", steps, "messages", format( "agent_1:step %d", steps ));
        };

        EdgeAction<State> shouldContinue = state -> {
            int steps = state.steps();
            if (steps >= expectedSteps) {
                return "exit";
            }
            return "next";
        };

        var workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async(agent_1))
                .addConditionalEdges( "agent_1",
                        edge_async( shouldContinue),
                        Map.of( "next", "agent_1", "exit", END) );;

        var saver = new VersionedMemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

       var app = workflow.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "steps", 0 );

        var runnableConfig = RunnableConfig.builder()
                                    .threadId("thread_1")
                                    .build();

        var state = app.invoke( GraphInput.args(inputs), runnableConfig );

        assertTrue( state.isPresent() );
        assertEquals( expectedSteps, state.get().steps() );

        var messages = state.get().messages();
        assertFalse( messages.isEmpty() );

        log.info( "{}", messages );

        assertEquals( expectedSteps, messages.size() );
        for( int i = 0; i < messages.size(); i++ ) {
            assertEquals( format("agent_1:step %d", i+1), messages.get(i) );
        }

        assertTrue( saver.lastVersionByThreadId(runnableConfig).isEmpty() );

        var snapshot = app.getState( runnableConfig );

        assertNotNull( snapshot );
        log.info( "SNAPSHOT:\n{}\n", snapshot );

        // SUBMIT NEW THREAD 2
        runnableConfig = RunnableConfig.builder()
                .threadId("thread_2")
                .build();

        state = app.invoke( GraphInput.noArgs(), runnableConfig );

        assertTrue( state.isPresent() );
        assertEquals( expectedSteps, state.get().steps() );
        messages = state.get().messages();

        log.info( "{}", messages );

        assertEquals( expectedSteps, messages.size() );
        for( int i = 0; i < messages.size(); i++ ) {
            assertEquals( format("agent_1:step %d", i+1), messages.get(i) );
        }

        // RE-SUBMIT THREAD 1
        state = app.invoke( GraphInput.noArgs(), runnableConfig );

        assertTrue( state.isPresent() );
        assertEquals( expectedSteps + 1, state.get().steps() );
        messages = state.get().messages();

        log.info( "{}", messages );

        assertEquals( expectedSteps + 1, messages.size() );

    }

    @Test
    public void testViewAndUpdatePastGraphState() throws Exception {

        var workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent", node_async(agent_whether) )
                .addNode("tools", node_async(tool_whether) )
                .addEdge(START, "agent")
                .addConditionalEdges("agent",
                        edge_async(shouldContinue_whether),
                        Map.of("tools", "tools", END, END))
                .addEdge("tools", "agent");


        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var app = workflow.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "messages", "whether in Naples?") ;

        var runnableConfig = RunnableConfig.builder()
                .threadId("thread_1")
                .build();

        var results = app.streamSnapshots(  GraphInput.args(inputs), runnableConfig ).stream().collect( Collectors.toList() );

        results.forEach( r -> log.info( "{}: Node: {} - {}", r.getClass().getSimpleName(), r.node(), r.state().messages() ) );

        assertEquals( 5, results.size() );
        assertInstanceOf( NodeOutput.class, results.get(0) );
        assertInstanceOf( StateSnapshot.class, results.get(1) );
        assertInstanceOf( StateSnapshot.class, results.get(2) );
        assertInstanceOf( StateSnapshot.class, results.get(3) );
        assertInstanceOf( NodeOutput.class, results.get(4) );

        var snapshot = app.getState(runnableConfig);
        assertNotNull( snapshot );
        assertEquals( END, snapshot.next() );

        log.info( "LAST SNAPSHOT:\n{}\n", snapshot );

        var stateHistory = app.getStateHistory( runnableConfig );
        stateHistory.forEach( state -> log.info( "SNAPSHOT HISTORY:\n{}\n", state ) );
        assertNotNull( stateHistory );
        assertEquals( 4, stateHistory.size() );

        for( StateSnapshot<State> s : stateHistory ) {
            log.info( "SNAPSHOT HISTORY:\n{}\n", s );
        }

        results = app.stream( GraphInput.resume(), runnableConfig ).stream().collect( Collectors.toList() );

        assertNotNull( results );
        assertFalse( results.isEmpty() );
        assertEquals( 1, results.size() );
        assertTrue( results.get(0).state().lastMessage().isPresent() );
        assertEquals( "whether in Naples is sunny", results.get(0).state().lastMessage().get() );

        Optional<StateSnapshot<State>> firstSnapshot = stateHistory.stream().reduce( (first, second) -> second); // take the last
        assertTrue( firstSnapshot.isPresent() );
        assertTrue( firstSnapshot.get().state().lastMessage().isPresent() );
        assertEquals( "whether in Naples?", firstSnapshot.get().state().lastMessage().get() );

        var toReplay = firstSnapshot.get().config();

        toReplay = app.updateState( toReplay, Map.of( "messages", "i'm bartolo") );
        results = app.stream( GraphInput.resume(), toReplay ).stream().collect( Collectors.toList() );

        assertNotNull( results );
        assertFalse( results.isEmpty() );
        assertEquals( 2, results.size() );
        assertEquals( END, results.get(1).node() );
        assertTrue( results.get(1).state().lastMessage().isPresent() );
        assertEquals( "Hi bartolo, nice to meet you too! How can I assist you today?", results.get(0).state().lastMessage().get() );

    }

    @Test
    public void testPauseAndUpdatePastGraphState() throws Exception {

        var workflow = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent", node_async(agent_whether) )
                .addNode("tools", node_async(tool_whether) )
                .addEdge(START, "agent")
                .addConditionalEdges("agent",
                        edge_async(shouldContinue_whether),
                        Map.of("tools", "tools", END, END))
                .addEdge("tools", "agent");

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("tools")
                .build();

        var app = workflow.compile( compileConfig );

        var runnableConfig = RunnableConfig.builder()
                .threadId("thread_1")
                .build();

        log.info( "FIRST CALL WITH INTERRUPT BEFORE 'tools'");
        Map<String,Object> inputs = Map.of( "messages", "whether in Naples?")  ;
        var results = app.stream(  GraphInput.args(inputs), runnableConfig ).stream()
                                .peek( n -> log.info( "{}", n ) )
                                .collect(Collectors.toList());
        assertNotNull( results );
        assertEquals( 2, results.size() );
        assertEquals( START, results.get(0).node() );
        assertEquals( "agent", results.get(1).node() );
        assertTrue( results.get(1).state().lastMessage().isPresent() );

        var state = app.getState(runnableConfig);

        assertNotNull( state );
        assertEquals( "tools", state.next() );

        log.info( "RESUME CALL");
        results = app.stream( GraphInput.resume(), state.config() ).stream()
                                        .peek(n -> log.info( "{}", n ) )
                                        .collect(Collectors.toList());

        assertNotNull( results );
        assertEquals( 3, results.size() );
        assertEquals( "tools", results.get(0).node() );
        assertEquals( "agent", results.get(1).node() );
        assertEquals( END, results.get(2).node() );
        assertTrue( results.get(2).state().lastMessage().isPresent() );
        assertEquals( "whether in Naples is sunny", results.get(2).state().lastMessage().get() );

    }

    @Test
    public void testMemoryWithVersionsSaver() throws Exception {

        var threadId = "thread_1";

        var saver = new VersionedMemorySaver();

        // Check for error
        var configWithVersion = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // Create a new version of thread_1
        var configWithoutVersion = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        var checkpoint = Checkpoint.builder()
                .state(new AgentState(Map.of()))
                .nodeId(START)
                .nextNodeId(END)
                .build();

        var newConfig = saver.put(configWithoutVersion, checkpoint);

        var list = saver.list(newConfig);

        assertEquals(1, list.size());

        var tag = saver.release(newConfig);

        assertEquals(1, tag.checkpoints().size());

        var versions = saver.versionsByThreadId( threadId );

        assertEquals(1, versions.size());

        // Check if checkpoints collection  is immutable
        assertThrowsExactly(UnsupportedOperationException.class, () -> tag.checkpoints().remove(checkpoint));

        var configWithVersion1 = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

        assertEquals(1, tag.checkpoints().size());

        versions = saver.versionsByThreadId(configWithVersion);

        assertEquals(1, versions.size());
        assertEquals( checkpoint.getId(), list.stream().findFirst().map(Checkpoint::getId).orElseThrow() );

        var checkpoint_1 = Checkpoint.builder()
                .state(new AgentState(Map.of()))
                .nodeId("test")
                .nextNodeId(END)
                .build();
        var checkpoint_2 = Checkpoint.builder()
                .state(new AgentState(Map.of()))
                .nodeId("test_1")
                .nextNodeId(END)
                .build();

        configWithVersion1 = saver.put( configWithVersion1, checkpoint_1 );

        configWithVersion1 = saver.put( configWithVersion1.withCheckPointId(null), checkpoint_2 );

        versions = saver.versionsByThreadId( threadId );

        assertEquals(1, versions.size());

        var tag2 = saver.release(configWithVersion1);

        assertEquals(2, tag2.checkpoints().size());

    }

}
