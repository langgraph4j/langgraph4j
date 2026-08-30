# <img src="https://spring.io/img/favicon.ico" alt="logo" width="25"/> Spring AI Integrations


`langgraph4j-springai-agentexecutor` provides ReAct-style agents built on LangGraph4j and Spring AI `ChatModel`. Use it when you want a ready-to-run agent loop with tool callbacks, streaming output, approvals, or sub-agents instead of wiring every node manually.

## Features

- `AgentExecutor` for a compact ReAct graph with `agent -> action -> agent` execution.
- `AgentExecutorEx` for explicit tool-dispatch nodes, approval gates, and richer orchestration.
- Streaming support backed by Spring AI `ChatModel` responses.
- Tool registration from `ToolCallback`, `ToolCallbackProvider`, or annotated tool objects.
- Optional LangGraph Studio integration for interactive graph execution.

## Agent Executor Diagram 

```mermaid
flowchart TD
	__START__((start))
	__END__((stop))
	agent("agent")
	action("actions")
	%%	condition1{"check state"}
	__START__:::__START__ --> agent:::agent
	%%	agent:::agent -.-> condition1:::condition1
	%%	condition1:::condition1 -.->|continue| action:::action
	agent:::agent -.->|continue| action:::action
	%%	condition1:::condition1 -.->|end| __END__:::__END__
	agent:::agent -.->|end| __END__:::__END__
	action:::action --> agent:::agent

	classDef ___START__ fill:black,stroke-width:1px,font-size:xx-small;
	classDef ___END__ fill:black,stroke-width:1px,font-size:xx-small;

```

## AgentExecutorEx Diagram

```mermaid
flowchart TD
	__START__((start)):::__START__
	__END__((stop)):::__END__
	model("model")
	action_dispatcher("action_dispatcher")
	action1("action 1")
	action2("action 2")
	approval_action3("approval action 3 <br>(interruption)")
	action3("action 3")

	%%	condition1{"check state"}
	%%	condition2{"check state"}
	__START__:::__START__ --> model:::model
	%%	model:::model -.-> condition1:::condition1
	%%	condition1:::condition1 -.->|continue| action_dispatcher:::action_dispatcher
	model:::model -.->|continue| action_dispatcher:::action_dispatcher
	%%	condition1:::condition1 -.->|end| __END_:::__END_
	model:::model -.->|end| __END__:::__END__
	action1:::action1 --> action_dispatcher:::action_dispatcher
	action2:::action2 --> action_dispatcher:::action_dispatcher
	%%	action_dispatcher:::action_dispatcher -.-> condition2:::condition2
	%%	condition2:::condition2 -.-> model:::model
	action_dispatcher:::action_dispatcher -.-> model:::model
	%%	condition2:::condition2 -.-> __END_:::__END_
	action_dispatcher:::action_dispatcher -.-> __END__:::__END__
	%%	condition2:::condition2 -.-> action1:::action1
	action_dispatcher:::action_dispatcher -.-> action1:::action1
	%%	condition2:::condition2 -.-> action2:::action2
	action_dispatcher:::action_dispatcher -.-> action2:::action2
	%%	condition1{"check state"}
	%%	condition2{"check state"}

        action3:::action3 --> action_dispatcher:::action_dispatcher
	approval_action3:::approval_action3 -.-> model:::model
	approval_action3:::approval_action3 -.-> action_dispatcher:::action_dispatcher
	approval_action3:::approval_action3 -.->|APPROVED| action3:::action3
	action_dispatcher:::action_dispatcher -.-> approval_action3:::approval_action3


	classDef __START__ fill:black,stroke-width:1px,font-size:xx-small;
	classDef __END__ fill:black,stroke-width:1px,font-size:xx-small;
```

## Installation

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-springai-agentexecutor</artifactId>
    <version>1.9.0-beta4</version>
</dependency>
```

This module depends on `langgraph4j-spring-ai` and targets Java 17.

## Usage

### Configure a `ChatModel`

```java
@Configuration
public class ChatModelConfiguration {

    @Bean
    @Profile("ollama")
    ChatModel ollamaModel() {
        return OllamaChatModel.builder()
                .ollamaApi(new OllamaApi("http://localhost:11434"))
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen2.5:7b")
                        .temperature(0.1)
                        .build())
                .build();
    }
}
```

### Build and run an agent

```java
var agent = AgentExecutor.builder()
        .chatModel(chatModel)
        .tools(tools)
        .build()
        .compile();

var result = agent.stream(
        GraphInput.args(Map.of("messages", new UserMessage("what is 234 + 45?"))),
        RunnableConfig.empty());

var finalState = result.stream()
        .reduce((a, b) -> b)
        .orElseThrow()
        .state();
```

The default state type is `AgentExecutor.State`, which extends `MessagesState<Message>`.

### Enable streaming and tool extraction from an object

```java
var agent = AgentExecutor.builder()
        .chatModel(chatModel)
        .streaming(true)
        .emitStreamingEnd(true)
        .toolsFromObject(new TestTools())
        .build()
        .compile(compileConfig);
```

### Use `AgentExecutorEx` when you need approvals or sub-agents

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

`AgentExecutorEx.State` adds channels for pending tool execution requests, tool responses, and next-action dispatching. This is the variant used by the test applications when approval flows or sub-agents are involved.


## LangGraph Studio

The test configuration in `src/test/java/.../LangGraphStudioConfiguration.java` shows how to expose an `AgentExecutorEx` graph through `LangGraphStudioConfig`, backed by a `MemorySaver` checkpoint store.

## Related Documentation

- [Spring AI integration overview](../README.md)
- [Core Spring AI utilities](../spring-ai-core/README.md)

[Spring AI]: https://docs.spring.io/spring-ai/reference/index.html
