#!/usr/bin/env python3
"""Generate how-tos/plan-and-execute.ipynb (stub + optional LLM modes)."""

from __future__ import annotations

import json
from pathlib import Path


def md(s: str) -> dict:
    lines = s.strip("\n").split("\n")
    return {"cell_type": "markdown", "metadata": {}, "source": [l + "\n" for l in lines]}


def code(s: str) -> dict:
    s = s.strip("\n") + "\n"
    return {
        "cell_type": "code",
        "execution_count": None,
        "metadata": {},
        "outputs": [],
        "source": [line + "\n" for line in s.split("\n")[:-1]] + (["\n"] if s.endswith("\n\n") else []),
    }


def code_lines(*lines: str) -> dict:
    src = [line + "\n" for line in lines]
    return {
        "cell_type": "code",
        "execution_count": None,
        "metadata": {},
        "outputs": [],
        "source": src,
    }


cells: list[dict] = []

cells.append(
    md(
        """
# Plan-and-Execute

Port of the LangGraph [Plan-and-Execute](https://langchain-ai.github.io/langgraph/tutorials/plan-and-execute/plan-and-execute/) agent pattern to LangGraph4j.

Related to [#8](https://github.com/langgraph4j/langgraph4j/issues/8).

## Agentic Architecture

```
START → planner → agent → replan ──► (continue) → agent
                      ↑               │
                      └───────────────┘
                                      └──► (respond) → END
```

| Node | Role |
|------|------|
| `planner` | Break the user goal into an ordered list of steps |
| `agent` | Execute the **first** remaining step (tool-calling agent) |
| `replan` | Refresh remaining steps **or** emit the final answer |

This notebook has two modes:

1. **Stub** (default) — deterministic nodes, no API key required  
2. **LLM** — LangChain4j `AiServices` planner / tool agent / replanner (needs `OPENAI_API_KEY`)
"""
    )
)

cells.append(
    code_lines(
        'var userHomeDir = System.getProperty("user.home");',
        'var localRespoUrl = "file://" + userHomeDir + "/.m2/repository/";',
        'var langchain4jVersion = "1.9.1";',
        'var langchain4jbeta = "1.9.1-beta17";',
        'var langgraph4jVersion = "1.8.22";',
    )
)

cells.append(md("Remove installed package from Jupyter cache"))
cells.append(
    code_lines(
        "%%bash ",
        "rm -rf \\{userHomeDir}/Library/Jupyter/kernels/rapaio-jupyter-kernel/mima_cache/org/bsc/langgraph4j",
    )
)

cells.append(md("Add local Maven repo and resolve dependencies"))
cells.append(
    code_lines(
        "%dependency /add-repo local \\{localRespoUrl} release|never snapshot|always",
        "// %dependency /list-repos",
        "%dependency /add org.slf4j:slf4j-jdk14:2.0.9",
        "%dependency /add org.bsc.langgraph4j:langgraph4j-core:\\{langgraph4jVersion}",
        "%dependency /add org.bsc.langgraph4j:langgraph4j-langchain4j:\\{langgraph4jVersion}",
        "%dependency /add dev.langchain4j:langchain4j:\\{langchain4jVersion}",
        "%dependency /add dev.langchain4j:langchain4j-open-ai:\\{langchain4jVersion}",
        "%dependency /add net.sourceforge.plantuml:plantuml-mit:1.2024.8",
        "",
        "%dependency /resolve",
    )
)

cells.append(md("**Initialize Logger**"))
cells.append(
    code_lines(
        'try( var file = new java.io.FileInputStream("./logging.properties")) {',
        "    java.util.logging.LogManager.getLogManager().readConfiguration( file );",
        "}",
        "",
        'var log = org.slf4j.LoggerFactory.getLogger("PlanAndExecute");',
    )
)

