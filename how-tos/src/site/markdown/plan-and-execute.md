# Plan-and-Execute

Port of the LangGraph [Plan-and-Execute](https://langchain-ai.github.io/langgraph/tutorials/plan-and-execute/plan-and-execute/) pattern to LangGraph4j.

Related to [#8](https://github.com/langgraph4j/langgraph4j/issues/8).

See the interactive notebook: [`plan-and-execute.ipynb`](../../plan-and-execute.ipynb).

## Architecture

```
START → planner → agent → replan ──► (continue) → agent
                      ↑               │
                      └───────────────┘
                                      └──► (respond) → END
```

| Node | Role |
|------|------|
| `planner` | Break the user goal into ordered steps |
| `agent` | Execute the first remaining step |
| `replan` | Refresh the plan or emit the final answer |

## Modes

1. **Stub** — deterministic nodes (no API key). Covered by `PlanAndExecuteStubTest`.
2. **LLM** — LangChain4j `AiServices` planner / tool agent / replanner. Requires `OPENAI_API_KEY`. Covered by `PlanAndExecuteITest` (excluded from default Surefire via `*ITest`).

Replace `SearchTools` with Tavily / HTTP / MCP tools for production-style demos.
