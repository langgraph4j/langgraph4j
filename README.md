
# 🦜🕸️ Welcome to LangGraph4j ( <i>AI Agentic workflow in Java</i> )

‼️ **Project has been moved here from personal space [bsorrentino](https://github.com/bsorrentino/bsorrentino)**
----

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg) [![docs](https://img.shields.io/badge/Site-Documentation-blue)][documentation] [![Static Badge](https://img.shields.io/badge/maven--snapshots-1.6--SNAPSHOT-blue)][snapshots] [![Maven Central](https://img.shields.io/maven-central/v/org.bsc.langgraph4j/langgraph4j-core.svg)][releases][![discord](https://img.shields.io/discord/1364514593765986365?logo=discord&style=flat)](https://discord.gg/szVVztSYKh)


LangGraph for Java. A library for building stateful, multi-agents applications with LLMs, built for work with [langchain4j] and [Spring AI]
> It is inspired by [LangGraph] solution, part of [LangChain AI project].


## Releases

‼️ **Note:**
> From release 1.2.x the miminum supported Java version is the `Java 17` and
> the artifact `langgraph4j-core-jdk8` is replaced by `langgraph4j-core`

| Date         | Release        | info
|--------------|----------------| ---
| Sep 14, 2025 | `1.6.3` | official release

----

# Getting Started

Welcome to LangGraph4j! This guide will help you understand the core concepts of LangGraph4j, install it, and build your first application.

## Introduction

LangGraph4j is a Java library for building stateful, multi-agent applications with Large Language Models (LLMs). It is inspired by the Python library [LangGraph](https://github.com/langchain-ai/langgraph) and is designed to work seamlessly with popular Java LLM frameworks like [Langchain4j](https://github.com/langchain4j/langchain4j) and [Spring AI](https://spring.io/projects/spring-ai).

At its core, LangGraph4j allows you to define cyclical graphs where different components (agents, tools, or custom logic) can interact in a stateful manner. This is crucial for building complex applications that require memory, context, and the ability for different "agents" to collaborate or hand off tasks.

## Core Features & Benefits

LangGraph4j offers several features and benefits:

*   **Stateful Execution:** Manage and update a shared state across graph nodes, enabling memory and context awareness.
*   **Cyclical Graphs:** Unlike traditional DAGs, LangGraph4j supports cycles, essential for agent-based architectures where control flow can loop back (e.g., an agent retrying a task or asking for clarification).
*   **Explicit Control Flow:** Clearly define the paths and conditions for transitions between nodes in your graph.
*   **Modularity:** Build complex systems from smaller, reusable components (nodes).
*   **Flexibility:** Integrate with various LLM providers and custom Java logic.
*   **Observability & Debugging:**
    *   **Checkpoints:** Save the state of your graph at any point and replay or inspect it later. This is invaluable for debugging and understanding complex interactions.
    *   **Graph Visualization:** Generate visual representations of your graph using PlantUML or Mermaid to understand its structure.
*   **Asynchronous & Streaming Support:** Build responsive applications with non-blocking operations and stream results from LLMs.
*   **Playground & Studio:** A web UI to visually inspect, run, and debug your graphs.

## Core Concepts Explained

Understanding these concepts is key to using LangGraph4j effectively:

### `StateGraph<S extends AgentState>`

The `StateGraph` is the primary class you'll use to define the structure of your application. It's where you add nodes and edges to create your graph. It is parameterized by an `AgentState`.

### `AgentState`

The `AgentState` (or a class extending it) represents the shared state of your graph. It's essentially a map (`Map<String, Object>`) that gets passed from node to node. Each node can read from this state and return updates to it.

*   **Schema:** The structure of the state is defined by a "schema," which is a `Map<String, Channel.Reducer>`. Each key in the map corresponds to an attribute in the state.
*   **`Channel.Reducer`:** A reducer defines how updates to a state attribute are handled. For example, a new value might overwrite the old one, or it might be added to a list of existing values.
*   **`Channel.Default<T>`:** Provides a default value for a state attribute if it's not already set.
*   **`Channel.Appender<T>` / `MessageChannel.Appender<M>`:** A common type of reducer that appends the new value to a list associated with the state attribute. This is useful for accumulating messages, tool calls, or other sequences of data. `MessageChannel.Appender` is specifically designed for chat messages and can also handle message deletion by ID.

### `Nodes`

Nodes are the building blocks of your graph that perform actions. A node is typically a function (or a class implementing `NodeAction<S>` or `AsyncNodeAction<S>`) that:
1.  Receives the current `AgentState` as input.
2.  Performs some computation (e.g., calls an LLM, executes a tool, runs custom business logic).
3.  Returns a `Map<String, Object>` representing updates to the state. These updates are then applied to the `AgentState` according to the schema's reducers.

Nodes can be synchronous or asynchronous (`CompletableFuture`).

### `Edges`

Edges define the flow of control between nodes.

*   **Normal Edges:** An unconditional transition from one node to another. After node A completes, control always passes to node B. You define these with `addEdge(sourceNodeName, destinationNodeName)`.
*   **Conditional Edges:** The next node is determined dynamically based on the current `AgentState`. After a source node completes, an `EdgeAction<S>` (or `AsyncEdgeAction<S>`) function is executed. This function receives the current state and returns the name of the next node to execute. This allows for branching logic (e.g., if an agent decided to use a tool, go to the "execute_tool" node; otherwise, go to the "respond_to_user" node). Conditional edges are defined with `addConditionalEdges(...)`.
*   **Entry Points:** You can also define conditional entry points to your graph using `addConditionalEntryPoint(...)`.

### `Compilation`

Once you've defined all your nodes and edges in a `StateGraph`, you `compile()` it into a `CompiledGraph<S extends AgentState>`. This compiled graph is an immutable, runnable representation of your logic. Compilation validates the graph structure (e.g., checks for orphaned nodes).

### `Checkpoints (Persistence)`

LangGraph4j allows you to save (`Checkpoint`) the state of your graph at any step. This is extremely useful for:
*   **Debugging:** Inspect the state at various points to understand what happened.
*   **Resuming:** Restore a graph to a previous state and continue execution.
*   **Long-running processes:** Persist the state of long-running agent interactions.
You'll typically use a `CheckpointSaver` implementation (e.g., `MemorySaver` for in-memory storage, or you can implement your own for persistent storage).

## Project Structure

```
langgraph4j/
├── langgraph4j-bom/                     # LangGraph4j dependency management
├── langgraph4j-core/                    # LangGraph4j core components
├── langgraph4j-postgres-saver           # LangGraph4j persistent checkpoint saver based on PostgresSQL
├── langchain4j/                         # LangChain4j integration
│   ├── langchain4j-core/                # LangChain4j core components (integration required)
│   └── langchain4j-agent/               # LangChain4j agent executor
├── spring-ai/                           # Spring AI integration
│   └── spring-ai-core/                  # Spring AI core components (integration required)
│   └── spring-ai-agent/                 # Spring AI agent executor
├── studio/                              # LangGraph4j Studio (web UI)
│   └── base/                            # Base classes and interfaces
│   └── jetty/                           # Jetty server implementation
│   └── quarkus/                         # Quarkus server implementation
│   └── springboot/                      # Spring Boot implementation
├── how-tos/                             # How-tos and examples, examples repository: https://github.com/langgraph4j/langgraph4j-examples
```

## Installation

To use LangGraph4j in your project, you need to add it as a dependency.

**Maven:**

Make sure you are using Java 17 or later.

**Latest Stable Version (Recommended):**
```xml
<properties>
    <langgraph4j.version>1.6.3</langgraph4j.version> <!-- Check for the actual latest version -->
</properties>

<!-- Optional: Add the Bill of Materials (BOM) to manage langgraph4j module versions -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.bsc.langgraph4j</groupId>
      <artifactId>langgraph4j-bom</artifactId>
      <version>${langgraph4j.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
    </dependency>
    <!-- Add other langgraph4j modules if needed, e.g., langgraph4j-langchain4j -->
</dependencies>
```
*(Note: Always check the [Maven Central Repository](https://central.sonatype.com/search?q=g%3Aorg.bsc.langgraph4j) for the latest version number.)*

**Development Snapshot Version:**
If you want to use the latest unreleased features, you can use a snapshot version.
```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
    <version>1.6-SNAPSHOT</version> <!-- Or the current snapshot version -->
</dependency>
```
You might need to configure your `settings.xml` or `pom.xml` to include the Sonatype OSS snapshots repository:
```xml
<repositories>
    <repository>
        <id>sonatype-oss-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

## Your First Graph - A Simple Example

Let's create a very simple graph that has two nodes: `greeter` and `responder`.
The `greeter` node will add a greeting message to the state.
The `responder` node will add a response message based on the greeting.

**1. Define the State:**
Our state will hold a list of messages.

```java
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Define the state for our graph
class SimpleState extends AgentState {
    public static final String MESSAGES_KEY = "messages";

    // Define the schema for the state.
    // MESSAGES_KEY will hold a list of strings, and new messages will be appended.
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new)
    );

    public SimpleState(Map<String, Object> initData) {
        super(initData);
    }

    public List<String> messages() {
        return this.<List<String>>value("messages")
                .orElse( List.of() );
    }
}
```

**2. Define the Nodes:**

```java
import org.bsc.langgraph4j.action.NodeAction;
import java.util.List;
import java.util.Map;

// Node that adds a greeting
class GreeterNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("GreeterNode executing. Current messages: " + state.messages());
        return Map.of(SimpleState.MESSAGES_KEY, "Hello from GreeterNode!");
    }
}

// Node that adds a response
class ResponderNode implements NodeAction<SimpleState> {
    @Override
    public Map<String, Object> apply(SimpleState state) {
        System.out.println("ResponderNode executing. Current messages: " + state.messages());
        List<String> currentMessages = state.messages();
        if (currentMessages.contains("Hello from GreeterNode!")) {
            return Map.of(SimpleState.MESSAGES_KEY, "Acknowledged greeting!");
        }
        return Map.of(SimpleState.MESSAGES_KEY, "No greeting found.");
    }
}
```

**3. Define and Compile the Graph:**

```java
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.GraphStateException;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;

import java.util.Map;

public class SimpleGraphApp {
    
    public static void main(String[] args) throws GraphStateException {
        // Initialize nodes
        GreeterNode greeterNode = new GreeterNode();
        ResponderNode responderNode = new ResponderNode();

        // Define the graph structure
       var stateGraph = new StateGraph<>(SimpleState.SCHEMA, initData -> new SimpleState(initData))
            .addNode("greeter", node_async(greeterNode))
            .addNode("responder", node_async(responderNode))
            // Define edges
            .addEdge(START, "greeter") // Start with the greeter node
            .addEdge("greeter", "responder")
            .addEdge("responder", END)   // End after the responder node
             ;
        // Compile the graph
        var compiledGraph = stateGraph.compile();

        // Run the graph
        // The `stream` method returns an AsyncGenerator.
        // For simplicity, we'll collect results. In a real app, you might process them as they arrive.
        // Here, the final state after execution is the item of interest.
        
        for (var item : compiledGraph.stream( Map.of( SimpleState.MESSAGES_KEY, "Let's, begin!" ) ) ) {

            System.out.println( item );
        }

    }
}
```

**Explanation:**

*   We defined `SimpleState` with a `MESSAGES_KEY` that uses `AppenderChannel` to accumulate strings.
*   `GreeterNode` adds a "Hello" message.
*   `ResponderNode` checks for the greeting and adds an acknowledgment.
*   The `StateGraph` is defined, nodes are added, and edges specify the flow: `START` -> `greeter` -> `responder` -> `END`.
*   `stateGraph.compile()` creates the runnable `CompiledGraph`.
*   `compiledGraph.stream(initialState)` executes the graph. We iterate through the stream to get the final state. Each item in the stream represents the state after a node has executed.

This example demonstrates the basic workflow: define state, define nodes, wire them with edges, compile, and run.

## Running Your Graph

As shown in the example, you typically run a compiled graph using one of its execution methods:

*   `stream(S initialState, RunnableConfig config)`: Executes the graph and returns an `AsyncGenerator<S>`. Each yielded item is the state `S` after a node has completed. This is useful for observing the state at each step or for streaming partial results.
*   `invoke(S initialState, RunnableConfig config)`: Executes the graph and returns a `CompletableFuture<S>` that completes with the final state of the graph after it reaches an `END` node.

The `RunnableConfig` can be used to pass in runtime configuration.

## Studio 🤩 - Running Your Graph visually

**Langgraph4j Studio** is an embeddable web application for visualizing and experimenting with graphs:

To explore the **Langgraph4j Studio** go to [studio]

## BONUS: built-in integrations

### LangChain4j 

As default use case to proof [LangChain4j] integration, We have implemented **AgentExecutor (aka ReACT Agent)** using LangGraph4j. In the [project's module][agent-executor], you can the complete working code with tests. Feel free to checkout and use it as a reference.
Below usage example of the `AgentExecutor`.

#### Define Tools

```java
public class TestTool {

    @Tool("tool for test AI agent executor")
    String execTest(@P("test message") String message) {
        return format( "test tool ('%s') executed with result 'OK'", message);
    }

    @Tool("return current number of system thread allocated by application")
    int threadCount() {
        return Thread.getAllStackTraces().size();
    }

}
```

#### Run Agent

```java

var model = OllamaChatModel.builder()
            .modelName( "qwen2.5:7b" )
            .baseUrl("http://localhost:11434")
            .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(2)
            .temperature(0.0)
            .build();

var agent = AgentExecutor.builder()
            .chatModel(model)
            .toolsFromObject(new TestTool())
            .build()
            .compile();

for (var item : agent.stream( Map.of( "messages", "perform test twice and return number of current active threads" ) ) ) {

    System.out.println( item );
}

```

### Spring AI 

As default use case to proof [Spring AI] integration, We have implemented **AgentExecutor (aka ReACT Agent)** using LangGraph4j. In the [project's module][spring-ai-agent], you can the complete working code with tests. Feel free to checkout and use it as a reference.
Below usage example of the `AgentExecutor`.

#### Define Tools

```java
public class TestTool {

    @Tool(description = "tool for test AI agent executor")
    String execTest( @ToolParam(description ="test message") String message ) {
        return format( "test tool ('%s') executed with result 'OK'", message);
    }

    @Tool(description = "return current number of system thread allocated by application")
    int threadCount() {
        return Thread.getAllStackTraces().size();
    }

}
```

#### Run Agent

```java

var model = OllamaChatModel.builder()
            .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
            .defaultOptions(OllamaOptions.builder()
                    .model("qwen2.5:7b")
                    .temperature(0.1)
                    .build())
            .build();

var agent = AgentExecutor.builder()
        .chatModel(model)
        .toolsFromObject(new TestTool())
        .build()
        .compile()
        ;

for (var item : agent.stream( Map.of( "messages", "perform test twice and return number of current active threads" ) ) ) {

    System.out.println( item );
}
```

## Key Capabilities Overview

LangGraph4j is packed with features to build sophisticated agentic applications:

*   **Asynchronous Operations:** Nodes and edges can be asynchronous (returning `CompletableFuture`), allowing for non-blocking I/O operations, especially when dealing with LLM calls.
*   **Streaming:** Natively supports streaming responses from LLMs through nodes, enabling real-time output. See [how-tos/llm-streaming.ipynb].
*   **Checkpoints (Persistence & Time Travel):** Save and load the state of your graph. This allows you to resume long-running tasks, debug by inspecting intermediate states, and even "time travel" to previous states. See [how-tos/persistence.ipynb] and [how-tos/time-travel.ipynb].
*   **Graph Visualization:** Generate [PlantUML] or [Mermaid] diagrams of your graph to visualize its structure, which aids in understanding and debugging. See [how-tos/plantuml.ipynb].
*   **Playground & Studio:** LangGraph4j comes with an embeddable web UI (Studio) that allows you to visualize, run, and interact with your graphs in real-time. This is excellent for development and debugging.
*   **Child Graphs (Subgraphs):** Compose complex graphs by nesting smaller, reusable graphs within nodes of a parent graph. This promotes modularity and reusability. See [how-tos/subgraph-as-nodeaction.ipynb], [how-tos/subgraph-as-compiledgraph.ipynb], and [how-tos/subgraph-as-stategraph.ipynb].
*   **Parallel Execution:** Configure parts of your graph to execute multiple nodes in parallel, improving performance for tasks that can be run concurrently. See [how-tos/parallel-branch.ipynb].
*   **Threads (Multi-turn Conversations):** Manage distinct, parallel execution threads within a single graph instance, each with its own checkpoint history. This is vital for handling multiple user sessions or conversations simultaneously.

## Next Steps

Now that you have a basic understanding of LangGraph4j, here's how you can continue your journey:

*   **Explore the `how-to`:** The [`how-tos/`](how-tos) directory in the repository contains Jupyter notebooks (runnable with Java kernels like [IJava](https://github.com/SpencerPark/IJava)) that demonstrate various features with code examples.
*   **Study the Examples:** Check out the examples from [here](https://github.com/langgraph4j/langgraph4j-examples) for more complete application examples, including integrations with Langchain4j and Spring AI.
*   **Consult the Javadocs:** For detailed information on classes and methods, refer to the [API documentation (Javadocs)][javadocs]. *(Link might need updating if official project documentation site changes)*
*   **Experiment!** The best way to learn is by doing. Try modifying the examples or building your own simple graphs.

We hope this guide helps you get started with LangGraph4j. Happy building!

## Star History

[![Star History Chart](https://starchart.cc/langgraph4j/langgraph4j.svg)](https://starchart.cc/langgraph4j/langgraph4j)

# References

## Articles
* [LangGraph4j Meets AG-UI - Building UI/UX in AI Agents era](https://bsorrentino.github.io/bsorrentino/ai/2025/08/21/LangGraph4j-meets-AG-UI.html)
* [LangGraph4j - Implementing Human-in-the-Loop at ease](https://bsorrentino.github.io/bsorrentino/ai/2025/07/13/LangGraph4j-Agent-with-approval.html)
* [LangGraph4j - Multi-Agent handoff implementation with Spring AI](https://bsorrentino.github.io/bsorrentino/ai/2025/05/10/Langgraph4j-agent-handoff.html)
* [Microsoft JDConf 2025 - AI Agents Graph: Your following tool in your Java AI journey](https://youtu.be/Sp36wdpobpI?si=dXDTD9k16hM8A8KQ)
* [Enhancing AI Agent Development: A Hands-On Weekend with LangGraph4J](https://www.linkedin.com/pulse/enhancing-ai-agent-development-hands-on-weekend-langgraph4j-chung-ha-iu3be/)
* [LangGraph4j Generator - Visually scaffold LangGraph Java code](https://dev.to/bsorrentino/langgraph4j-generator-2ika)
* [AI Agent in Java with LangGraph4j][article01]
* [Building Stateful Multi AI Agents -LangGraph4J & Spring AI](https://medium.com/@ganeshmoorthy5999/building-stateful-multi-ai-agents-langgraph4j-spring-ai-c0046e293d00)

## Projects

* [A deep research assistant based on the Langgraph4j](https://github.com/imfangs/langgraph4j-deep-researcher)
* [research4j - Build your own perplexity for your applications](https://github.com/bhavuklabs/research4j)
* [Multi Agent Banking Assistant with Java using Langraph4j](https://github.com/Azure-Samples/agent-openai-java-banking-assistant-langgraph4j)
* [Java Async Generator, a Java version of Javascript async generator][java-async-generator]
* [AIDEEPIN: Ai-based productivity tools (Chat,Draw,RAG,Workflow etc)](https://github.com/moyangzhan/langchain4j-aideepin)
* [Dynamo Multi AI Agent POC: Unlock the Power of Spring AI and LangGraph4J](https://github.com/Breezeware-OS/dynamo-multi-ai-agent-langgraph4j-starter)
* [LangChain4j & LangGraph4j Integrate LangFuse](https://github.com/Kugaaa/langchain4j-langfuse)

<!--
* [LangGraph - LangChain Blog][langgraph.blog]
-->

[LangChain AI project]: https://github.com/langchain-ai
[langchain4j]: https://github.com/langchain4j/langchain4j
[Spring AI]: https://spring.io/projects/spring-ai
[langgraph]: https://github.com/langchain-ai/langgraph
[documentation]: https://langgraph4j.github.io/langgraph4j/
[releases]: https://central.sonatype.com/search?q=a%3Alanggraph4j-parent
[snapshots]: https://central.sonatype.com/repository/maven-snapshots/org/bsc/langgraph4j/
[PlantUML]: https://plantuml.com
[Mermaid]: https://mermaid.js.org
[documentation]: https://langgraph4j.github.io/langgraph4j/
[javadocs]: https://langgraph4j.github.io/langgraph4j/apidocs/
[springai-agentexecutor]: spring-ai/spring-ai-agent
[agent-executor]: langchain4j/langchain4j-agent
[spring-ai-agent]: spring-ai/spring-ai-agent
[Studio]: studio/README.md
[Jetty]: https://jetty.org
[Spring Boot]: https://spring.io/projects/spring-boot
[Quarkus]: https://quarkus.io
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[article01]: https://bsorrentino.github.io/bsorrentino/ai/2024/05/20/langgraph-for-java.html
[langgraph.blog]: https://blog.langchain.dev/langgraph/
[langchain]: https://github.com/langchain-ai/langchain/
[langgraph]: https://github.com/langchain-ai/langgraph
[langchain.agents]: https://python.langchain.com/docs/modules/agents/
[AgentExecutor]: https://github.com/langchain-ai/langchain/blob/master/libs/langchain/langchain/agents/agent.py
[java-async-generator]: https://github.com/bsorrentino/java-async-generator

[how-tos/plantuml.ipynb]: how-tos/plantuml.ipynb
[how-tos/persistence.ipynb]: how-tos/persistence.ipynb
[how-tos/llm-streaming.ipynb]: how-tos/llm-streaming.ipynb
[how-tos/time-travel.ipynb]: how-tos/time-travel.ipynb
[how-tos/subgraph-as-nodeaction.ipynb]: how-tos/subgraph-as-nodeaction.ipynb
[how-tos/subgraph-as-compiledgraph.ipynb]: how-tos/subgraph-as-compiledgraph.ipynb
[how-tos/subgraph-as-stategraph.ipynb]: how-tos/subgraph-as-stategraph.ipynb