cells.append(md("**Utility to render graph representation in PlantUML**"))
cells.append(
    code_lines(
        "import net.sourceforge.plantuml.SourceStringReader;",
        "import net.sourceforge.plantuml.FileFormatOption;",
        "import net.sourceforge.plantuml.FileFormat;",
        "",
        "java.awt.Image plantUML2PNG( String code ) throws IOException { ",
        "    var reader = new SourceStringReader(code);",
        "",
        "    try(var imageOutStream = new java.io.ByteArrayOutputStream()) {",
        "",
        "        var description = reader.outputImage( imageOutStream, 0, new FileFormatOption(FileFormat.PNG));",
        "",
        "        var imageInStream = new java.io.ByteArrayInputStream(  imageOutStream.toByteArray() );",
        "",
        "        return javax.imageio.ImageIO.read( imageInStream );",
        "",
        "    }",
        "}",
    )
)

cells.append(
    md(
        """
## 1. Define the State

* `input` — user objective  
* `plan` — remaining steps (replaced on each planner/replan update)  
* `past_steps` — completed `(step, result)` pairs (appended)  
* `response` — final answer when the loop ends
"""
    )
)

cells.append(
    code_lines(
        "import org.bsc.langgraph4j.state.AgentState;",
        "import org.bsc.langgraph4j.state.Channel;",
        "import org.bsc.langgraph4j.state.Channels;",
        "",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "import java.util.Map;",
        "import java.util.Optional;",
        "",
        "record PastStep(String step, String result) implements java.io.Serializable {}",
        "",
        "class PlanExecuteState extends AgentState {",
        "",
        '    public static final String INPUT = "input";',
        '    public static final String PLAN = "plan";',
        '    public static final String PAST_STEPS = "past_steps";',
        '    public static final String RESPONSE = "response";',
        "",
        "    public static final Map<String, Channel<?>> SCHEMA = Map.of(",
        '            INPUT, Channels.base(() -> ""),',
        "            PLAN, Channels.base(ArrayList::new),",
        "            PAST_STEPS, Channels.appender(ArrayList::new),",
        '            RESPONSE, Channels.base(() -> "")',
        "    );",
        "",
        "    public PlanExecuteState(Map<String, Object> initData) {",
        "        super(initData);",
        "    }",
        "",
        "    public String input() {",
        "        return this.<String>value(INPUT).orElse(\"\");",
        "    }",
        "",
        "    @SuppressWarnings(\"unchecked\")",
        "    public List<String> plan() {",
        "        return this.<List<String>>value(PLAN).orElse(List.of());",
        "    }",
        "",
        "    @SuppressWarnings(\"unchecked\")",
        "    public List<PastStep> pastSteps() {",
        "        return this.<List<PastStep>>value(PAST_STEPS).orElse(List.of());",
        "    }",
        "",
        "    public Optional<String> response() {",
        "        return this.value(RESPONSE);",
        "    }",
        "",
        "    public boolean hasResponse() {",
        "        return response().filter(r -> r != null && !r.isBlank()).isPresent();",
        "    }",
        "}",
    )
)

cells.append(
    md(
        """
## 2. Shared helpers

Routing edge + a small search tool used by both stub and LLM agent modes.
"""
    )
)

cells.append(
    code_lines(
        "import org.bsc.langgraph4j.action.EdgeAction;",
        "import dev.langchain4j.agent.tool.P;",
        "import dev.langchain4j.agent.tool.Tool;",
        "",
        "import java.util.Locale;",
        "import java.util.stream.Collectors;",
        "",
        "EdgeAction<PlanExecuteState> shouldContinue = state ->",
        '        state.hasResponse() ? "respond" : "continue";',
        "",
        "class SearchTools {",
        "",
        '    @Tool("Search for information. Use for weather, cities, or factual lookups.")',
        '    String search(@P("search query") String query) {',
        '        var q = query == null ? "" : query.toLowerCase(Locale.ROOT);',
        '        log.info("tool search: {}", query);',
        '        if (q.contains("weather") || q.contains("sf") || q.contains("san francisco")) {',
        '            return "San Francisco: 60F and foggy.";',
        "        }",
        '        if (q.contains("nyc") || q.contains("new york")) {',
        '            return "New York: 55F and cloudy.";',
        "        }",
        '        return "No structured result for: " + query;',
        "    }",
        "}",
        "",
        "String formatPastSteps(List<PastStep> past) {",
        "    return past.stream()",
        '            .map(ps -> ps.step() + " => " + ps.result())',
        '            .collect(Collectors.joining("\\n"));',
        "}",
    )
)

