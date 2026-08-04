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

The notebook includes a deterministic stub implementation so the graph runs without an LLM API key. Replace stub nodes with LangChain4j `AiServices` / agent-executor for a live model.
