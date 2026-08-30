package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic coverage for the Prompt Chaining how-to.
 */
public class PromptChainingStubTest {

    static class JokeState extends AgentState {
        static final String TOPIC = "topic";
        static final String JOKE = "joke";
        static final String IMPROVED_JOKE = "improved_joke";
        static final String FINAL_JOKE = "final_joke";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TOPIC, Channels.base(() -> ""),
                JOKE, Channels.base(() -> ""),
                IMPROVED_JOKE, Channels.base(() -> ""),
                FINAL_JOKE, Channels.base(() -> "")
        );

        JokeState(Map<String, Object> initData) {
            super(initData);
        }

        String topic() {
            return this.<String>value(TOPIC).orElse("");
        }

        String joke() {
            return this.<String>value(JOKE).orElse("");
        }

        String improvedJoke() {
            return this.<String>value(IMPROVED_JOKE).orElse("");
        }

        String finalJoke() {
            return this.<String>value(FINAL_JOKE).orElse("");
        }
    }

    @Test
    void promptChainStopsWhenTheInitialJokeHasAPunchline() throws Exception {
        var result = workflow().invoke(Map.of(JokeState.TOPIC, "pass"))
                .orElseThrow();

        assertTrue(result.joke().contains("?"));
        assertEquals("", result.improvedJoke());
        assertEquals("", result.finalJoke());
    }

    @Test
    void promptChainImprovesAndPolishesJokesWithoutAPunchline() throws Exception {
        var result = workflow().invoke(Map.of(JokeState.TOPIC, "fail"))
                .orElseThrow();

        assertEquals("Cats write code", result.joke());
        assertEquals("Cats write code with purr-fect formatting", result.improvedJoke());
        assertEquals("Cats write code with purr-fect formatting!", result.finalJoke());
    }

    private CompiledGraph<JokeState> workflow() throws GraphStateException {
        NodeAction<JokeState> generateJoke = state -> Map.of(
                JokeState.JOKE,
                state.topic().equals("pass")
                        ? "Why do cats make great programmers? They always avoid cat-astrophic bugs!"
                        : "Cats write code"
        );
        EdgeAction<JokeState> checkPunchline = state ->
                state.joke().contains("?") || state.joke().contains("!") ? "Pass" : "Fail";
        NodeAction<JokeState> improveJoke = state -> Map.of(
                JokeState.IMPROVED_JOKE,
                state.joke() + " with purr-fect formatting"
        );
        NodeAction<JokeState> polishJoke = state -> Map.of(
                JokeState.FINAL_JOKE,
                state.improvedJoke() + "!"
        );

        return new StateGraph<>(JokeState.SCHEMA, JokeState::new)
                .addNode("generate_joke", node_async(generateJoke))
                .addNode("improve_joke", node_async(improveJoke))
                .addNode("polish_joke", node_async(polishJoke))
                .addEdge(START, "generate_joke")
                .addConditionalEdges("generate_joke", edge_async(checkPunchline), Map.of(
                        "Pass", END,
                        "Fail", "improve_joke"
                ))
                .addEdge("improve_joke", "polish_joke")
                .addEdge("polish_joke", END)
                .compile();
    }
}
