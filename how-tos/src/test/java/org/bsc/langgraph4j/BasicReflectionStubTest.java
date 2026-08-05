package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skeleton coverage for the Basic Reflection how-to (related to
 * <a href="https://github.com/langgraph4j/langgraph4j/issues/8">#8</a>).
 */
public class BasicReflectionStubTest {

    static final int MAX_ROUNDS = 2;

    static class ReflectionState extends MessagesState<String> {

        static final Map<String, Channel<?>> SCHEMA = MessagesState.SCHEMA;

        ReflectionState(Map<String, Object> initData) {
            super(initData);
        }

        String topic() {
            var msgs = messages();
            return msgs.isEmpty() ? "" : msgs.get(0);
        }

        long draftCount() {
            return messages().stream().filter(m -> m.startsWith("DRAFT:")).count();
        }

        String lastMessageOrEmpty() {
            return lastMessage().orElse("");
        }
    }

    static class StubGenerateNode implements NodeAction<ReflectionState> {
        @Override
        public Map<String, Object> apply(ReflectionState state) {
            var round = state.draftCount() + 1;
            var critique = state.messages().stream()
                    .filter(m -> m.startsWith("CRITIQUE:"))
                    .reduce((a, b) -> b)
                    .orElse("");
            var draft = critique.isBlank()
                    ? "DRAFT: (round " + round + ") An initial essay about: " + state.topic()
                    : "DRAFT: (round " + round + ") Revised essay about: " + state.topic()
                    + " | addressing: " + critique.substring("CRITIQUE:".length()).trim();
            return Map.of("messages", draft);
        }
    }

    static class StubReflectNode implements NodeAction<ReflectionState> {
        @Override
        public Map<String, Object> apply(ReflectionState state) {
            var draft = state.lastMessageOrEmpty();
            return Map.of("messages",
                    "CRITIQUE: Add a concrete example and a clearer conclusion for: " + draft);
        }
    }

    @Test
    void stubReflectionRunsFixedRounds() throws Exception {
        EdgeAction<ReflectionState> shouldContinue = state ->
                state.draftCount() >= MAX_ROUNDS ? "end" : "continue";

        var app = new StateGraph<>(ReflectionState.SCHEMA, ReflectionState::new)
                .addNode("generate", node_async(new StubGenerateNode()))
                .addNode("reflect", node_async(new StubReflectNode()))
                .addEdge(START, "generate")
                .addEdge("generate", "reflect")
                .addConditionalEdges("reflect", edge_async(shouldContinue), Map.of(
                        "continue", "generate",
                        "end", END
                ))
                .compile();

        var result = app.invoke(GraphInput.args(Map.of(
                "messages", "Write a short essay about the benefits of journaling.")),
                RunnableConfig.empty());

        assertTrue(result.isPresent());
        var state = result.get();
        assertEquals(MAX_ROUNDS, state.draftCount());
        assertTrue(state.messages().stream().anyMatch(m -> m.startsWith("CRITIQUE:")));
        assertTrue(state.messages().size() >= 1 + MAX_ROUNDS * 2);
    }
}
