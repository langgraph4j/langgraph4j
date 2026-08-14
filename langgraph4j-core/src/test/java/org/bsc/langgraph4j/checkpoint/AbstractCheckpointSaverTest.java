package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class AbstractCheckpointSaverTest implements LG4JTestUtil, LG4JLoggable {

    /**
     * Build a checkpoint saver for the given state serializer.
     * This checkpoint saver must not drop table or delete data from the database, as it will be used in multiple tests.
     *
     * @param stateSerializer the state serializer to use for serializing the workflow state
     * @return a new instance of a checkpoint saver
     * @throws Exception if an error occurs while building the checkpoint saver
     */
    protected abstract BaseCheckpointSaver buildCheckpointSaver(StateSerializer<? extends AgentState> stateSerializer, @Nullable String threadId) throws Exception;

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public final void testCheckpointWithInterruption(StateSerializerEnum param) throws Exception {

        final var threadId = switch( param ){
            case JSON -> "json-thread-testCheckpointWithInterruption";
            case BINARY -> "binary-thread-testCheckpointWithInterruption";
        };

        final var agent1 = CustomNodeAction.of("agent_1");
        final var agent2 = CustomNodeAction.of("agent_2");

        final var graph = new StateGraph<>(State.SCHEMA, param.stateSerializer)
                .addNode("agent_1", agent1)
                .addNode("agent_2", agent2)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", END);

        var compileConfig = CompileConfig.builder()
                .interruptBefore("agent_2")
                .build();


        var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        { // STEP 1
            var saver = buildCheckpointSaver(param.stateSerializer, threadId);

            var workflow = graph.compile(CompileConfig.builder(compileConfig)
                    .checkpointSaver(saver)
                    .build());

            try {
                workflow.stream(GraphInput.noArgs(), runnableConfig).toCompletableFuture()
                        .thenApply(GraphResult::from)
                        .thenAccept(result -> {
                            assertTrue(result.isInterruptionMetadata());

                            final InterruptionMetadata<State> im = result.asInterruptionMetadata();

                            assertEquals(1, im.state().messages().size());

                            final var value = im.state().lastMessage();
                            assertTrue(value.isPresent());
                            assertEquals("agent_1", value.get());
                        })
                        .join();
            }
            catch (Exception e) {
                saver.releaseOnError(runnableConfig, e);
            }
        }

        { // STEP 2

            var saver = buildCheckpointSaver(param.stateSerializer, threadId);

            var workflow = graph.compile(CompileConfig.builder(compileConfig)
                    .checkpointSaver(saver)
                    .build());

            try {
                workflow.stream(GraphInput.resume(), runnableConfig).toCompletableFuture()
                        .thenApply(GraphResult::from)
                        .thenAccept(result -> {
                            assertTrue(result.isCheckpointSaverTag());

                            final var im = result.asCheckpointSaverTag()
                                    .checkpoints()
                                    .stream()
                                    .findFirst();
                            assertTrue(im.isPresent());

                            final var state = new State(im.get().getState());
                            assertEquals(2, state.messages().size());

                            Optional<String> value = state.lastMinus(1);
                            assertTrue(value.isPresent());
                            assertEquals("agent_1", value.get());
                            value = state.lastMessage();
                            assertTrue(value.isPresent());
                            assertEquals("agent_2", value.get());
                        })
                        .join();
            }
            catch (Exception e) {
                saver.releaseOnError(runnableConfig, e);
                throw e;
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public final void testCheckpointWithReleasedThread( StateSerializerEnum param ) throws Exception {

        final var saver = buildCheckpointSaver(param.stateSerializer, null);

        final var agent1 = CustomNodeAction.of("agent_1");

        var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("agent_1", agent1)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var runnableConfig = RunnableConfig.empty();

        var workflow = graph.compile(compileConfig);

        try {
            var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

            assertTrue(result.isPresent());
            assertTrue(workflow.getStateHistory(runnableConfig).isEmpty());
        }
        catch (Exception e) {
            saver.releaseOnError(runnableConfig, e);
            throw e;
        }
    }

    @ParameterizedTest
    @EnumSource(StateSerializerEnum.class)
    public final void testCheckpointWithNotReleasedThread(StateSerializerEnum param ) throws Exception  {


        final var threadId = switch( param ){
            case JSON -> "json-thread-testCheckpointWithNotReleasedThread";
            case BINARY -> "binary-thread-testCheckpointWithNotReleasedThread";
        };

        var saver = buildCheckpointSaver(param.stateSerializer, threadId);

        final var agent1 = CustomNodeAction.of("agent_1");

        var graph = new StateGraph<>(State.SCHEMA, param.stateSerializer)
                .addNode("agent_1", agent1)
                .addEdge(START, "agent_1")
                .addEdge("agent_1", END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        var workflow = graph.compile(compileConfig);

        try {
            var result = workflow.invoke(GraphInput.args(Map.of("input", "test1")), runnableConfig);

            assertTrue(result.isPresent());

            var history = workflow.getStateHistory(runnableConfig);

            assertFalse(history.isEmpty());
            assertEquals(2, history.size());

            var lastSnapshot = workflow.lastStateOf(runnableConfig);

            assertTrue(lastSnapshot.isPresent());
            assertEquals("agent_1", lastSnapshot.get().node());
            assertEquals(END, lastSnapshot.get().next());

            var updatedConfig = workflow.updateState(lastSnapshot.get().config(), Map.of("update", "update test"));

            var updatedSnapshot = workflow.stateOf(updatedConfig);
            assertTrue(updatedSnapshot.isPresent());
            assertEquals("agent_1", updatedSnapshot.get().node());
            assertTrue(updatedSnapshot.get().state().value("update").isPresent());
            assertEquals("update test", updatedSnapshot.get().state().value("update").get());
            assertEquals(END, updatedSnapshot.get().next());

            saver = buildCheckpointSaver(param.stateSerializer, threadId);

            compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .releaseThread(false)
                    .build();

            workflow = graph.compile(compileConfig);

            history = workflow.getStateHistory(runnableConfig);

            assertFalse( history.isEmpty(), "State history should not be empty after reloading the workflow");
            assertEquals(2, history.size());

            updatedSnapshot = workflow.stateOf(updatedConfig);

            assertTrue(updatedSnapshot.isPresent());
            assertEquals("agent_1", updatedSnapshot.get().node());
            assertTrue(updatedSnapshot.get().state().value("update").isPresent());
            assertEquals("update test", updatedSnapshot.get().state().value("update").get());
            assertEquals(END, updatedSnapshot.get().next());

            saver.release(runnableConfig);

            assertTrue(workflow.getStateHistory(runnableConfig).isEmpty());

        }
        catch (Exception e) {
            saver.releaseOnError(runnableConfig, e);
            throw e;
        }

    }

    /**
     * refer to issue <a href="https://github.com/langgraph4j/langgraph4j/issues/333">#333<a></a>
     */
    @ParameterizedTest
    @EnumSource( StateSerializerEnum.class )
    public final void testIssue333( StateSerializerEnum param ) throws Exception {

        buildCheckpointSaver(param.stateSerializer, "idempotent-thread-1");

        buildCheckpointSaver(param.stateSerializer, "idempotent-thread-1");


    }

}
