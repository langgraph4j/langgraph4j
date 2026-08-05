package org.bsc.langgraph4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live LLM coverage for Plan-and-Execute (related to
 * <a href="https://github.com/langgraph4j/langgraph4j/issues/8">#8</a>).
 *
 * Skipped unless {@code OPENAI_API_KEY} is set. Excluded from default Surefire runs
 * by the {@code *ITest} pattern in {@code how-tos/pom.xml}.
 */
public class PlanAndExecuteITest {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PlanAndExecuteITest.class);

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

    static class SearchTools {
        @Tool("Search for information. Use for weather, cities, or factual lookups.")
        String search(@P("search query") String query) {
            var q = query == null ? "" : query.toLowerCase(Locale.ROOT);
            log.info("tool search: {}", query);
            if (q.contains("weather") || q.contains("sf") || q.contains("san francisco")) {
                return "San Francisco: 60F and foggy.";
            }
            if (q.contains("nyc") || q.contains("new york")) {
                return "New York: 55F and cloudy.";
            }
            return "No structured result for: " + query;
        }
    }

    static class Plan {
        @Description("different steps to follow, should be in sorted order")
        public List<String> steps;
    }

    static class Act {
        @Description("Remaining steps if more tool work is needed; empty when responding to the user")
        public List<String> plan;

        @Description("Final answer for the user when no more steps are required; blank otherwise")
        public String response;

        boolean isResponse() {
            return response != null && !response.isBlank();
        }
    }

    interface PlannerService {
        @SystemMessage("For the given objective, come up with a simple step by step plan. "
                + "This plan should involve individual tasks that if executed correctly will yield the correct answer. "
                + "Do not add any superfluous steps. The result of the final step should be the final answer. "
                + "Make sure that each step has all the information needed - do not skip steps.")
        Plan plan(@UserMessage String objective);
    }

    interface ReplanService {
        @SystemMessage("You update plans for a plan-and-execute agent. "
                + "Only keep steps that still NEED to be done. "
                + "If you can answer the user now, set response and leave plan empty.")
        Act replan(@UserMessage String details);
    }

    interface StepAgentService {
        @SystemMessage("You are a helpful assistant that executes a single plan step. "
                + "Use tools when needed. Return a concise result for that step only.")
        String execute(@UserMessage String step);
    }

    static String formatPastSteps(List<PastStep> past) {
        return past.stream()
                .map(ps -> ps.step() + " => " + ps.result())
                .collect(Collectors.joining("\n"));
    }

    static class LlmPlannerNode implements NodeAction<PlanExecuteState> {
        private final PlannerService service;

        LlmPlannerNode(ChatModel model) {
            this.service = AiServices.create(PlannerService.class, model);
        }

        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            var plan = service.plan(state.input());
            var steps = plan.steps == null ? List.<String>of() : new ArrayList<>(plan.steps);
            log.info("llm planner steps: {}", steps);
            return Map.of(PlanExecuteState.PLAN, steps);
        }
    }

    static class LlmAgentNode implements NodeAction<PlanExecuteState> {
        private final StepAgentService service;

        LlmAgentNode(ChatModel model) {
            this.service = AiServices.builder(StepAgentService.class)
                    .chatModel(model)
                    .tools(new SearchTools())
                    .build();
        }

        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            var plan = state.plan();
            if (plan.isEmpty()) {
                return Map.of();
            }
            var step = plan.get(0);
            log.info("llm agent step: {}", step);
            return Map.of(PlanExecuteState.PAST_STEPS, new PastStep(step, service.execute(step)));
        }
    }

    static class LlmReplanNode implements NodeAction<PlanExecuteState> {
        private final ReplanService service;

        LlmReplanNode(ChatModel model) {
            this.service = AiServices.builder(ReplanService.class)
                    .chatModel(model)
                    .build();
        }

        @Override
        public Map<String, Object> apply(PlanExecuteState state) {
            var details = "Objective: " + state.input()
                    + "\nCurrent remaining plan:\n" + String.join("\n", state.plan())
                    + "\nCompleted steps:\n" + formatPastSteps(state.pastSteps());
            var act = service.replan(details);
            if (act != null && act.isResponse()) {
                return Map.of(
                        PlanExecuteState.PLAN, new ArrayList<String>(),
                        PlanExecuteState.RESPONSE, act.response
                );
            }
            var next = (act == null || act.plan == null)
                    ? new ArrayList<String>()
                    : new ArrayList<>(act.plan);
            if (next.isEmpty()) {
                return Map.of(
                        PlanExecuteState.PLAN, next,
                        PlanExecuteState.RESPONSE,
                        "Final answer based on executed steps:\n" + formatPastSteps(state.pastSteps())
                );
            }
            return Map.of(PlanExecuteState.PLAN, next);
        }
    }

    @Test
    void llmPlanAndExecuteProducesFinalResponse() throws Exception {
        var openAiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(openAiKey != null && !openAiKey.isBlank(),
                "OPENAI_API_KEY is required for PlanAndExecuteITest");

        var chatModel = OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName("gpt-4o-mini")
                .timeout(Duration.ofMinutes(2))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .maxTokens(2000)
                .build();

        EdgeAction<PlanExecuteState> shouldContinue = state ->
                state.hasResponse() ? "respond" : "continue";

        var app = new StateGraph<>(PlanExecuteState.SCHEMA, PlanExecuteState::new)
                .addNode("planner", node_async(new LlmPlannerNode(chatModel)))
                .addNode("agent", node_async(new LlmAgentNode(chatModel)))
                .addNode("replan", node_async(new LlmReplanNode(chatModel)))
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
                RunnableConfig.empty());

        assertTrue(result.isPresent());
        var state = result.get();
        assertTrue(state.hasResponse());
        assertFalse(state.pastSteps().isEmpty());
        log.info("final response: {}", state.response().orElse(""));
    }
}
