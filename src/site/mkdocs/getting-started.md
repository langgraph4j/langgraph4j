# 🦜🕸️ Getting Started with LangGraph4j

Welcome to LangGraph4j! This guide will help you understand the core concepts of LangGraph4j, install it, and build your first application.

## Introduction

LangGraph4j is a Java library for building stateful, multi-agent applications with Large Language Models (LLMs). It is inspired by the Python library [LangGraph] and is designed to work seamlessly with popular Java LLM frameworks like [Langchain4j] and [Spring AI].

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
*(Note: Always check the [Maven Central Repository] for the latest version number.)*

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import java.util.Collections;
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

import java.util.List;
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

To explore the **Langgraph4j Studio** go to [Studio]

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
*   **Child Graphs (Subgraphs):** Compose complex graphs by nesting smaller, reusable graphs within nodes of a parent graph. This promotes modularity and reusability. See [how-tos/subgraph-as-nodeaction.ipynb], [how-tos/subgraph-as-compiledgraph.ipynb], and [how-tos/subgraph-as-stategraph.ipynb] samples.
*   **Parallel Execution:** Configure parts of your graph to execute multiple nodes in parallel, improving performance for tasks that can be run concurrently. See [how-tos/parallel-branch.ipynb].
*   **Threads (Multi-turn Conversations):** Manage distinct, parallel execution threads within a single graph instance, each with its own checkpoint history. This is vital for handling multiple user sessions or conversations simultaneously.

## Next Steps

Now that you have a basic understanding of LangGraph4j, here's how you can continue your journey:

*   **Explore the `how-tos`:** The [how-tos/] directory in the repository contains Jupyter notebooks (runnable with Java kernels like [IJava]) that demonstrate various features with code examples.
*   **Study the Samples:** Check out the [samples/] directory for more complete application examples, including integrations with Langchain4j and Spring AI.
*   **Consult the Javadocs:** For detailed information on classes and methods, refer to the [API documentation (Javadocs)]. *(Link might need updating if official project documentation site changes)*
*   **Experiment!** The best way to learn is by doing. Try modifying the examples or building your own simple graphs.

We hope this guide helps you get started with LangGraph4j. Happy building!


[agent-executor]: https://github.com/langgraph4j/langgraph4j/tree/main/langchain4j/langchain4j-agent
[spring-ai-agent]: https://github.com/langgraph4j/langgraph4j/tree/main/spring-ai/spring-ai-agent
[Studio]: /studio

[LangGraph]: https://github.com/langchain-ai/langgraph
[Langchain4j]: https://github.com/langchain4j/langchain4j
[Spring AI]: https://spring.io/projects/spring-ai.
[Maven Central Repository]: https://central.sonatype.com/search?q=g%3Aorg.bsc.langgraph4j
[Mermaid]: https://mermaid.js.org/
[PlantUML]: https://plantuml.com/

[how-tos/]: how-tos/
[how-tos/llm-streaming.ipynb]: how-tos/llm-streaming.ipynb
[how-tos/plantuml.ipynb]: how-tos/plantuml.ipynb
[how-tos/persistence.ipynb]:how-tos/persistence.ipynb
[how-tos/time-travel.ipynb]: how-tos/time-travel.ipynb
[how-tos/subgraph-as-nodeaction.ipynb]: how-tos/subgraph-as-nodeaction.ipynb
[how-tos/subgraph-as-compiledgraph.ipynb]: how-tos/subgraph-as-compiledgraph.ipynb
[how-tos/subgraph-as-stategraph.ipynb]: how-tos/subgraph-as-stategraph.ipynb
[how-tos/parallel-branch.ipynb]: how-tos/parallel-branch.ipynb

[IJava]: https://github.com/SpencerPark/IJava
[samples/]: https://github.com/langgraph4j/langgraph4j-examples
[API documentation (Javadocs)]: /langgraph4j/apidocs/index.html
