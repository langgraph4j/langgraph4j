package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.TryConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for streaming branches inside a {@link ParallelNode}.
 *
 * <p>A streaming branch (e.g. a node returning a {@code StreamingChatGenerator})
 * emits chunk outputs whose state is the branch's <em>starting</em> state, plus a
 * final result value (the {@code Data.done(...)} payload, e.g. the
 * {@code mapResult} output). {@code ParallelNode} used to merge every chunk's
 * full state snapshot into the branch result, turning it into a full-state map
 * that {@code apply()} then merged <em>as a delta</em>. That caused two defects:
 *
 * <ul>
 *   <li>sibling branches' writes were reverted to the parent values whenever the
 *       streaming branch was merged after them (order-dependent clobbering);</li>
 *   <li>with an allow-duplicates appender channel, the pre-existing list content
 *       was appended again (duplication).</li>
 * </ul>
 *
 * <p>The serial path ({@code CompiledGraph.embedGenerator}) only merges the
 * generator's final result value; the parallel path now does the same.
 */
public class ParallelStreamingBranchTest {

    static class State extends AgentState {
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                "a", Channels.base(() -> ""),
                "b", Channels.base(() -> ""));
        State(Map<String, Object> initData) { super(initData); }
    }

    static class AppenderState extends AgentState {
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                "messages", Channels.appenderWithDuplicate(ArrayList::new));
        AppenderState(Map<String, Object> initData) { super(initData); }
    }

    /** A streaming node action like StreamingChatGenerator: chunks carry the starting state,
     * the business update is only in the final result value. */
    private static <S extends AgentState> AsyncNodeActionWithConfig<S> streamingAction(String key, Object value) {
        return (state, config) -> {
            final var generator = AsyncGeneratorFlow.<NodeOutput<S>>create(TryConsumer.Try($1 -> {
                for (String chunk : List.of("chunk-1", "chunk-2")) {
                    $1.dispatchSync(AsyncGenerator.Data.of(new StreamingOutput<>(chunk, "streaming", state, null)));
                }
                $1.dispatchSync(AsyncGenerator.Data.done(Map.of(key, value)));
            }));
            return completedFuture(Map.of("content", generator));
        };
    }

    private static <S extends AgentState> AsyncNodeActionWithConfig<S> parallelAction(
            ParallelNode<S> parallelNode) throws Exception {
        return parallelNode.actionFactory().apply(CompileConfig.builder().build());
    }

    @Test
    void streamingBranchDoesNotClobberSiblingBranchWrites() throws Exception {
        AsyncNodeActionWithConfig<State> normalAction =
                (state, config) -> completedFuture(Map.of("a", "from-a"));

        var actions = List.<AsyncNodeActionWithConfig<State>>of(
                normalAction,
                streamingAction("b", "from-stream"));

        var result = parallelAction(new ParallelNode<>("p", actions, State.SCHEMA))
                .apply(new State(Map.of("a", "orig-a", "b", "orig-b")),
                        RunnableConfig.builder().build())
                .join();

        assertEquals("from-a", result.get("a"), "sibling branch write must survive the streaming branch merge");
        assertEquals("from-stream", result.get("b"), "streaming branch final result must be applied");
    }

    @Test
    void streamingBranchDoesNotClobberSiblingBranchWritesReversedOrder() throws Exception {
        AsyncNodeActionWithConfig<State> normalAction =
                (state, config) -> completedFuture(Map.of("a", "from-a"));

        // streaming branch first: must still not clobber, and its own update must survive
        var actions = List.<AsyncNodeActionWithConfig<State>>of(
                streamingAction("b", "from-stream"),
                normalAction);

        var result = parallelAction(new ParallelNode<>("p", actions, State.SCHEMA))
                .apply(new State(Map.of("a", "orig-a", "b", "orig-b")),
                        RunnableConfig.builder().build())
                .join();

        assertEquals("from-a", result.get("a"));
        assertEquals("from-stream", result.get("b"));
    }

    @Test
    void streamingBranchDoesNotDuplicateAppenderContent() throws Exception {
        var actions = List.<AsyncNodeActionWithConfig<AppenderState>>of(
                streamingAction("messages", "ai-msg"));

        var result = parallelAction(new ParallelNode<>("p", actions, AppenderState.SCHEMA))
                .apply(new AppenderState(Map.of("messages", new ArrayList<>(List.of("m0")))),
                        RunnableConfig.builder().build())
                .join();

        assertEquals(List.of("m0", "ai-msg"), result.get("messages"),
                "appender content must be applied exactly once");
    }

    @Test
    void streamingBranchEndToEnd() throws Exception {
        var app = new StateGraph<>(State.SCHEMA, State::new)
                .addNode("init", node_async(state -> Map.of("a", "orig-a", "b", "orig-b")))
                .addNode("normal", node_async(state -> Map.of("a", "from-a")))
                .addNode("stream", streamingAction("b", "from-stream"))
                .addNode("join", node_async(state -> Map.of()))
                .addEdge(START, "init")
                .addEdge("init", "normal")
                .addEdge("init", "stream")
                .addEdge("normal", "join")
                .addEdge("stream", "join")
                .addEdge("join", END)
                .compile();

        var result = app.invoke(GraphInput.noArgs(),RunnableConfig.empty()).orElseThrow();

        assertEquals("from-a", result.<String>value("a").orElseThrow());
        assertEquals("from-stream", result.<String>value("b").orElseThrow());
    }
}
