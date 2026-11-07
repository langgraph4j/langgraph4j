package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for simple App.
 */
public class LG4JFileSystemSaverTest implements LG4JLoggable {

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super( initData  );
        }

        int steps() {
            return this.<Integer>value("steps").orElse(0);
        }

    }

    static class JsonStateSerializer extends JacksonStateSerializer<State> {
        public JsonStateSerializer() {
            super(State::new);
        }
    }

    static class StdStateSerializer extends ObjectStreamStateSerializer<State> {
        public StdStateSerializer() {
            super(State::new);
        }
    }

    public enum StateSerializerEnum {
        STD( new StdStateSerializer() ),
        JSON( new JsonStateSerializer() )
        ;

        private final org.bsc.langgraph4j.serializer.StateSerializer<State> value;

        StateSerializerEnum(org.bsc.langgraph4j.serializer.StateSerializer<State> stateSerializer) {
            this.value = stateSerializer;
        }
    }


    final String rootPath = Paths.get( "target", "checkpoint" ).toString();

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointSaverResubmit( StateSerializerEnum stateSerializer ) throws Exception {
        int expectedSteps = 5;

        final var checkpointStore = Paths.get( rootPath, "testCheckpointSaverResubmit" );

        var workflow = new StateGraph<>(State.SCHEMA, stateSerializer.value )
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async( state -> {
                    int steps = state.steps() + 1;
                    log.info( "agent_1: step: {}", steps );
                    return Map.of("steps", steps, "messages", format( "agent_1:step %d", steps ));
                }))
                .addConditionalEdges( "agent_1", edge_async( state -> {
                    int steps = state.steps();
                    if( steps >= expectedSteps) {
                        return "exit";
                    }
                    return "next";
                }), Map.of( "next", "agent_1", "exit", END) );

        var saver = new FileSystemSaver( checkpointStore, workflow.getStateSerializer() );

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        CompiledGraph<State> app = workflow.compile( compileConfig );

        RunnableConfig runnableConfig_1 = RunnableConfig.builder()
                                    .threadId("thread_1")
                                    .build();

        RunnableConfig runnableConfig_2 = RunnableConfig.builder()
                                            .threadId("thread_2")
                                            .build();

        try {

            for (int execution = 0; execution < 2; execution++) {

                var result = app.invokeSync( GraphInput.noArgs(), runnableConfig_1);

                assertTrue(result.isStateData());
                assertEquals(expectedSteps + (execution * 2), result.<State>asState().steps());;

                List<String> messages = result.<State>asState().messages();
                assertFalse(messages.isEmpty());

                log.info("thread_1: execution: {} messages:\n{}\n", execution, messages);

                assertEquals(expectedSteps + execution * 2, messages.size());
                for (int i = 0; i < messages.size(); i++) {
                    assertEquals(format("agent_1:step %d", (i + 1)), messages.get(i));
                }

                StateSnapshot<State> snapshot = app.getState(runnableConfig_1);

                assertNotNull(snapshot);
                log.info("SNAPSHOT:\n{}\n", snapshot);

                // SUBMIT NEW THREAD 2

                result = app.invokeSync(GraphInput.noArgs(), runnableConfig_2);

                assertTrue(result.isStateData());
                assertEquals(expectedSteps + execution, result.<State>asState().steps());
                messages = result.<State>asState().messages();

                log.info("thread_2: execution: {} messages:\n{}\n", execution, messages);

                assertEquals(expectedSteps + execution, messages.size());

                // RE-SUBMIT THREAD 1
                result = app.invokeSync(GraphInput.noArgs(), runnableConfig_1);

                assertTrue(result.isStateData());
                assertEquals(expectedSteps + 1 + execution * 2, result.<State>asState().steps());
                messages = result.<State>asState().messages();

                log.info("thread_1: execution: {} messages:\n{}\n", execution, messages);

                assertEquals(expectedSteps + 1 +  execution * 2, messages.size());

            }
        }
        finally {

            saver.release(runnableConfig_1);
            saver.release(runnableConfig_2);
        }
    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointSaverWithManualRelease( StateSerializerEnum stateSerializer ) throws Exception {
        int expectedSteps = 5;

        var workflow = new StateGraph<>(State.SCHEMA, stateSerializer.value )
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async( state -> {
                    int steps = state.steps() + 1;
                    log.info( "agent_1: step: {}", steps );
                    return Map.of("steps", steps, "messages", format( "agent_1:step %d", steps ));
                }))
                .addConditionalEdges( "agent_1", edge_async( state -> {
                    int steps = state.steps();
                    if( steps >= expectedSteps) {
                        return "exit";
                    }
                    return "next";
                }), Map.of( "next", "agent_1", "exit", END) );

        var saver = new FileSystemSaver( Paths.get( rootPath, "testCheckpointSaverWithManualRelease" ),
                workflow.getStateSerializer() );

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var app = workflow.compile( compileConfig );

        var runnableConfig_1 = RunnableConfig.builder()
                .threadId("thread_1")
                .build();
        // saver.deleteFile( runnableConfig_1 );

        var runnableConfig_2 = RunnableConfig.builder()
                .threadId("thread_2")
                .build();
        // saver.deleteFile( runnableConfig_2 );

        var result = app.invokeSync( GraphInput.noArgs(), runnableConfig_1);

        assertTrue(result.isStateData());
        assertEquals(expectedSteps, result.<State>asState().steps());

        var tag = saver.release(runnableConfig_1);
        assertNotNull( tag );
        assertEquals( "thread_1", tag.threadId());

        var tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();
        assertTrue( tagState.isPresent() );

        assertIterableEquals( result.<State>asState().data().entrySet(), tagState.get().entrySet() );

        var messages = result.<State>asState().messages();

        assertEquals(expectedSteps, messages.size());

        for (int i = 0; i < messages.size(); i++) {
            assertEquals(format("agent_1:step %d", (i + 1)), messages.get(i));
        }

        var ex = assertThrowsExactly( IllegalStateException.class, () -> app.getState(runnableConfig_1));
        assertEquals( "Missing Checkpoint!", ex.getMessage() );

        // SUBMIT NEW THREAD 2

        result = app.invokeSync(GraphInput.noArgs(), runnableConfig_2);

        assertTrue(result.isStateData());
        assertEquals(expectedSteps, result.<State>asState().steps());
        messages = result.<State>asState().messages();

        tag = saver.release(runnableConfig_2);
        assertNotNull( tag );
        assertEquals( "thread_2", tag.threadId());

        tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();
        assertTrue( tagState.isPresent() );

        assertIterableEquals( result.<State>asState().data().entrySet(), tagState.get().entrySet() );

        assertEquals(expectedSteps, messages.size());

        // RE-SUBMIT THREAD 1
        result = app.invokeSync(GraphInput.noArgs(), runnableConfig_1);

        assertTrue(result.isStateData());
        assertEquals(expectedSteps, result.<State>asState().steps());

        tag = saver.release(runnableConfig_1);
        assertNotNull( tag );
        assertEquals( "thread_1", tag.threadId());

        tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();
        assertTrue( tagState.isPresent() );

        assertIterableEquals( result.<State>asState().data().entrySet(), tagState.get().entrySet() );

    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointSaverWithAutoRelease( StateSerializerEnum stateSerializer ) throws Exception {
        int expectedSteps = 5;

        final var workflow = new StateGraph<>(State.SCHEMA, stateSerializer.value )
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async( state -> {
                    int steps = state.steps() + 1;
                    log.info( "agent_1: step: {}", steps );
                    return Map.of("steps", steps, "messages", format( "agent_1:step %d", steps ));
                }))
                .addConditionalEdges( "agent_1", edge_async( state -> {
                    int steps = state.steps();
                    if( steps >= expectedSteps) {
                        return "exit";
                    }
                    return "next";
                }), Map.of( "next", "agent_1", "exit", END) );

        var saver = new FileSystemSaver( Paths.get( rootPath, "testCheckpointSaverWithManualRelease" ),
                workflow.getStateSerializer() );

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var app = workflow.compile( compileConfig );

        var runnableConfig_1 = RunnableConfig.builder()
                .threadId("thread_1")
                .build();

        var runnableConfig_2 = RunnableConfig.builder()
                .threadId("thread_2")
                .build();

        var result_1 = app.invokeSync( GraphInput.noArgs(), runnableConfig_1);

        assertTrue(result_1.isStateData());
        assertEquals(expectedSteps, result_1.<State>asState().steps());

        var tag = saver.release(runnableConfig_1);
        assertNotNull( tag );
        assertEquals( "thread_1", tag.threadId());

        var tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();
        assertTrue( tagState.isEmpty() );

        var messages = result_1.<State>asState().messages();

        assertEquals(expectedSteps, messages.size());

        for (int i = 0; i < messages.size(); i++) {
            assertEquals(format("agent_1:step %d", (i + 1)), messages.get(i));
        }

        var ex = assertThrowsExactly( IllegalStateException.class, () -> app.getState(runnableConfig_1));
        assertEquals( "Missing Checkpoint!", ex.getMessage() );

        // SUBMIT NEW THREAD 2

        var result_2 = app.invokeSync(GraphInput.noArgs(), runnableConfig_2);

        assertTrue(result_2.isStateData());
        assertEquals(expectedSteps, result_2.<State>asState().steps());
        messages = result_2.<State>asState().messages();

        tag = saver.release(runnableConfig_2);
        assertEquals( "thread_2", tag.threadId());
        assertNotNull( tag );

        tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();

        assertTrue( tagState.isEmpty() );
        assertEquals(expectedSteps, messages.size());

        // RE-SUBMIT THREAD 1
        var result = app.invokeSync(GraphInput.noArgs(), runnableConfig_1);

        assertFalse( result.isEmpty() );
        assertTrue( result.isCheckpointSaverTag() );
        tag = result.asCheckpointSaverTag();
        tagState = tag.checkpoints().stream().map(Checkpoint::state).findFirst();

        assertTrue( tagState.isPresent() );
        assertIterableEquals( result.<State>asState().data().entrySet(), tagState.get().entrySet() );


    }

    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public void testCheckpointCreationDateSurvivesPersistence( StateSerializerEnum stateSerializer ) throws Exception {
        final var checkpointStore = Paths.get( rootPath, "testCheckpointCreationDateSurvivesPersistence" );

        final var workflow = new StateGraph<>(State.SCHEMA, stateSerializer.value )
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async( state ->
                        Map.of("steps", 1, "messages", "agent_1:step 1") ))
                .addEdge("agent_1", END);

        final var saver = new FileSystemSaver( checkpointStore, workflow.getStateSerializer() );

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        final var app = workflow.compile( compileConfig );

        final var runnableConfig = RunnableConfig.builder()
                .threadId("thread_1")
                .build();

        try {
            final var result = app.invokeSync( GraphInput.noArgs(), runnableConfig );

            assertTrue(result.isStateData());
            assertEquals(1, result.<State>asState().steps());

            // reload the checkpoints from the file system using a brand new saver instance
            final var reloadedSaver = new FileSystemSaver( checkpointStore, workflow.getStateSerializer() );
            final var checkpoints = reloadedSaver.list( runnableConfig ).stream().toList();

            assertFalse(checkpoints.isEmpty());
            for( final var checkpoint : checkpoints ) {
                assertNotNull( checkpoint.savedAt(), "creation date must survive the persistence round trip" );
            }

            // checkpoints are returned newest first: creation dates must not increase going deeper in the list
            for( int i = 0 ; i < checkpoints.size() - 1 ; i++ ) {
                final var instant = checkpoints.get(i).savedAt();
                assertNotNull(instant);
                final var otherInstant = checkpoints.get(i + 1).savedAt();
                assertNotNull(otherInstant);
                assertFalse( instant.isBefore(otherInstant) );
            }

            final var snapshot = app.getState( runnableConfig );
            assertNotNull(snapshot);
            assertEquals( checkpoints.get(0).savedAt(), snapshot.savedAt() );
        }
        finally {
            saver.release( runnableConfig );
        }
    }

}
