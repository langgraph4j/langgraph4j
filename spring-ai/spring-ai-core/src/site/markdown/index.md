# LangGraph4j Spring AI Core

`langgraph4j-spring-ai` provides reusable Spring AI adapters for LangGraph4j applications. The module focuses on three integration points: streaming chat responses, Spring AI tool execution, and serialization of state that contains Spring AI message types.

## Features

- `StreamingChatGenerator` bridges Spring AI `Flux<ChatResponse>` output to LangGraph4j streaming events.
- `SpringAIToolService` resolves and executes Spring AI `ToolCallback` instances from model-issued tool calls.
- `SpringAIJacksonStateSerializer` and `SpringAIStateSerializer` persist state containing `UserMessage`, `AssistantMessage`, `SystemMessage`, and `ToolResponseMessage`.

## Installation

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-spring-ai</artifactId>
    <version>1.9-SNAPSHOT</version>
</dependency>
```

## Key Classes

- `org.bsc.langgraph4j.spring.ai.generators.StreamingChatGenerator`
- `org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService`
- `org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer`
- `org.bsc.langgraph4j.spring.ai.serializer.std.SpringAIStateSerializer`
- `org.bsc.langgraph4j.spring.ai.util.MessageUtil`

## Additional Reference

- [Spring AI module overview](../../README.md)

[Spring AI]: https://docs.spring.io/spring-ai/reference/index.html
