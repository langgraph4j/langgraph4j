# LangGraph4j support for AG-UI

This module provides the building blocks to expose a [LangGraph4j](https://github.com/langgraph4j/langgraph4j) graph
as an agent compliant with the [AG-UI protocol](https://github.com/ag-ui-protocol/ag-ui), streaming its execution as
a sequence of AG-UI events (message chunks, step lifecycle, run lifecycle, errors, etc.).

## Classes overview

### AGUIAgent 
Core interface representing an AG-UI compliant agent. Exposes a unique `id()` and a `run(RunAgentInput)` method returning a `Flow.Publisher<Event>` that streams the AG-UI events produced by the agent execution.

### AGUIAgentBase

Abstract base implementation of `AGUIAgent` that bridges a LangGraph4j `CompiledGraph` to the AG-UI event stream. It lazily compiles the graph (via the abstract `newGraph()` method), builds the graph input from the incoming request (via the abstract `graphInput(RunAgentInput)` method), runs it either asynchronously or synchronously, and translates node/streaming outputs, errors and completion into the corresponding AG-UI events. 
Subclasses typically only need to implement the two abstract methods.

### AGUIAgentRegistry
A simple registry (`Map<String, AGUIAgent>`) used to look up a registered `AGUIAgent` by its identifier, typically used to dispatch an incoming AG-UI run request to the appropriate agent implementation.

### AGUIHook
A LangGraph4j `NodeHook` implementation dedicated to the AG-UI protocol.
Its `stepEvents()` factory method returns a `NodeHook.WrapCall` that automatically dispatches the AG-UI `StepStartedEvent` before a node runs and the `StepFinishedEvent` after it completes successfully, without requiring manual event handling in the node's own logic.

### AGUINodeOutput
A custom LangGraph4j `NodeOutput` implementation that acts as a bridge between LangGraph4j's
output streaming and AG-UI events. It carries a list of AG-UI `Event`s (e.g. text message events, step lifecycle
events) produced by a node, which are then forwarded to the AG-UI subscriber. Instances are created through its
fluent `Builder` (`AGUINodeOutput.builder()`).

### AGUIRunAgentInput
A mutable, bean-style wrapper around AG-UI's `RunAgentInput` (thread id, run id, tools,
context, messages, forwarded properties, resume, state), convenient for deserialization frameworks. It exposes
`toRunAgentParameters()` to convert itself into an immutable `RunAgentInput` instance.