cells.append(
    md(
        """
## 3. Stub mode (no API key)

Deterministic planner / agent / replan — useful to understand the graph wiring and to run CI/offline.
"""
    )
)

cells.append(
    code_lines(
        "import org.bsc.langgraph4j.action.NodeAction;",
        "",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "class StubPlannerNode implements NodeAction<PlanExecuteState> {",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        "        var goal = state.input();",
        '        log.info("stub planner input: {}", goal);',
        "        List<String> plan = List.of(",
        '                "Gather facts relevant to: " + goal,',
        '                "Synthesize a final answer for: " + goal',
        "        );",
        "        return Map.of(PlanExecuteState.PLAN, new ArrayList<>(plan));",
        "    }",
        "}",
        "",
        "class StubAgentNode implements NodeAction<PlanExecuteState> {",
        "    private final SearchTools tools = new SearchTools();",
        "",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        "        var plan = state.plan();",
        "        if (plan.isEmpty()) {",
        "            return Map.of();",
        "        }",
        "        var step = plan.get(0);",
        '        log.info("stub agent step: {}", step);',
        "        return Map.of(PlanExecuteState.PAST_STEPS, new PastStep(step, tools.search(step)));",
        "    }",
        "}",
        "",
        "class StubReplanNode implements NodeAction<PlanExecuteState> {",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        "        var plan = new ArrayList<>(state.plan());",
        "        var past = state.pastSteps();",
        "        if (!plan.isEmpty()) {",
        "            plan.remove(0);",
        "        }",
        "        if (plan.isEmpty()) {",
        '            var response = "Final answer based on executed steps:\\n" + formatPastSteps(past);',
        '            log.info("stub replan -> respond");',
        "            return Map.of(",
        "                    PlanExecuteState.PLAN, plan,",
        "                    PlanExecuteState.RESPONSE, response",
        "            );",
        "        }",
        '        log.info("stub replan -> continue, remaining={}", plan);',
        "        return Map.of(PlanExecuteState.PLAN, plan);",
        "    }",
        "}",
    )
)

cells.append(md("### Build stub graph"))
cells.append(
    code_lines(
        "import org.bsc.langgraph4j.StateGraph;",
        "import org.bsc.langgraph4j.GraphRepresentation;",
        "import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;",
        "import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;",
        "import static org.bsc.langgraph4j.StateGraph.START;",
        "import static org.bsc.langgraph4j.StateGraph.END;",
        "",
        "var stubWorkflow = new StateGraph<>(PlanExecuteState.SCHEMA, PlanExecuteState::new)",
        '        .addNode("planner", node_async(new StubPlannerNode()))',
        '        .addNode("agent", node_async(new StubAgentNode()))',
        '        .addNode("replan", node_async(new StubReplanNode()))',
        '        .addEdge(START, "planner")',
        '        .addEdge("planner", "agent")',
        '        .addEdge("agent", "replan")',
        '        .addConditionalEdges("replan", edge_async(shouldContinue), Map.of(',
        '                "continue", "agent",',
        '                "respond", END',
        "        ));",
        "",
        "var stubApp = stubWorkflow.compile();",
    )
)

cells.append(md("### Visualize stub graph"))
cells.append(
    code_lines(
        'var representation = stubWorkflow.getGraph( GraphRepresentation.Type.PLANTUML, "plan-and-execute (stub)", false );',
        "display( plantUML2PNG( representation.getContent() ) );",
    )
)

cells.append(md("### Run stub demo"))
cells.append(
    code_lines(
        "var stubInput = Map.<String,Object>of(",
        '        PlanExecuteState.INPUT, "What is the weather in San Francisco?"',
        ");",
        "",
        "for (var event : stubApp.stream(stubInput)) {",
        '    log.info("STUB STEP: {}", event);',
        "}",
    )
)

cells.append(
    md(
        """
## 4. LLM mode (LangChain4j AiServices)

Requires `OPENAI_API_KEY`. Structured outputs:

* `Plan.steps` — ordered remaining work  
* `Act` — either a new `plan` **or** a final `response` (mirrors Python `Union[Plan, Response]`)

The agent node is a tool-calling assistant that executes **only the current step**.
"""
    )
)

