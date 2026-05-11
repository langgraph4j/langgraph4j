# LangGraph4j Spring AI Integration

This directory contains the Spring AI integration modules for LangGraph4j. Use `spring-ai-core` when you need reusable Spring AI adapters inside your own graphs, and use `spring-ai-agent` when you want a ready-made ReAct-style agent executor built on top of LangGraph4j.

## Modules

- [spring-ai-core](spring-ai-core/README.md): Streaming chat generation, Spring AI tool execution helpers, and serializers for Spring AI messages and agent state.
- [spring-ai-agent](spring-ai-agent/README.md): ReAct agent executors for `ChatModel`-based agents, including tool callbacks, streaming output, approvals, and LangGraph Studio integration.

## Documentation

- [Core Maven site docs](spring-ai-core/src/site/markdown/index.md)
- [Agent Maven site docs](spring-ai-agent/src/site/markdown/index.md)
