package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

public class Issue306Test {
    private static final String USER_QUERY = "user_query";
    private static final String INTERNAL_DATA = "internal_data";
    private static final String SECRET = "secret";
    private static final String MESSAGES = "messages";
    private static final String CHILD_RESULT = "child_result";
    private static final String LLM_VIEW = "llm_view";
    private static final String CHILD_SCRATCH = "child_scratch";

    static final Map<String, Channel<?>> PARENT_SCHEMA = Map.of(
            USER_QUERY, Channels.base(() -> ""),
            INTERNAL_DATA, Channels.base(() -> ""),
            SECRET, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new),
            CHILD_RESULT, Channels.base(() -> ""),
            LLM_VIEW, Channels.base(() -> List.of())
    );

    /** Intentionally omits SECRET and INTERNAL_DATA. */
    static final Map<String, Channel<?>> CHILD_SCHEMA = Map.of(
            USER_QUERY, Channels.base(() -> ""),
            CHILD_SCRATCH, Channels.base(() -> ""),
            CHILD_RESULT, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new),
            LLM_VIEW, Channels.base(() -> List.of())
    );

    static Map<String, Object> parentInput() {
        return Map.of(
                USER_QUERY, "what is the weather",
                INTERNAL_DATA, "parent-internal",
                SECRET, "fake-secret",
                MESSAGES, List.of("parent-msg")
        );
    }

    static class BoundaryState extends AgentState {
        BoundaryState(Map<String, Object> data) {
            super(data);
        }
    }

    record Probe(List<Map<String, Object>> n1, List<Map<String, Object>> n2) {
        Probe() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }
    static NodeHook.BeforeCall<BoundaryState> removeKeys(String... keys) {
        return (nodeId, state, config) -> {
            var delta = new LinkedHashMap<String, Object>();
            for (String key : keys) {
                delta.put(key, AgentState.MARK_FOR_REMOVAL);
            }
            return completedFuture(delta);
        };
    }

    static NodeHook.AfterCall<BoundaryState> stripFromLastResult(String key) {
        return (nodeId, state, config, lastResult) -> {
            var copy = new LinkedHashMap<>(lastResult);
            copy.put(key, AgentState.MARK_FOR_REMOVAL);
            return completedFuture(copy);
        };
    }

    /** Exact snippet from issue #306 (2-arg mergeMap rejects duplicate keys). */
    static NodeHook.AfterCall<BoundaryState> maintainerMergeMapSnippet(String key) {
        return (nodeId, state, config, lastResult) ->
                completedFuture(mergeMap(lastResult, Map.of(key, AgentState.MARK_FOR_REMOVAL)));
    }

    private CompiledGraph<BoundaryState> childGraph(Probe probe, CompileConfig childCompile) throws GraphStateException {
        var g = new StateGraph<>(CHILD_SCHEMA, BoundaryState::new)
                .addNode("n1", node_async(state -> {
                    probe.n1.add(new LinkedHashMap<>(state.data()));
                    return Map.of(CHILD_SCRATCH, "scratch-from-n1", MESSAGES, "child-n1");
                }))
                .addNode("n2", node_async(state -> {
                    probe.n2.add(new LinkedHashMap<>(state.data()));
                    var visibleToLlm = List.copyOf(state.<List<?>>value(MESSAGES).orElse(List.of()));
                    return Map.of(CHILD_RESULT, "ok", LLM_VIEW, visibleToLlm);
                }))
                .addEdge(START, "n1").addEdge("n1", "n2").addEdge("n2", END);
        return childCompile == null ? g.compile() : g.compile(childCompile);
    }

    private CompiledGraph<BoundaryState> parentGraph(
            CompiledGraph<BoundaryState> child,
            CompileConfig parentCompile,
            NodeHook.BeforeCall<BoundaryState> parentBeforeOnChild,
            NodeHook.AfterCall<BoundaryState> parentAfterOnChild) throws GraphStateException {

        var g = new StateGraph<>(PARENT_SCHEMA, BoundaryState::new)
                .addNode("before", node_async(state -> Map.of()))
                .addNode("child", child)   // nested CompiledGraph
                .addNode("after", node_async(state -> Map.of()))
                .addEdge(START, "before")
                .addEdge("before", "child")
                .addEdge("child", "after")
                .addEdge("after", END);

        if (parentBeforeOnChild != null) {
            g.addBeforeCallNodeHook("child", parentBeforeOnChild);
        }
        if (parentAfterOnChild != null) {
            g.addAfterCallNodeHook("child", parentAfterOnChild);
        }
        return parentCompile == null ? g.compile() : g.compile(parentCompile);
    }


    private BoundaryState runParent(CompiledGraph<BoundaryState> parent) throws GraphStateException {
        return parent.invoke( GraphInput.args(parentInput()), RunnableConfig.empty() )
                .orElseThrow();
    }

    @Test
    void experimentA_noHookBaseline() throws Exception {
        final var probe = new Probe();
        final var parent = parentGraph( childGraph(probe, null),
                null,
                null,
                null);
        var parentFinal = runParent(parent);

        var n1 = probe.n1.get(0);
        var n2 = probe.n2.get(0);

        // Child schema does not declare secret / internal_data, but they are still visible.
        assertEquals("fake-secret", n1.get(SECRET));
        assertEquals("parent-internal", n1.get(INTERNAL_DATA));
        assertEquals("what is the weather", n1.get(USER_QUERY));
        assertEquals("fake-secret", n2.get(SECRET));

        assertEquals("scratch-from-n1", n2.get(CHILD_SCRATCH));
        assertEquals("ok", parentFinal.value(CHILD_RESULT).orElseThrow());
        // Child-only field leaks into parent even though parent schema never declared it.
        assertEquals("scratch-from-n1", parentFinal.value(CHILD_SCRATCH).orElseThrow());
        assertEquals("fake-secret", parentFinal.value(SECRET).orElseThrow());
        assertEquals("parent-internal", parentFinal.value(INTERNAL_DATA).orElseThrow());
    }

    @Test
    void experimentF_stateSecretDoesNotAutomaticallyEnterLlmView() throws Exception {
        final var probe = new Probe();
        final var parentFinal = runParent( parentGraph(childGraph(probe, null),
                null,
                null,
                null));

        final var llmView = parentFinal.<List<Object>>value(LLM_VIEW).orElseThrow();

        assertTrue(llmView.contains("parent-msg"));
        assertTrue(llmView.contains("child-n1"));
        assertFalse(llmView.contains("fake-secret"),
                "secret lives on State but is not copied into the mock LLM request view");
        assertEquals("fake-secret", probe.n2.get(0).get(SECRET));
    }

    @Test
    void experimentC2_maintainerMergeMapSnippetThrowsOnExistingKey() {
        var snippet = maintainerMergeMapSnippet(CHILD_SCRATCH);
        var lastResult = Map.<String, Object>of(
                CHILD_SCRATCH, "scratch-from-n1",
                CHILD_RESULT, "ok");
        var ex = assertThrows(
                IllegalStateException.class,
                () -> snippet.applyAfter("child",
                        new BoundaryState(Map.of()),
                        RunnableConfig.builder().build(),
                        lastResult));
        assertTrue(ex.getMessage().contains("Duplicate key"));
    }

    @Test
    void experimentC2_parentAfterCallStripsChildScratch() throws Exception {
        final var probe = new Probe();
        final var parent = parentGraph(
                childGraph(probe, null),
                null,
                null,
                stripFromLastResult(CHILD_SCRATCH));
        final var parentFinal = runParent(parent);

        assertEquals("scratch-from-n1", probe.n2.get(0).get(CHILD_SCRATCH));
        assertTrue(parentFinal.value(CHILD_SCRATCH).isEmpty(),
                "AfterCall on parent subgraph node can strip child field");
        assertEquals("ok", parentFinal.value(CHILD_RESULT).orElseThrow());
        assertEquals("fake-secret", parentFinal.value(SECRET).orElseThrow());
    }

    @Test
    void experimentB3_stackedParentBeforeCallsOnlyLastDeltaMerged() throws Exception {
        var probe = new Probe();
        var g = new StateGraph<>(PARENT_SCHEMA, BoundaryState::new)
                .addBeforeCallNodeHook("child", removeKeys(SECRET))
                .addBeforeCallNodeHook("child", removeKeys(INTERNAL_DATA))
                .addNode("before", node_async(state -> Map.of()))
                .addNode("child", childGraph(probe, null))
                .addNode("after", node_async(state -> Map.of()))
                .addEdge(START, "before").addEdge("before", "child")
                .addEdge("child", "after").addEdge("after", END);

        var parentFinal = g.compile().invoke( GraphInput.args(parentInput()), RunnableConfig.empty()).orElseThrow();
        var n1 = probe.n1.get(0);

        // last-executed stacked BeforeCall (SECRET) is applied;
        // earlier-executed stacked BeforeCall delta is discarded
        assertFalse(n1.containsKey(SECRET));
        assertTrue(n1.containsKey(INTERNAL_DATA));
        assertTrue(parentFinal.value(SECRET).isEmpty());
        assertEquals("parent-internal", parentFinal.value(INTERNAL_DATA).orElseThrow());
    }

    @Test
    void experimentB3b_singleBeforeCallRemovingBothKeysWorks() throws Exception {
        var probe = new Probe();
        var parent = parentGraph(
                childGraph(probe, null),
                null,
                removeKeys(SECRET, INTERNAL_DATA),
                null);
        var n1 = runParent(parent);
        assertFalse(probe.n1.get(0).containsKey(SECRET));
        assertTrue(n1.value(SECRET).isEmpty());
        assertTrue(n1.value(INTERNAL_DATA).isEmpty());
    }

    @Test
    void experimentB2_childFirstNodeBeforeCallOnly() throws Exception {
        var probe = new Probe();
        var child = new StateGraph<>(CHILD_SCHEMA, BoundaryState::new)
                .addBeforeCallNodeHook("n1", removeKeys(SECRET, INTERNAL_DATA))
                .addNode("n1", node_async(state -> {
                    probe.n1.add(new LinkedHashMap<>(state.data()));
                    return Map.of(CHILD_SCRATCH, "scratch-from-n1");
                }))
                .addNode("n2", node_async(state -> {
                    probe.n2.add(new LinkedHashMap<>(state.data()));
                    return Map.of(CHILD_RESULT, "ok");
                }))
                .addEdge(START, "n1").addEdge("n1", "n2").addEdge("n2", END)
                .compile();

        var parent = parentGraph(child, null, null, null);
        var parentFinal = runParent(parent);

        assertFalse(probe.n1.get(0).containsKey(SECRET));
        assertFalse(probe.n2.get(0).containsKey(SECRET));
        // Child done-map lacks secret; parent merge is union → parent secret remains.
        assertTrue(parentFinal.value(SECRET).isEmpty());
        //assertEquals("fake-secret", parentFinal.value(SECRET).orElseThrow());
        assertEquals("ok", parentFinal.value(CHILD_RESULT).orElseThrow());
    }

    @Test
    void experimentB1_parentBeforeCallRemovesSecret() throws Exception {
        var probe = new Probe();
        var parent = parentGraph(
                childGraph(probe, null),
                null,
                removeKeys(SECRET, INTERNAL_DATA),
                null);
        var parentFinal = runParent(parent);

        assertFalse(probe.n1.get(0).containsKey(SECRET));
        assertFalse(probe.n1.get(0).containsKey(INTERNAL_DATA));
        assertEquals("what is the weather", probe.n1.get(0).get(USER_QUERY));

        // Parent live state was updated by BeforeCall before subgraph ran.
        assertTrue(parentFinal.value(SECRET).isEmpty());
        assertTrue(parentFinal.value(INTERNAL_DATA).isEmpty());
        assertEquals("ok", parentFinal.value(CHILD_RESULT).orElseThrow());
    }

    @Test
    void experimentE_interruptResumeDoesNotReinjectSecretWhenParentBeforeFiltered() throws Exception {
        var saver = new MemorySaver();
        var probe = new Probe();
        var childCompile = CompileConfig.builder()
                .checkpointSaver(saver).interruptAfter("n1").build();
        var parentCompile = CompileConfig.builder()
                .checkpointSaver(saver).build();

        var parent = parentGraph(
                childGraph(probe, childCompile),
                parentCompile,
                removeKeys(SECRET, INTERNAL_DATA),
                null);

        var config = RunnableConfig.builder().threadId("boundary-e").build();
        var first = parent.stream(GraphInput.args(parentInput()), config)
                                .toCompletableFuture()
                                .thenApply(GraphResult::from)
                                .join();

        assertTrue(first.isInterruptionMetadata());
        assertFalse(probe.n1.get(0).containsKey(SECRET));

        var resumed = parent.stream(GraphInput.resume(), config)
                .toCompletableFuture()
                .thenApply(GraphResult::from)
                .join();
        assertTrue(resumed.isStateData() || resumed.isCheckpointSaverTag());

        assertFalse(probe.n2.get(0).containsKey(SECRET));
        assertNull(resumed.asStateData().get(SECRET));
    }

    @Test
    void experimentE2_resumeWithoutParentFilterKeepsSecretInChildCheckpoint() throws Exception {
        var saver = new MemorySaver();
        var probe = new Probe();
        var childCompile = CompileConfig.builder()
                .checkpointSaver(saver).interruptAfter("n1").build();
        var parentCompile = CompileConfig.builder()
                .checkpointSaver(saver).build();

        var parent = parentGraph(childGraph(probe, childCompile), parentCompile, null, null);
        var config = RunnableConfig.builder().threadId("boundary-e2").build();

        var first = parent.stream(GraphInput.args(parentInput()), config)
                .toCompletableFuture()
                .thenApply(GraphResult::from)
                .join();
        assertTrue(first.isInterruptionMetadata());
        assertEquals("fake-secret", probe.n1.get(0).get(SECRET));

        parent.stream(GraphInput.resume(), config)
                .toCompletableFuture()
                .join();
        assertEquals("fake-secret", probe.n2.get(0).get(SECRET));
    }

    @Test
    void experimentC3_childLastNodeAfterCallStripsScratchFromDoneMap() throws Exception {
        var probe = new Probe();
        var child = new StateGraph<>(CHILD_SCHEMA, BoundaryState::new)
                .addAfterCallNodeHook("n2", stripFromLastResult(CHILD_SCRATCH))
                .addNode("n1", node_async(state -> {
                    probe.n1.add(new LinkedHashMap<>(state.data()));
                    return Map.of(CHILD_SCRATCH, "scratch-from-n1");
                }))
                .addNode("n2", node_async(state -> {
                    probe.n2.add(new LinkedHashMap<>(state.data()));
                    return Map.of(CHILD_RESULT, "ok");
                }))
                .addEdge(START, "n1").addEdge("n1", "n2").addEdge("n2", END)
                .compile();

        var parentFinal = parentGraph(child, null, null, null)
                .invoke( GraphInput.args(parentInput()), RunnableConfig.empty())
                .orElseThrow();

        assertEquals("scratch-from-n1", probe.n2.get(0).get(CHILD_SCRATCH),
                "n2 still sees scratch written by n1; AfterCall runs after n2 action");
        assertTrue(parentFinal.value(CHILD_SCRATCH).isEmpty(),
                "stripping on last child node removes key from done-map so parent never receives it");
        assertEquals("ok", parentFinal.value(CHILD_RESULT).orElseThrow());
    }

    @Test
    void experimentD_appenderMergesParentAndChildMessages() throws Exception {
        var probe = new Probe();
        var parentFinal = parentGraph(childGraph(probe, null), null, null, null)
                .invoke( GraphInput.args(parentInput()), RunnableConfig.empty())
                .orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) parentFinal.value(MESSAGES).orElseThrow();

        assertEquals(List.of("parent-msg", "child-n1"), messages);
        assertEquals("fake-secret", parentFinal.value(SECRET).orElseThrow());
    }
}