cells.append(
    code_lines(
        "import dev.langchain4j.model.chat.ChatModel;",
        "import dev.langchain4j.model.openai.OpenAiChatModel;",
        "import dev.langchain4j.model.output.structured.Description;",
        "import dev.langchain4j.service.AiServices;",
        "import dev.langchain4j.service.SystemMessage;",
        "import dev.langchain4j.service.UserMessage;",
        "",
        "import java.time.Duration;",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        'var openAiKey = System.getenv("OPENAI_API_KEY");',
        "var llmEnabled = openAiKey != null && !openAiKey.isBlank();",
        'log.info("LLM mode enabled: {}", llmEnabled);',
        "",
        "ChatModel chatModel = null;",
        "if (llmEnabled) {",
        "    chatModel = OpenAiChatModel.builder()",
        "            .apiKey(openAiKey)",
        '            .modelName("gpt-4o-mini")',
        "            .timeout(Duration.ofMinutes(2))",
        "            .logRequests(true)",
        "            .logResponses(true)",
        "            .maxRetries(2)",
        "            .temperature(0.0)",
        "            .maxTokens(2000)",
        "            .build();",
        "}",
    )
)

cells.append(
    code_lines(
        "class Plan {",
        '    @Description("different steps to follow, should be in sorted order")',
        "    public List<String> steps;",
        "}",
        "",
        "class Act {",
        '    @Description("Remaining steps if more tool work is needed; empty when responding to the user")',
        "    public List<String> plan;",
        "",
        '    @Description("Final answer for the user when no more steps are required; blank otherwise")',
        "    public String response;",
        "",
        "    boolean isResponse() {",
        "        return response != null && !response.isBlank();",
        "    }",
        "}",
        "",
        "interface PlannerService {",
        '    @SystemMessage("For the given objective, come up with a simple step by step plan. "',
        '            + "This plan should involve individual tasks that if executed correctly will yield the correct answer. "',
        '            + "Do not add any superfluous steps. The result of the final step should be the final answer. "',
        '            + "Make sure that each step has all the information needed - do not skip steps.")',
        "    Plan plan(@UserMessage String objective);",
        "}",
        "",
        "interface ReplanService {",
        '    @SystemMessage("You update plans for a plan-and-execute agent. "',
        '            + "Only keep steps that still NEED to be done. "',
        '            + "If you can answer the user now, set response and leave plan empty.")',
        "    Act replan(@UserMessage String details);",
        "}",
        "",
        "interface StepAgentService {",
        '    @SystemMessage("You are a helpful assistant that executes a single plan step. "',
        '            + "Use tools when needed. Return a concise result for that step only.")',
        "    String execute(@UserMessage String step);",
        "}",
    )
)

