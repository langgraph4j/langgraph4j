package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Resume of a graph that was interrupted inside a compiled subgraph, when the values given
 * on resume are already in the state and when the subgraph is nested in another subgraph.
 */
class NestedSubGraphResumeTest {

    static class State extends AgentState {
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                "messages", Channels.appenderWithDuplicate(ArrayList::new));

        State(Map<String, Object> initData) {
            super(initData);
        }

        String answer() {
            return this.<String>value("answer").orElse(null);
        }

        List<String> messages() {
            return this.<List<String>>value("messages").orElseGet(List::of);
        }
    }

    private final AtomicReference<State> resumedWith = new AtomicReference<>();
    private MemorySaver saver;
    private RunnableConfig config;

    @BeforeEach
    void setUp() {
        saver = new MemorySaver();
        config = RunnableConfig.builder().threadId("nested").build();
    }

    /** the innermost graph is interrupted before the node "ask", {@code depth} graphs are wrapped around it */
    private CompiledGraph<State> buildGraph(int depth) throws GraphStateException {
        var graph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("prepare", node_async(state -> Map.of()))
                .addNode("ask", node_async(state -> {
                    resumedWith.set(state);
                    return Map.of();
                }))
                .addEdge(START, "prepare")
                .addEdge("prepare", "ask")
                .addEdge("ask", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("ask")
                        .build());

        for (int level = 1; level <= depth; level++) {
            graph = new StateGraph<>(State.SCHEMA, State::new)
                    .addNode("start" + level, node_async(state -> Map.of()))
                    .addNode("sub" + level, graph)
                    .addEdge(START, "start" + level)
                    .addEdge("start" + level, "sub" + level)
                    .addEdge("sub" + level, END)
                    .compile(CompileConfig.builder()
                            .checkpointSaver(saver)
                            .build());
        }
        return graph;
    }

    private void runUntilInterruption(CompiledGraph<State> app, Map<String, Object> args) {
        assertTrue(app.invokeSync(GraphInput.args(args), config).isInterruptionMetadata());
        assertNull(resumedWith.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumeWithAValueThatIsAlreadyInTheState(int depth) throws Exception {
        final var app = buildGraph(depth);
        runUntilInterruption(app, Map.of("answer", "unknown"));

        app.invokeSync(GraphInput.resume(Map.of("answer", "yes")), config);

        assertEquals("yes", resumedWith.get().answer());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumeAfterUpdateState(int depth) throws Exception {
        final var app = buildGraph(depth);
        runUntilInterruption(app, Map.of());

        final var updatedConfig = app.updateState(config, Map.of("answer", "yes"));
        app.invokeSync(GraphInput.resume(), updatedConfig);

        assertEquals("yes", resumedWith.get().answer());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumeValuesOverrideUpdateState(int depth) throws Exception {
        final var app = buildGraph(depth);
        runUntilInterruption(app, Map.of());

        final var updatedConfig = app.updateState(config, Map.of("answer", "no"));
        app.invokeSync(GraphInput.resume(Map.of("answer", "yes")), updatedConfig);

        assertEquals("yes", resumedWith.get().answer());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumeDoesNotApplyTheParentStateAgain(int depth) throws Exception {
        final var app = buildGraph(depth);
        runUntilInterruption(app, Map.of("messages", List.of("hi")));

        app.invokeSync(GraphInput.resume(), config);

        assertEquals(List.of("hi"), resumedWith.get().messages());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumeAppendsTheNewMessageOnce(int depth) throws Exception {
        final var app = buildGraph(depth);
        runUntilInterruption(app, Map.of("messages", List.of("hi")));

        app.invokeSync(GraphInput.resume(Map.of("messages", "yes")), config);

        assertEquals(List.of("hi", "yes"), resumedWith.get().messages());
    }
}
