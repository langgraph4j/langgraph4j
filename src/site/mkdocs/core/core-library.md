# Core Library: Conceptual Guide

## Graphs

At its core, LangGraph4j models agent workflows as graphs. You define the behavior of your agents using three key components:

1. [State](#state): A shared data structure that represents the current snapshot of your application. It is represented by an [AgentState] object.

2. [Nodes](#nodes): A **Functional Interface** ([AsyncNodeAction])  that encode the logic of your agents. They receive the current `State` as input, perform some computation or side-effect, and return a request for  updated `State`.

3. [Edges](#edges): A **Functional Interface**  ([AsyncCommandAction]) that determine which `Node` to execute next based on the current `State`. They can be conditional branches or fixed transitions.
   > It also can equest for  updated `State` 👀.

By composing `Nodes` and `Edges`, you can create complex, looping workflows that evolve the `State` over time. The real power, though, comes from how LangGraph4j manages that `State`. 
To emphasize: `Nodes` and `Edges` are like functions - they can contain an LLM or just Java code.

In short: _nodes do the work. edges tell what to do next_.

<!-- 
LangGraph4j's underlying graph algorithm uses [message passing](https://en.wikipedia.org/wiki/Message_passing) to define a general program. When a Node completes its operation, it sends messages along one or more edges to other node(s). These recipient nodes then execute their functions, pass the resulting messages to the next set of nodes, and the process continues. Inspired by Google's [Pregel](https://research.google/pubs/pregel-a-system-for-large-scale-graph-processing/) system, the program proceeds in discrete "super-steps." 
-->
<!-- 
A super-step can be considered a single iteration over the graph nodes. Nodes that run in parallel are part of the same super-step, while nodes that run sequentially belong to separate super-steps. At the start of graph execution, all nodes begin in an `inactive` state. A node becomes `active` when it receives a new message (state) on any of its incoming edges (or "channels"). The active node then runs its function and responds with updates. At the end of each super-step, nodes with no incoming messages vote to `halt` by marking themselves as `inactive`. The graph execution terminates when all nodes are `inactive` and no messages are in transit.
 -->

### StateGraph

The [StateGraph] class is the main graph class to uses. This is parameterized by a user defined `State` object. 

<!-- 
### MessageGraph

The `MessageGraph` class is a special type of graph. The `State` of a `MessageGraph` is ONLY an array of messages. This class is rarely used except for chatbots, as most applications require the `State` to be more complex than an array of messages.
 -->
 <a id="compiling-your-graph"></a>

### Compile your graph

To build your graph, you first define the [state](#state), you then add [nodes](#nodes) and [edges](#edges), and then you compile it. What exactly is compiling your graph and why is it needed?

Compiling is a pretty simple step. It provides a few basic checks on the structure of your graph (no orphaned nodes, etc). It is also where you can specify runtime args like [checkpointers](#checkpointer) and [breakpoints](#breakpoints). You compile your graph by just calling the `.compile` method:

```java
CompileConfig compileConfig = ....
// compile your graph
var graph = graphBuilder.compile( CompileConfig );
```

‼️ You **MUST** compile your graph before you can use it.

#### CompileConfig

The [CompileConfig] class allows you to pass configuration parameters that control runtime behaviors of your graph. You build a configuration using the builder pattern and pass it to the `.compile()` method.

```java
var compileConfig = CompileConfig.builder()
                    .checkpointSaver(mySaver)
                    .recursionLimit(50)
                    .interruptBefore("nodeA", "nodeB")
                    .build();

var graph = graphBuilder.compile(compileConfig);
```

##### Configuration Attributes

| Attribute | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| **checkpointSaver** | `BaseCheckpointSaver` | `null` | The checkpoint saver implementation for persisting graph state across executions. Required for stateful features like human-in-the-loop workflows and resuming interrupted graphs. See [Checkpointer](#checkpointer) section for more details. |
| **interruptBefore** | `Set<String>` | empty | Node names where the graph should pause **before** executing the node. Useful for inspecting state before a node runs or for getting human approval before proceeding. |
| **interruptsAfter** | `Set<String>` | empty | Node names where the graph should pause **after** executing the node. Useful for inspecting results after a node completes or for user feedback. |
| **interruptBeforeEdge** | `boolean` | `false` | If `true`, interruptions at a node occur **after** it executes but **before** any conditional edges are evaluated. This allows inspecting the state before the graph branches to the next node. |
| **recursionLimit** | `int` | `25` | Default maximum number of execution steps allowed for each graph invocation. Prevents infinite loops by raising an error if the limit is exceeded. A `RunnableConfig` recursion limit overrides this default for one invocation. |
| **releaseThread** | `boolean` | `false` | If `true`, the checkpointer will release all data associated to the current thread acquired during graph execution. |
| **graphId** | `String` | `null` | Optional identifier for the graph. Useful for logging, monitoring, or distinguishing between multiple graph instances. It will available through `RunnableConfig.graphId()`|



### Execute your graph

Once you have compiled your graph, you can execute it in two different modes: **synchronous** and **asynchronous**.

#### Synchronous Execution

For synchronous execution, use the [execute] method to run the graph and get the final result directly:

```java
var config = RunnableConfig.builder()
                          .threadId("thread-1")
                          .build();

Map<String, Object> result = graph.execute(inputs, config);
System.out.println(result);
```

The `execute()` method blocks until the graph completes and returns the final state of the graph.

#### Asynchronous Execution

For more detailed visibility into the execution flow, use the [stream] method which returns an [AsyncGenerator] that allows you to iterate over each step executed in the workflow:

```java
var config = RunnableConfig.builder()
                          .threadId("thread-1")
                          .build();

var generator = graph.stream(inputs, config);
for (var stepResult : generator) {
    System.out.println("Step executed: " + stepResult);
}
```

The `stream()` method returns an `AsyncGenerator` that yields the state updates after each node execution. This is particularly useful for:

- **Streaming updates**: Monitor intermediate states as the workflow progresses
- **Real-time feedback**: Display each step to users as it executes
- **Debugging**: Inspect how the state evolves throughout execution
- **Interruption handling**: Access interruption metadata when breakpoints are triggered

You can also retrieve the final result value from the generator, for details take a look to [GraphResult](#graphresult) section:

#### RunnableConfig

The [RunnableConfig] class carries configuration parameters through graph execution, making runtime information available to both nodes ([AsyncNodeActionWithConfig]) and conditional edges ([AsyncCommandAction]). This allows you to pass context-specific data and control execution behavior at runtime without modifying the graph structure.

You provide a `RunnableConfig` when invoking the graph:

```java
var config = RunnableConfig.builder()
                          .threadId("user-123")
                          .streamMode(CompiledGraph.StreamMode.UPDATES)
                          .recursionLimit(50)
                          .putMetadata("userId", "user-123")
                          .putMetadata("model", "gpt-4")
                          .build();

graph.stream(inputs, config);
```

##### Configuration Attributes

| Attribute | Type | Description |
| --------- | ---- | ----------- |
| **threadId** | `String` | A unique identifier for the execution thread/session. Essential for checkpoint-based persistence, as it groups related executions together. Allows resuming interrupted graphs or maintaining conversation history. |
| **checkPointId** | `String` | Specific checkpoint identifier within a thread. Useful for resuming execution from a specific point rather than from the beginning. |
| **nextNode** | `String` | Specifies which node should execute next. Primarily used internally by the graph engine when resuming interrupted executions. |
| **streamMode** | `CompiledGraph.StreamMode` | Controls how results are streamed during execution. Options are `VALUES` (full state after each step) or `UPDATES` (only state changes). Defaults to `VALUES`. |
| **recursionLimit** | `int` | Maximum number of execution steps for this invocation. Overrides the `CompileConfig` default and must be greater than zero. |
| **metadata** | `Map<String, Object>` | Custom key-value pairs available throughout execution. Useful for passing runtime context like user IDs, API keys, feature flags, or model selection that nodes and edges need access to. |

##### Reserved attributes' metadata

`RunnableConfig` provides several useful metadata that are reserved by the runtime and should not be overwritten:

| Metadata Key |Type | Getter | Purpose |
| ------------------ | ---- | ---- | ------- |
| `"LG4j_STUDIO_MDK"` | `Boolean` | `config.isRunningInStudio()` | Internal flag used by Studio integrations.|
| `"LG4j_NODE_ID"` | `String` | `config.nodeId()`| Current executing node id, injected by the runtime. |
| `"LG4j_GRAPH_PATH"` | `GraphPath` | `config.graphPath()`| graph/subgraph path used to track nested executions. |
| `"LG4j_GRAPH_ID"` | `Optional<String>` | `config.graphId()` | Effective graph id propagated at runtime (for tracing/logging). |
| `"LG4j_SUBGRAPH_UPDATE_DATA"` | `Map<String,Object>` | Internal | Reserved for subgraph resume/update handling. Do not use in application metadata. |

Additional internal keys can be generated at runtime (for example subgraph resume flags and per-parallel-node executor keys). Prefer `RunnableConfig` helper APIs such as `addParallelNodeExecutor(...)` instead of writing those keys manually.


##### Accessing RunnableConfig in Nodes and Edges

Since nodes and edges are functional interfaces, you can access the configuration through:

**In AsyncNodeActionWithConfig:**

```java
AsyncNodeActionWithConfig<MyState> node = (state, config) -> {
    // Access thread ID
    var threadId = config.threadId().orElse("default");
    
    // Access metadata
    String userId = (String) config.metadata("userId").orElse("anonymous");
    String model = (String) config.metadata("model").orElse("gpt-3.5");
    
    // Access optional graph ID
    var graphId = config.graphId();
    
    // Use configuration in your logic
    System.out.printf("Executing for user: %s with model: %s%n", userId, model);
    
    return Map.of("result", "processed");
};
```

**In AsyncCommandAction (conditional edges):**

```java
AsyncCommandAction<MyState> router = (state, config) -> {
    String userId = (String) config.metadata("userId").orElse("anonymous");
    
    // Route based on runtime configuration
    if ("premium-user".equals(userId)) {
        return "premium-path";
    } else {
        return "standard-path";
    }
};
```

##### Common Use Cases

- **User Context**: Pass user ID, tenant ID, or organizational context through execution
- **Feature Flags**: Enable/disable features at runtime via metadata
- **Model Selection**: Choose different LLM models based on metadata
- **Logging & Monitoring**: Use `graphId()` and `threadId()` for tracing and debugging
- **Session Resumption**: Use `threadId()` to fetch and resume previous execution state
- **Concurrent Control**: Configure custom executors for parallel nodes via metadata

##### Building RunnableConfig

```java
// Minimal configuration
var config = RunnableConfig.builder()
                          .threadId("thread-1")
                          .build();

// Rich configuration with metadata
var config = RunnableConfig.builder()
                          .threadId("conversation-user-123")
                          .streamMode(CompiledGraph.StreamMode.UPDATES)
                          .putMetadata("userId", "user-123")
                          .putMetadata("tenantId", "org-456")
                          .putMetadata("llmModel", "gpt-4-turbo")
                          .putMetadata("isVip", true)
                          .build();

// Modify existing configuration
var updatedConfig = config.updateMetadata(Map.of("llmModel", "gpt-4"));
```

#### GraphResult

The final result returned by `AsyncGenerator` from the [stream] method is a generic `Object` that can contain different types of values depending on what was yielded during the graph execution. Rather than constantly checking the type with `instanceof`, LangGraph4j provides the [GraphResult] utility class to safely identify and retrieve the correct result type.

##### Result Types

The [GraphResult] class can wrap the following result types:

| Type | Description |
| ---- | ----------- |
| **STATE_DATA** | A `Map<String, Object>` representing the state snapshot after a node execution. This is the most common result during graph streaming. |
| **NODE_OUTPUT** | A `NodeOutput` object containing detailed information about a node's execution, including the node ID, the resulting state, and execution metadata. |
| **INTERRUPTION_METADATA** | An `InterruptionMetadata` object indicating that the graph execution was interrupted (e.g., at a breakpoint). This contains information about why the graph was interrupted and the state at the interruption point. |
| **CHECKPOINT_SAVER_TAG** | A [BaseCheckpointSaver.Tag] object representing a checkpoint identifier and metadata. Useful for tracking persisted states. |
| **EMPTY** | Indicates no result was produced (typically when the stream yields null). |

##### Using GraphResult

Instead of manually checking types, use [GraphResult] to safely extract the result:

```java
// Get the final result direct from the generator
GraphResult finalResult = GraphResult.from(generator);

if ( finalResult.isEmpty ) {
    System.out.println("result is empty");
}
else if (finalResult.isStateData()) {
    Map<String, Object> state = result.asStateData();
    System.out.printf("Graph completed with state: %s%n", state);
} else if (finalResult.isNodeOutput()) {
    NodeOutput<?> output = result.asNodeOutput();
    System.out.printf("Graph completed with node: %s%n", output.nodeId());
} else if (finalResult.isInterruptionMetadata()) {
    InterruptionMetadata<?> metadata = result.asInterruptionMetadata();
    System.out.printf("Graph completed with interruption: %s%n", metadata);
}
```

The [GraphResult] class provides type-safe methods to check (`isStateData()`, `isNodeOutput()`, etc.) and retrieve (`asStateData()`, `asNodeOutput()`, etc.) each result type, preventing casting errors and making your code more maintainable.

## State

The first thing you do when you define a graph is define the `State` of the graph. The `State` consists of the [schema of the graph](#schema) as well as [reducer](#reducers) functions which specify how to apply updates to the state. The schema of the `State` will be the input schema to all `Nodes` and `Edges` in the graph, and should be defined using a map of  [`Channel`] object. All `Nodes` will emit updates to the `State` which are then applied using the specified `reducer` function.

### Schema

The way to specify the schema of a graph is by defining map of [Channel] objects where each key is an item in the state.
If no [Channel] is specified for an item then it is assumed that all updates to that item should override it.

### Reducers

[Reducers][reducer] are key to understanding how updates from nodes are applied to the `State`. Each key in the `State` has its own independent reducer function. If no reducer function is explicitly specified then it is assumed that all updates to that key should override it. Let's take a look at a few examples to understand them better.

**Example A:**

```java
static class MessagesState extends AgentState {

    static Map<String, Channel<?>> SCHEMA = Map.of(
            "messages", Channels.appender(ArrayList::new)
    );
}

var graphBuilder = new StateGraph<>( MessagesState.SCHEMA, MessagesState::new)

```

### AppenderChannel

In the example above we specify for `messages` property a built-in channel [AppenderChannel] which use a [Reducer] implementation to accumulate values.

<a id="remove-messages"></a>
#### Remove Messages

[AppenderChannel] supports the message deletion throught its nested functional interface [RemoveIdentifier]. Inheriting such interface you can create a particular value that when will be put inside a State's property, with [AppenderChannel] schema, instrucs the [Reducer] to remove the  element that match the specified conditions in [RemoveIdentifier] throught `compareTo( element, index )` method. 

##### RemoveByHash

Langgraph4j provides a Built in [AppederChannel.RemoveIdentifier] named [RemoveByHash] that allow to remove messages comparing their `hashCode`, below an example of its usage:

```java

var workflow = new StateGraph<>(MessagesState.SCHEMA, MessagesState::new)
        .addNode("agent_1", node_async(state -> Map.of("messages", "message1")))
        .addNode("agent_2", node_async(state -> Map.of("messages", List.of("message2", "message2.1"))))
        .addNode("agent_3", node_async(state -> 
            Map.of("messages", RemoveByHash.of("message2.1")) // this remove "message2.1" from messages values
        ))
        .addEdge("agent_1", "agent_2")
        .addEdge("agent_2", "agent_3")
        .addEdge(START, "agent_1")
        .addEdge("agent_3", END);

```
##### ReplaceAllWith

Langgraph4j provides a built in [AppederChannel.ReplaceAllWith]  that allow to replace all elements with new ones, below an example of its usage:

```java
var workflow = new StateGraph<>(MessagesState.SCHEMA, MessagesState::new)
        .addNode("agent_1", node_async(state -> Map.of("messages", "message1")))
        .addNode("agent_2", node_async(state -> Map.of("messages", List.of("message2", "message2.1"))))
        .addNode("agent_3", node_async(state -> 
            Map.of("messages", ReplaceAllWith.of( List.of("a1", "a2"))) // this replace current messages values with ["a1", "a2"]
        ))
        .addEdge("agent_1", "agent_2")
        .addEdge("agent_2", "agent_3")
        .addEdge(START, "agent_1")
        .addEdge("agent_3", END);

```

### Custom Reducer

You can also specify a custom reducer for a particular state property

**Example B:**

```java
static class MyState extends AgentState {

    static Map<String, Channel<?>> SCHEMA = Map.of(
            "property", Channel.<String>of( ( oldValue, newValue ) -> newValue.toUpperCase() )
    );
}

var graphBuilder = new StateGraph<>( MessagesState.SCHEMA, MyState::new)

```

### Serializer 

During graph execution the state needs to be serialized (mostly for cloning purpose) also for providing ability to persist the state across different executions. To do this we have provided a new streighforward implementation based on [Serializer] interface.

#### Why create a new Serialization framework ?

1. Doesn't rely on unsafe standard serialization framework.
1. Allow to implement serialization also to third-party (non serializable) classes
1. Avoid as much as possible class loading problem
1. Manage nullable value in serialization process

#### Features 

- [x] Allow to serialize using the java built-in standard binary serialization technique
- [x] Allow to plug also different serialization techniques

Currently the main class for state's serialization using built-in java stream is [ObjectStreamStateSerializer]. It is also available an abstraction allowing to plug serialization techniques text based like `JSON` and/or `YAML` that is [PlainTextStateSerializer].

<a id="seriliazer-out-of-box"></a>
#### Out of the Box

There are several provided Serializers out-of-the-box:

 class | description 
 ----- | -----
`ListSerializer` | built-in `List<Object>` serializer
`MapSerializer` | built-in `Map<String,Object>` serializer
&nbsp; |  &nbsp; 
`AiMessageSerializer` | langchain4j `AiMessage` Serializer
`ChatMesssageSerializer` | langchain4j `ChatMesssage` Serializer
`SystemMessageSerializer` | langchain4j `SystemMessage` Serializer
`UserMessageSerializer` | langchain4j `UserMessage` Serializer
`ToolExecutionRequestSerializer` | langchain4j `ToolExecutionRequest` Serializer
`ToolExecutionResultMessageSerializer` | langchain4j `ToolExecutionResultMessage` Serializer


## Nodes

<!--
In LangGraph4j, nodes are typically a **Functional Interface** ([AsyncNodeAction])  where the argument is the [state](#tate), and (optionally), the **second** positional argument is a "config", containing optional [configurable parameters](#configuration) (such as a `thread_id`).
-->
In LangGraph4j, nodes are typically a **Functional Interface** ([AsyncNodeAction])  where the argument is the [state](#state), you add these nodes to a graph using the [addNode] method:

```java
public class State extends AgentState {

  public State(Map<String, Object> initData) {
    super( initData  );
  }

  Optional<String> input() { return value("input"); } 
  Optional<String> results() { return value("results"); } 
 
}

AsyncNodeAction<State> myNode = node_async(state -> {
    System.out.println( "In myNode: " );
    return Map.of( results: "Hello " + state.input().orElse( "" ) );  
});

AsyncNodeAction<State> myOtherNode = node_async(state -> state);

var builder = new StateGraph( State::new )
  .addNode("myNode", myNode)
  .addNode("myOtherNode", myOtherNode)

```

Since [AsyncNodeAction] is designed to work with [CompletableFuture], you can use `node_async` static method that adapt it to a simpler syncronous scenario. 

<a id="start-node"></a>
### `START` Node

The `START` Node is a special node that represents the node sends user input to the graph. The main purpose for referencing this node is to determine which nodes should be called first.

```java
import static org.bsc.langgraph4j.StateGraph.START;

graph.addEdge(START, "nodeA");
```
<a id="end-node"></a>
### `END` Node

The `END` Node is a special node that represents a terminal node. This node is referenced when you want to denote which edges have no actions after they are done.

```java
import static org.bsc.langgraph4j.StateGraph.END;

graph.addEdge("nodeA", END);
```

## Edges

Edges define how the logic is routed and how the graph decides to stop. This is a big part of how your agents work and how different nodes communicate with each other. There are a few key types of edges:

- **Normal Edges**: 
  > Go directly from one node to the next.
- **Conditional Edges**: 
  > Call a function to determine which node(s) to go to next.
- **Entry Point**: 
  > Which node to call first when user input arrives.
- **Conditional Entry Point**: 
  > Call a function to determine which node(s) to call first when user input arrives.

<!-- 👉 PARALLEL
 A node can have MULTIPLE outgoing edges. If a node has multiple out-going edges, **all** of those destination nodes will be executed in parallel as a part of the next superstep. -->

<a id="normal-edges"></a>
### Normal Edges

If you **always** want to go from node A to node B, you can use the [addEdge] method directly.

```java
// add a normal edge
graph.addEdge("nodeA", "nodeB");
```

<a id="conditional-edges"></a>
### Conditional Edges

If you want to **optionally** route to 1 or more edges (or optionally terminate), you can use the [addConditionalEdges] method. This method accepts the name of a node and a **Functional Interface** ([AsyncCommandAction]) that will be used as " routing function" to call after that node is executed:

```java
graph.addConditionalEdges("nodeA", routingFunction, 
        EdgeMapping.builder()
          .to( "nodeB", "first")
          .to( "nodeC", "second" )
          .build());
```

Similar to nodes, the `routingFunction` accept the current `state` of the graph and return a string value.

<!-- By default, the return value `routingFunction` is used as the name of the node (or an array of nodes) to send the state to next. All those nodes will be run in parallel as a part of the next superstep. -->

You must provide an object that maps the `routingFunction`'s output to the name of the next node.

<a id="entry-point"></a>
### Entry Point

The entry point is the first node(s) that are run when the graph starts. You can use the [addEdge] method from the virtual `START` node to the first node to execute to specify where to enter the graph.

```java
import static org.bsc.langgraph4j.StateGraph.START;

graph.addEdge(START, "nodeA");
```
<a id="conditional-entry-point"></a>
### Conditional Entry Point

A conditional entry point lets you start at different nodes depending on custom logic. You can use [addConditionalEdges] from the virtual `START` node to accomplish this.

```java
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mapOf;

graph.addConditionalEdges(START, routingFunction, 
      EdgeMapping.builder()
          .to( "nodeB", "first")
          .to( "nodeC", "second" )
          .build());
```

You must provide an object that maps the `routingFunction`'s output to the name of the next node.

<!-- 
## `Send`

By default, `Nodes` and `Edges` are defined ahead of time and operate on the same shared state. However, there can be cases where the exact edges are not known ahead of time and/or you may want different versions of `State` to exist at the same time. A common of example of this is with `map-reduce` design patterns. In this design pattern, a first node may generate an array of objects, and you may want to apply some other node to all those objects. The number of objects may be unknown ahead of time (meaning the number of edges may not be known) and the input `State` to the downstream `Node` should be different (one for each generated object).

To support this design pattern, LangGraph4j supports returning [Send](/langgraphjs/reference/classes/langgraph.Send.html) objects from conditional edges. `Send` takes two arguments: first is the name of the node, and second is the state to pass to that node.

```typescript
const continueToJokes = (state: { subjects: string[] }) => {
  return state.subjects.map((subject) => new Send("generate_joke", { subject }));
}

graph.addConditionalEdges("nodeA", continueToJokes);
``` 
-->

## Checkpointer

LangGraph4j has a built-in persistence layer, implemented through [Checkpointers]. When you use a checkpointer with a graph, you can interact with the state of that graph. When you use a checkpointer with a graph, you can interact with and manage the graph's state. The checkpointer saves a _checkpoint_ of the graph state at every step, enabling several powerful capabilities:

First, checkpointers facilitate **human-in-the-loop workflows**<!--[human-in-the-loop workflows](agentic_concepts.md#human-in-the-loop)--> workflows by allowing humans to inspect, interrupt, and approve steps. Checkpointers are needed for these workflows as the human has to be able to view the state of a graph at any point in time, and the graph has to be to resume execution after the human has made any updates to the state.

Second, it allows for ["memory"](agentic_concepts.md#memory) between interactions. You can use checkpointers to create threads and save the state of a thread after a graph executes. In the case of repeated human interactions (like conversations) any follow up messages can be sent to that checkpoint, which will retain its memory of previous ones.

See [this guide](../how-tos/persistence.ipynb) for how to add a checkpointer to your graph.

## Threads

Threads enable the checkpointing of multiple different runs, making them essential for multi-tenant chat applications and other scenarios where maintaining separate states is necessary. A thread is a unique ID assigned to a series of checkpoints saved by a checkpointer. When using a checkpointer, you must specify a `thread_id` when running the graph.

`thread_id` is simply the ID of a thread. This is always required

You must pass these when invoking the graph as part of the configurable part of the config.

```java

var config = RunnableConfig.builder()
                                  .threadId("a")
                                  .build();
graph.invoke(inputs, config);
```

See [this guide](../how-tos/persistence.ipynb) for how to use threads.

<a id="checkpointer-state"></a>

## Checkpointer state

When interacting with the checkpointer state, you must specify a [thread identifier](#threads). Each checkpoint saved by the checkpointer has two properties:

- **state**: This is the value of the state at this point in time.
- **nextNodeId**: This is the Idenfier of the node to execute next in the graph.


<a id="get-state"></a>

### Get state

You can get the state of a checkpointer by calling [graph.getState(config)]. The config should contain `thread_id`, and the state will be fetched for that thread.

<a id="get-state-history"></a>

### Get state history

You can also call [graph.getStateHistory(config)] to get a list of the history of the graph. The config should contain `thread_id`, and the state history will be fetched for that thread.

<a id="Update-state"></a>
### Update state

You can also interact with the state directly and update it using [graph.updateState(config,values,asNode)].  This takes three different components:

- `config`
- `values`
- `asNode`

**`config`**

The config should contain `thread_id` specifying which thread to update.

**`values`**

These are the values that will be used to update the state. Note that this update is treated exactly as any update from a node is treated. This means that these values will be passed to the [reducer](#reducers) functions that are part of the state. So this does NOT automatically overwrite the state. 

**`asNode`**

The final thing you specify when calling `updateState` is `asNode`. This update will be applied as if it came from node `asNode`. If `asNode` is null, it will be set to the last node that updated the state.

<!-- 👉 AMBIGUITY  
The final thing you specify when calling `updateState` is `asNode`. This update will be applied as if it came from node `asNode`. If `asNode` is null, it will be set to the last node that updated the state, if not ambiguous.

The reason this matters is that the next steps in the graph to execute depend on the last node to have given an update, so this can be used to control which node executes next. -->

<!-- 
## Configuration

When creating a graph, you can also mark that certain parts of the graph are configurable. This is commonly done to enable easily switching between models or system prompts. This allows you to create a single "cognitive architecture" (the graph) but have multiple different instance of it.

You can then pass this configuration into the graph using the `configurable` config field.

```typescript
const config = { configurable: { llm: "anthropic" }};

await graph.invoke(inputs, config);
```

You can then access and use this configuration inside a node:

```typescript
const nodeA = (state, config) => {
  const llmType = config?.configurable?.llm;
  let llm: BaseChatModel;
  if (llmType) {
    const llm = getLlm(llmType);
  }
  ...
};
    
```

See [this guide](/langgraph4j/how-tos/langgraph4j-howtos/configuration.html) for a full breakdown on configuration 
-->

## Breakpoints (AKA interruptions )

In langgraph4j, a graph's execution can be paused at any node. This is particularly useful for implementing features like human-in-the-loop approvals, where the graph needs to
wait for external input before proceeding.

To set breakpoints before or after certain nodes execute. This can be used to wait for human approval before continuing. These can be set when you ["compile" a graph](#compiling-your-graph). 

### Static definition 

You can set breakpoints either _before_ a node executes (using `interruptBefore`) or _after_ a node executes (using `interruptAfter`) adding them on `CompileConfig`.

```java
var compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .interruptBefore( "tools")
                    .build();
```

### Dynamic definition

The `org.bsc.langgraph4j.action.InterruptibleAction<State>` interface is the core component that enables this functionality. Any node action that implements this interface can conditionally interrupt the graph's execution.

The heart of the interface is the interrupt method:

```java
public interface InterruptibleAction<State extends AgentState> {
   /**
    * Determines whether the graph execution should be interrupted at the current node.
    *
    * @param nodeId The identifier of the current node being processed.
    * @param state  The current state of the agent.
    * @return An {@link Optional} containing {@link InterruptionMetadata} if the execution
    *         should be interrupted. Returns an empty {@link Optional} to continue execution.
   */
   Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state, RunnableConfig config );
}
```

The legacy name `InterruptableAction` is still available as a deprecated alias of `InterruptibleAction` for backward compatibility.

**Here’s how it works**:

 * When the graph is about to execute a node, it first checks if the node's action implements InterruptibleAction.
 * If it does, the interrupt(String nodeId, State state) method is called.
 * If the method returns a non-empty Optional<InterruptionMetadata>, the graph's execution is paused. The InterruptionMetadata object contains information about the
  interruption, which can be sent to an external system or user for review.
 * If the method returns an empty Optional, the node executes normally, and the graph continues its execution without interruption.

---- 

You **MUST** use a [checkpoiner](#checkpointer) when using breakpoints. This is because your graph needs to be able to resume execution.

In order to resume execution, you can just invoke your graph with `GraphInput.resume()` or `GraphInput.resume(Map)` as the input.

```java
// Initial run of graph
graph.stream(inputs, config);

// Let's assume it hit a breakpoint somewhere, you can then resume it no passing new state data
graph.stream(GraphInput.resume(), config);

// Let's assume it hit a breakpoint somewhere, you can then resume it passing new state data
graph.stream(GraphInput.resume( Map.of( "key", "value")), config);

```

### Achieve InterruptionMetadata object after interruption

It is most important understand that the **nodes iterator holds the final result of graph execution**. In the case of interruption the `InterruptionMetadata` instance will be set as iterator's result so you can achieve it using : [GraphResult](#graphresult) as shown below
 
```java
var generator = app.stream( inputs );
for (var step : iterator) {
   ...
}

var finalResult =  GraphResult.from(generator);

if( finalResult.isInterruptionMetadata()) {
    var interruptionMetadata = finalResult.asInterruptionMetadata();

    ....
}

```
> `resultValue` is a generic `Object` that in case of interruptions is an instance of InterruptionMetadata



See [Wait for user Input (HITL)](../how-tos/wait-user-input.ipynb) for a full walkthrough of how to add breakpoints.

## Visualization

It's often nice to be able to visualize graphs, especially as they get more complex. LangGraph4j comes with several built-in ways to visualize graphs using diagram-as-code tools such as [PlantUML] and [Mermaid] through the [graph.getGraph] method. 

```java
// for PlantUML
GraphRepresentation result = app.getGraph(GraphRepresentation.Type.PLANTUML);

System.out.println(result.getContent());

// for Mermaid
GraphRepresentation result = app.getGraph(GraphRepresentation.Type.MERMAID);
System.out.println(result.getContent());

```

<!-- 
There are several different streaming modes that LangGraph4j supports:

- ["values"](/langgraph4j/how-tos/langgraph4j-howtos/stream-values.html): This streams the full value of the state after each step of the graph.
- ["updates](/langgraph4j/how-tos/langgraph4j-howtos/stream-updates.html): This streams the updates to the state after each step of the graph. If multiple updates are made in the same step (e.g. multiple nodes are run) then those updates are streamed separately.

In addition, you can use the [streamEvents](https://v02.api.js.langchain.com/classes/langchain_core_runnables.Runnable.html#streamEvents) method to stream back events that happen _inside_ nodes. This is useful for [streaming tokens of LLM calls](/langgraph4j/how-tos/langgraph4j-howtos/streaming-tokens-without-langchain.html). -->

[Mermaid]: https://mermaid.js.org
[java-async-generator]: https://github.com/bsorrentino/java-async-generator

[PlainTextStateSerializer]: /langgraph4j/apidocs/org/bsc/langgraph4j/serializer/plain_text/PlainTextStateSerializer.html
[ObjectStreamStateSerializer]: /langgraph4j/apidocs/org/bsc/langgraph4j/serializer/std/ObjectStreamStateSerializer.html
[RemoveByHash]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/RemoveByHash.html
[RemoveIdentifier]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/AppenderChannel.RemoveIdentifier.html
[Serializer]: /langgraph4j/apidocs/org/bsc/langgraph4j/serializer/Serializer.html
[Reducer]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/Reducer.html
[AgentState]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/AgentState.html
[StateGraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html
[Channel]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/Channel.html
[AsyncNodeAction]: /langgraph4j/apidocs/org/bsc/langgraph4j/action/AsyncNodeActionWithConfig.html
[AsyncCommandAction]: /langgraph4j/apidocs/org/bsc/langgraph4j/action/AsyncCommandAction.html
[AppenderChannel]: /langgraph4j/apidocs/org/bsc/langgraph4j/state/AppenderChannel.html
[addNode]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html#addNode(java.lang.String,org.bsc.langgraph4j.action.AsyncNodeAction)
[addEdge]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html#addEdge(java.lang.String,java.lang.String)
[addConditionalEdges]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html#addConditionalEdges(java.lang.String,org.bsc.langgraph4j.action.AsyncCommandAction,java.util.Map)
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[Checkpointers]: /langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/BaseCheckpointSaver.html
[graph.updateState(config,values,asNode)]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#updateState(org.bsc.langgraph4j.RunnableConfig,java.util.Map,java.lang.String)
[graph.getStateHistory(config)]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#getStateHistory(org.bsc.langgraph4j.RunnableConfig)
[CompileConfig]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompileConfig.html
[BaseCheckpointSaver]: /langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/BaseCheckpointSaver.html
[graph.getGraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#getGraph(org.bsc.langgraph4j.GraphRepresentation.Type(java.lang.String)
[GraphResult]: /langgraph4j/apidocs/org/bsc/langgraph4j/GraphResult.html
[NodeOutput]: /langgraph4j/apidocs/org/bsc/langgraph4j/NodeOutput.html
[BaseCheckpointSaver.Tag]: /langgraph4j/apidocs/org/bsc/langgraph4j/checkpoint/BaseCheckpointSaver.Tag.html
[execute]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#execute(java.util.Map,org.bsc.langgraph4j.RunnableConfig)
[stream]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#stream(java.util.Map,org.bsc.langgraph4j.RunnableConfig)
[RunnableConfig]: /langgraph4j/apidocs/org/bsc/langgraph4j/RunnableConfig.html
[AsyncNodeActionWithConfig]: /langgraph4j/apidocs/org/bsc/langgraph4j/action/AsyncNodeActionWithConfig.html
