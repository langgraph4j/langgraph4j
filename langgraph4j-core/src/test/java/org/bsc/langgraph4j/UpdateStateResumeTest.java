package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link CompiledGraph#updateState(RunnableConfig, Map, String)}
 * on graphs containing subgraphs.
 * <p>
 * The values given to {@code updateState} are merged into the checkpoint right away.
 * They must be propagated via {@link RunnableConfig#SUBGRAPH_RESUME_UPDATE_DATA} only
 * when resuming into a subgraph node (the subgraph applies them to its own checkpoint
 * on resume). Propagating them when resuming into a regular node made the graph
 * Emitter apply non-idempotent channel updates a second time on resume.
 */
class UpdateStateResumeTest {

    static class State extends AgentState {
        // non-idempotent reducer: applying the same update twice is observable
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                "count", Channels.base(
                        (Integer current, Integer update) ->
                                (current == null ? 0 : current) + (update == null ? 0 : update),
                        () -> 0));

        State(Map<String, Object> initData) {
            super(initData);
        }

        int count() {
            return this.<Integer>value("count").orElse(0);
        }
    }

    private CompiledGraph<State> buildGraph(MemorySaver saver) throws GraphStateException {
        var subGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("sub_a", node_async(state -> Map.of()))
                .addEdge(START, "sub_a")
                .addEdge("sub_a", END)
                .compile();

        return new StateGraph<>(State.SCHEMA, State::new)
                .addNode("before", node_async(state -> Map.of()))
                .addNode("mySub", subGraph)
                .addNode("after", node_async(state -> Map.of()))
                .addEdge(START, "before")
                .addEdge("before", "mySub")
                .addEdge("mySub", "after")
                .addEdge("after", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("after")
                        .build());
    }

    private static int lastNodeCount(CompiledGraph<State> app, GraphInput input, RunnableConfig config) {
        return app.stream(input, config).stream()
                .reduce((a, b) -> b)
                .map(NodeOutput::state)
                .orElseThrow()
                .count();
    }

    @Test
    void updateStateAppliedOnceWhenResumingIntoRegularNode() throws Exception {
        var saver = new MemorySaver();
        var app = buildGraph(saver);
        var config = RunnableConfig.builder().threadId("t1").build();

        // run until interruption before "after"
        app.stream(GraphInput.noArgs(), config).stream().reduce((a, b) -> b);

        // human-in-the-loop update
        var resumeConfig = app.updateState(config, Map.of("count", 10));

        // values must be visible in the checkpoint right away ...
        assertEquals(10, app.getState(resumeConfig).state().count());
        // ... and must NOT be propagated for resume: the checkpoint already holds them
        assertTrue(resumeConfig.metadata(RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA,
                        new TypeRef<Map<String, Object>>() {}).isEmpty(),
                "resume update data must not be set when resuming into a regular node");

        // resume: the update must be applied exactly once
        assertEquals(10, lastNodeCount(app, GraphInput.resume(), resumeConfig));
    }

    @Test
    void updateStatePropagatesValuesWhenResumingIntoSubgraphNode() throws Exception {
        var saver = new MemorySaver();
        var subGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("sub_a", node_async(state -> Map.of()))
                .addEdge(START, "sub_a")
                .addEdge("sub_a", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore("sub_a")
                        .build());

        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("mySub", subGraph)
                .addNode("after", node_async(state -> Map.of()))
                .addEdge(START, "mySub")
                .addEdge("mySub", "after")
                .addEdge("after", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        var config = RunnableConfig.builder().threadId("t2").build();

        // run until interruption inside the subgraph
        var generator = app.stream(GraphInput.noArgs(), config);
        generator.stream().forEach(nodeOutput -> { });
        var result = GraphResult.from(generator);
        assertTrue(result.isInterruptionMetadata(), "expected interruption from subgraph, got: " + result.type());

        // human-in-the-loop update while interrupted before the subgraph node
        var resumeConfig = app.updateState(config, Map.of("count", 10));

        // values must still be propagated: the subgraph applies them to its own checkpoint on resume
        Optional<Map<String, Object>> resumeData = resumeConfig.metadata(
                RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, new TypeRef<Map<String, Object>>() {});
        assertTrue(resumeData.isPresent(),
                "resume update data must be set when resuming into a subgraph node");
        assertEquals(10, resumeData.get().get("count"));

        // resume: subgraph completes and the parent finishes with the update applied once
        assertEquals(10, lastNodeCount(app, GraphInput.resume(), resumeConfig));
    }
}
