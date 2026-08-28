# LangGraph4j LangChain4j Integration

This directory contains the LangChain4j integration modules for LangGraph4j.
Use `langchain4j-core` when you need reusable LangChain4j adapters inside your own graphs, and use `langchain4j-agent` when you want a ready-made ReAct-style agent executor built on top of LangGraph4j.

## Modules

- [spring-ai-core](langchain4j-core/README.md): Streaming chat generation, Spring AI tool execution helpers, and serializers for Spring AI messages and agent state.
- [spring-ai-agent](langchain4j-agent/README.md): ReAct agent executors for `ChatModel`-based agents, including tool callbacks, streaming output, approvals, and LangGraph Studio integration.

