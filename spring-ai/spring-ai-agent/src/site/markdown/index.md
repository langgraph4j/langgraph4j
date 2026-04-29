# LangGraph4j Spring AI Agent Executor

`langgraph4j-springai-agentexecutor` packages ReAct-style agents for Spring AI `ChatModel` applications. It builds on LangGraph4j state graphs and the Spring AI integration utilities from `langgraph4j-spring-ai`.

## Diagram

![diagram](./agentexecutor.puml.png)

## Installation

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-springai-agentexecutor</artifactId>
    <version>1.9-SNAPSHOT</version>
</dependency>
```

## Build an Agent

```java
var agent = AgentExecutor.builder()
        .chatModel(chatModel)
        .tools(tools)
        .build()
        .compile();

var result = agent.stream(
        GraphInput.args(Map.of("messages", new UserMessage("what is 234 + 45?"))),
        RunnableConfig.empty());
```

Use `streaming(true)` and `emitStreamingEnd(true)` when you want incremental output events from the underlying Spring AI model.

## Choose Between `AgentExecutor` and `AgentExecutorEx`

- `AgentExecutor` keeps the graph compact and is suitable for the common `agent -> action -> agent` loop.
- `AgentExecutorEx` expands tool execution into dedicated nodes and adds approval and dispatch channels in `AgentExecutorEx.State`.

Example:

```java
var agent = AgentExecutorEx.builder()
        .chatModel(chatModel)
        .streaming(true)
        .approvalOn("threadCount", (nodeId, state) ->
                InterruptionMetadata.builder(nodeId, state)
                        .addMetadata("label", "confirm thread count execution?")
                        .build())
        .toolsFromObject(new TestTools())
        .build(compileConfig);
```

## LangGraph Studio Integration

The test configuration uses `LangGraphStudioConfig` to expose an `AgentExecutorEx` graph:

```java
@Configuration
public class LangGraphStudioConfiguration extends LangGraphStudioConfig {

    private final StateGraph<AgentExecutorEx.State> workflow;

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return Map.of("sample", LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Spring AI)")
                .addInputStringArg("messages", true, v -> new UserMessage(Objects.toString(v)))
                .graph(workflow)
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .releaseThread(true)
                        .build())
                .build());
    }
}
```

## Related Documentation

- [Spring AI integration overview](../../README.md)
- [Core Spring AI utilities](../../spring-ai-core/README.md)

[Spring AI]: https://docs.spring.io/spring-ai/reference/index.html
