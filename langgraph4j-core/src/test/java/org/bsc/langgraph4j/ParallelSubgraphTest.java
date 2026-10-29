package org.bsc.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for parallel branches containing subgraphs.
 *
 * <p>A parallel branch that runs a subgraph yields full-state snapshots. Merging every
 * snapshot as a delta (and then merging the final result on top) applied non-idempotent
 * reducers multiple times. Moreover, {@link node.ParallelNode} returned the full merged
 * state, which the graph {@code Emitter} merged <em>again</em> as if it were a node delta.
 *
 * <p>The fix: a subgraph branch contributes its final full state, merged into the parent
 * via key replacement (changed keys take the subgraph's values, removed keys are dropped);
 * regular branches still merge their deltas via channels. The parallel node result then
 * replaces (not merges into) the parent state.
 */
class ParallelSubgraphTest {

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

    private static CompiledGraph<State> subGraphAdding(int delta) throws GraphStateException {
        return new StateGraph<>(State.SCHEMA, State::new)
                .addNode("add", node_async(state -> Map.of("count", delta)))
                .addEdge(START, "add")
                .addEdge("add", END)
                .compile();
    }

    private static int invokeCount(CompiledGraph<State> app) throws Exception {
        return app.invoke(GraphInput.noArgs(),RunnableConfig.empty()).orElseThrow().count();
    }

    @Test
    void parallelSubgraphBranchAppliesUpdateOnce() throws Exception {
        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("sub", subGraphAdding(100))
                .addNode("plusOne", node_async(state -> Map.of("count", 1)))
                .addNode("join", node_async(state -> Map.of()))
                .addEdge(START, "sub")
                .addEdge(START, "plusOne")
                .addEdge("sub", "join")
                .addEdge("plusOne", "join")
                .addEdge("join", END)
                .compile();

        // 0 + 100 (subgraph) + 1 (regular branch); previously 301 (subgraph update applied 3x, then merged again as delta)
        assertEquals(101, invokeCount(app));
    }

    @Test
    void parallelSubgraphBranchWithNonDefaultParentState() throws Exception {
        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("init5", node_async(state -> Map.of("count", 5)))
                .addNode("sub", subGraphAdding(100))
                .addNode("plusOne", node_async(state -> Map.of("count", 1)))
                .addNode("join", node_async(state -> Map.of()))
                .addEdge(START, "init5")
                .addEdge("init5", "sub")
                .addEdge("init5", "plusOne")
                .addEdge("sub", "join")
                .addEdge("plusOne", "join")
                .addEdge("join", END)
                .compile();

        // 5 + 100 (subgraph) + 1 (regular branch); previously 326
        assertEquals(106, invokeCount(app));
    }

    @Test
    void parallelBranchesWithoutSubgraphDoNotDoubleApply() throws Exception {
        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("init5", node_async(state -> Map.of("count", 5)))
                .addNode("plus100", node_async(state -> Map.of("count", 100)))
                .addNode("plusOne", node_async(state -> Map.of("count", 1)))
                .addNode("join", node_async(state -> Map.of()))
                .addEdge(START, "init5")
                .addEdge("init5", "plus100")
                .addEdge("init5", "plusOne")
                .addEdge("plus100", "join")
                .addEdge("plusOne", "join")
                .addEdge("join", END)
                .compile();

        // 5 + 100 + 1; the parallel node's full-state result must not be merged as a delta
        assertEquals(106, invokeCount(app));
    }

    @Test
    void removalMarkerPropagatesFromParallelSubgraphBranch() throws Exception {
        var subGraph = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("add10", node_async(state -> Map.of("count", 10)))
                .addNode("remove", node_async(state -> Map.of("count", MARK_FOR_REMOVAL)))
                .addEdge(START, "add10")
                .addEdge("add10", "remove")
                .addEdge("remove", END)
                .compile();

        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("init5", node_async(state -> Map.of("count", 5)))
                .addNode("sub", subGraph)
                .addNode("plusOne", node_async(state -> Map.of("count", 1)))
                .addNode("join", node_async(state -> Map.of()))
                .addEdge(START, "init5")
                .addEdge("init5", "sub")
                .addEdge("init5", "plusOne")
                .addEdge("sub", "join")
                .addEdge("plusOne", "join")
                .addEdge("join", END)
                .compile();

        // subgraph: 5 -> 15 -> removed; plusOne's +1 then applies on the default (0) => 1
        assertEquals(1, invokeCount(app));
    }
}
