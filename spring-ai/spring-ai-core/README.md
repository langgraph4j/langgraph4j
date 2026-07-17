# LangGraph4j Spring AI Core

`langgraph4j-spring-ai` provides the reusable building blocks needed to connect LangGraph4j graphs to Spring AI message, model, and tool APIs. Use this module when you want to keep your own graph topology but need Spring AI-compatible streaming, tool execution, or state serialization.

## Features

- `StreamingChatGenerator` converts Spring AI `Flux<ChatResponse>` output into LangGraph4j streaming events.
- `SpringAIToolService` resolves and executes Spring AI `ToolCallback` instances from model-issued tool calls.
- `SpringAIJacksonStateSerializer` and `SpringAIStateSerializer` persist LangGraph4j state that contains Spring AI messages.
- `MessageUtil` provides small helpers for working with Spring AI message types inside graph code.

## Installation

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-spring-ai</artifactId>
    <version>1.9.0-beta1</version>
</dependency>
```

The module targets Java 17 and expects Spring AI dependencies managed through the Spring AI BOM.

## Usage

### Stream Spring AI chat responses through LangGraph4j

```java
var generator = StreamingChatGenerator.<MyState>builder()
        .startingNode("agent")
        .startingState(state)
        .mapResult(response -> Map.of("messages", response.getResult().getOutput()))
        .emitStreamingOutputEnd(true)
        .build(chatService.streamingExecute(messages));
```

This is the adapter used by the Spring AI agent builders when `streaming(true)` is enabled.

### Execute Spring AI tool callbacks

```java
var toolService = new SpringAIToolService(List.of(toolCallbacks));

var result = toolService.executeFunctions(
        assistantMessage.getToolCalls(),
        Map.of("customerId", customerId));
```

`SpringAIToolService` matches tool calls by the Spring AI tool definition name and can propagate `Command` updates back into the graph state.

### Serialize Spring AI message state

```java
var serializer = new SpringAIJacksonStateSerializer<MyState>(MyState::new);
```

Use the Jackson serializer when your agent state contains Spring AI `Message` implementations such as `UserMessage`, `AssistantMessage`, `SystemMessage`, or `ToolResponseMessage`.

## Related Documentation

- [Spring AI integration overview](../README.md)

[Spring AI]: https://docs.spring.io/spring-ai/reference/index.html
