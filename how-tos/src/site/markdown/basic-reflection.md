# Basic Reflection

Port of the LangGraph [Reflection](https://langchain-ai.github.io/langgraph/tutorials/reflection/reflection/) pattern to LangGraph4j.

Related to [#8](https://github.com/langgraph4j/langgraph4j/issues/8).

See the interactive notebook: [`basic-reflection.ipynb`](../../basic-reflection.ipynb).

## Architecture

```
START → generate → reflect ──► (continue) → generate
              ↑                 │
              └─────────────────┘
                                └──► END
```

| Node | Role |
|------|------|
| `generate` | Produce or revise a draft |
| `reflect` | Critique the latest draft |

Stops after `MAX_ROUNDS` generate/reflect cycles.

## Modes

1. **Stub** — deterministic draft/critique (no API key). Covered by `BasicReflectionStubTest`.
2. **LLM** — LangChain4j writer + critic. Requires `OPENAI_API_KEY`. Covered by `BasicReflectionITest`.
