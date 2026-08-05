package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skeleton coverage for the Plan-and-Execute how-to (related to
 * <a href="https://github.com/langgraph4j/langgraph4j/issues/8">#8</a>).
 *
 * Uses deterministic stub nodes so CI does not need an LLM API key.
 */
public class PlanAndExecuteStubTest {

    record PastStep(String step, String result) implements java.io.Serializable {}

    static class PlanExecuteState extends AgentState {

        static final String INPUT = "input";
        static final String PLAN = "plan";
        static final String PAST_STEPS = "past_steps";
        static final String RESPONSE = "response";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                INPUT, Channels.base(() -> ""),
                PLAN, Channels.base(ArrayList::new),
                PAST_STEPS, Channels.appender(ArrayList::new),
                RESPONSE, Channels.base(() -> "")
        );

        PlanExecuteState(Map<String, Object> initData) {
            super(initData);
        }

        String input() {
            return this.<String>value(INPUT).orElse("");
        }

        @SuppressWarnings("unchecked")
        List<String> plan() {
            return this.<List<String>>value(PLAN).orElse(List.of());
        }

        @SuppressWarnings("unchecked")
        List<PastStep> pastSteps() {
            return this.<List<PastStep>>value(PAST_STEPS).orElse(List.of());
        }

        Optional<String> response() {
            return value(RESPONSE);
        }

        boolean hasResponse() {
            return response().filter(r -> r != null && !r.isBlank()).isPresent();
        }
    }

    static class StubSearchTool {
        String search(String query) {
            var q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            if (q.contains("san francisco") || q.contains("weather")) {
                return "San Francisco: 60F and foggy.";
            }
            return "No structured result for: " + query;
        }
    }

    static class PlannerNode implements NodeAction<PlanExecuteState> {
        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            List<String> plan = List.of(
                    "Gather facts relevant to: " + state.input(),
                    "Synthesize a final answer for: " + state.input()
            );
            return Map.of(PlanExecuteState.PLAN, new ArrayList<>(plan));
        }
    }

    static class AgentNode implements NodeAction<PlanExecuteState> {
        private final StubSearchTool tool;

        AgentNode(StubSearchTool tool) {
            this.tool = tool;
        }

        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            var plan = state.plan();
            if (plan.isEmpty()) {
                return Map.of();
            }
            var step = plan.get(0);
            return Map.of(PlanExecuteState.PAST_STEPS, new PastStep(step, tool.search(step)));
        }
    }

    static class ReplanNode implements NodeAction<PlanExecuteState> {
        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            var plan = new ArrayList<>(state.plan());
            var past = state.pastSteps();

            if (!plan.isEmpty()) {
                plan.remove(0);
            }

            if (plan.isEmpty()) {
                var sb = new StringBuilder("Final answer based on executed steps:\n");
                for (var ps : past) {
                    sb.append("- ").append(ps.step()).append(" => ").append(ps.result()).append('\n');
                }
                return Map.of(
                        PlanExecuteState.PLAN, plan,
                        PlanExecuteState.RESPONSE, sb.toString().trim()
                );
            }

            return Map.of(PlanExecuteState.PLAN, plan);
        }
    }

    @Test
    void stubPlanAndExecuteProducesFinalResponse() throws Exception {
        EdgeAction<PlanExecuteState> shouldContinue = state ->
                state.hasResponse() ? "respond" : "continue";

        var app = new StateGraph<>(PlanExecuteState.SCHEMA, PlanExecuteState::new)
                .addNode("planner", node_async(new PlannerNode()))
                .addNode("agent", node_async(new AgentNode(new StubSearchTool())))
                .addNode("replan", node_async(new ReplanNode()))
                .addEdge(START, "planner")
                .addEdge("planner", "agent")
                .addEdge("agent", "replan")
                .addConditionalEdges("replan", edge_async(shouldContinue), Map.of(
                        "continue", "agent",
                        "respond", END
                ))
                .compile();

        var result = app.invoke(GraphInput.args(Map.of(
                PlanExecuteState.INPUT, "What is the weather in San Francisco?")),
                RunnableConfig.empty()
        );

        assertTrue(result.isPresent());
        var state = result.get();
        assertTrue(state.hasResponse());
        assertFalse(state.pastSteps().isEmpty());
        assertTrue(state.response().orElse("").contains("San Francisco"));
    }
}