cells.append(
    code_lines(
        "class LlmPlannerNode implements NodeAction<PlanExecuteState> {",
        "    private final PlannerService service;",
        "",
        "    LlmPlannerNode(ChatModel model) {",
        "        this.service = AiServices.create(PlannerService.class, model);",
        "    }",
        "",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        "        var plan = service.plan(state.input());",
        "        var steps = plan.steps == null ? List.<String>of() : new ArrayList<>(plan.steps);",
        '        log.info("llm planner steps: {}", steps);',
        "        return Map.of(PlanExecuteState.PLAN, steps);",
        "    }",
        "}",
        "",
        "class LlmAgentNode implements NodeAction<PlanExecuteState> {",
        "    private final StepAgentService service;",
        "",
        "    LlmAgentNode(ChatModel model) {",
        "        this.service = AiServices.builder(StepAgentService.class)",
        "                .chatModel(model)",
        "                .tools(new SearchTools())",
        "                .build();",
        "    }",
        "",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        "        var plan = state.plan();",
        "        if (plan.isEmpty()) {",
        "            return Map.of();",
        "        }",
        "        var step = plan.get(0);",
        '        log.info("llm agent step: {}", step);',
        "        var result = service.execute(step);",
        "        return Map.of(PlanExecuteState.PAST_STEPS, new PastStep(step, result));",
        "    }",
        "}",
        "",
        "class LlmReplanNode implements NodeAction<PlanExecuteState> {",
        "    private final ReplanService service;",
        "",
        "    LlmReplanNode(ChatModel model) {",
        "        this.service = AiServices.builder(ReplanService.class)",
        "                .chatModel(model)",
        "                .build();",
        "    }",
        "",
        "    @Override",
        "    public Map<String, Object> apply(PlanExecuteState state) {",
        '        var details = "Objective: " + state.input()',
        '                + "\\nCurrent remaining plan:\\n" + String.join("\\n", state.plan())',
        '                + "\\nCompleted steps:\\n" + formatPastSteps(state.pastSteps());',
        "        var act = service.replan(details);",
        "        if (act != null && act.isResponse()) {",
        '            log.info("llm replan -> respond");',
        "            return Map.of(",
        "                    PlanExecuteState.PLAN, new ArrayList<String>(),",
        "                    PlanExecuteState.RESPONSE, act.response",
        "            );",
        "        }",
        "        var next = (act == null || act.plan == null)",
        "                ? new ArrayList<String>()",
        "                : new ArrayList<>(act.plan);",
        "        if (next.isEmpty()) {",
        '            var fallback = "Final answer based on executed steps:\\n" + formatPastSteps(state.pastSteps());',
        "            return Map.of(",
        "                    PlanExecuteState.PLAN, next,",
        "                    PlanExecuteState.RESPONSE, fallback",
        "            );",
        "        }",
        '        log.info("llm replan -> continue, remaining={}", next);',
        "        return Map.of(PlanExecuteState.PLAN, next);",
        "    }",
        "}",
    )
)

cells.append(
    md(
        """
### Build / run LLM graph

Skipped automatically when `OPENAI_API_KEY` is not set.
"""
    )
)

cells.append(
    code_lines(
        "if (!llmEnabled) {",
        '    log.warn("Skipping LLM graph — set OPENAI_API_KEY to enable.");',
        "} else {",
        "    var llmWorkflow = new StateGraph<>(PlanExecuteState.SCHEMA, PlanExecuteState::new)",
        '            .addNode("planner", node_async(new LlmPlannerNode(chatModel)))',
        '            .addNode("agent", node_async(new LlmAgentNode(chatModel)))',
        '            .addNode("replan", node_async(new LlmReplanNode(chatModel)))',
        '            .addEdge(START, "planner")',
        '            .addEdge("planner", "agent")',
        '            .addEdge("agent", "replan")',
        '            .addConditionalEdges("replan", edge_async(shouldContinue), Map.of(',
        '                    "continue", "agent",',
        '                    "respond", END',
        "            ));",
        "",
        "    var llmApp = llmWorkflow.compile();",
        "",
        "    var llmRepresentation = llmWorkflow.getGraph(",
        '            GraphRepresentation.Type.PLANTUML, "plan-and-execute (llm)", false);',
        "    display(plantUML2PNG(llmRepresentation.getContent()));",
        "",
        "    var llmInput = Map.<String,Object>of(",
        '            PlanExecuteState.INPUT, "What is the weather in San Francisco?"',
        "    );",
        "    for (var event : llmApp.stream(llmInput)) {",
        '        log.info("LLM STEP: {}", event);',
        "    }",
        "}",
    )
)

cells.append(
    md(
        """
## 5. Next steps

* Swap `SearchTools` for Tavily / HTTP / MCP tools (`agentexecutor-mcp.ipynb`)
* Persist runs with `MemorySaver` / Postgres (`persistence.ipynb`)
* Port more [#8](https://github.com/langgraph4j/langgraph4j/issues/8) tutorials: Self-RAG, Reflection, Plan-and-Execute variants
"""
    )
)

nb = {
    "nbformat": 4,
    "nbformat_minor": 5,
    "metadata": {
        "kernelspec": {
            "display_name": "Java (rapaio)",
            "language": "java",
            "name": "rapaio-jupyter-kernel",
        },
        "language_info": {"name": "java"},
    },
    "cells": cells,
}

path = Path(__file__).resolve().parents[1] / "how-tos" / "plan-and-execute.ipynb"
path.write_text(json.dumps(nb, indent=1, ensure_ascii=False) + "\n")
print(f"wrote {path} cells={len(cells)}")
