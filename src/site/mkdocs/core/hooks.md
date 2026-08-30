
# Graph Hooks

Hooks in LangGraph4j provide a powerful mechanism to intercept the execution of graph nodes and edges. They allow you to run custom code before, after, or around the core logic of a node or a conditional edge. This is particularly useful for tasks such as:

*   Logging and tracing execution flow.
*   Collecting metrics and performance data.
*   Modifying the state between steps.
*   Implementing custom debugging tools.
*   Altering the control flow of the graph dynamically.

## Hook Interfaces

There are two main interfaces for hooks:

*   `org.bsc.langgraph4j.hook.NodeHook`: For instrumenting graph nodes.
*   `org.bsc.langgraph4j.hook.EdgeHook`: For instrumenting conditional edges (since only they have an associated function).

## Hook Types

Both `NodeHook` and `EdgeHook` support three distinct features, allowing for fine-grained control over the execution interception:

*   **`BeforeCall`**: This hook is executed *before* the node or edge function is called. It receives the current `nodeId`, `AgentState` and `RunnableConfig`.
*   **`AfterCall`**: This hook is executed *after* the node or edge function completes. It receives the `nodeId`, `AgentState`, `RunnableConfig`, and the result of the previous function call.
*   **`WrapCall`**: This hook "wraps" the execution of the node or edge function. It allows you to execute code both before and after the function call, and even to decide whether to call the original function at all. It receives the `nodeId`, `AgentState`, `RunnableConfig` and the action to execute.

### Node Hooks

Below the available node hooks  
```java
public interface NodeHook {
    @FunctionalInterface
    interface BeforeCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyBefore( String nodeId, State state, RunnableConfig config );
    }
    @FunctionalInterface
    interface AfterCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyAfter( String nodeId, State state, RunnableConfig config, Map<String, Object> lastResult ) ;
    }
    @FunctionalInterface
    interface WrapCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyWrap( String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action);
    }
}
```

### Edge Hooks
Below the available edge hooks  
```java
public interface EdgeHook {
    @FunctionalInterface
    interface BeforeCall<State extends AgentState> {
        CompletableFuture<Command> applyBefore( String sourceId, State state, RunnableConfig config );
    }
    @FunctionalInterface
    interface AfterCall<State extends AgentState> {
        CompletableFuture<Command> applyAfter( String sourceId, State state, RunnableConfig config, Command lastResult ) ;
    }
    @FunctionalInterface
    interface WrapCall<State extends AgentState> {
        CompletableFuture<Command> applyWrap( String sourceId, State state, RunnableConfig config, AsyncCommandAction<State> action);
    }
}
```
## Registering Hooks

You can add hooks to a `StateGraph` instance in two ways:

*   **Global Hooks**: These hooks apply to *all* nodes or conditional edges in the graph.
*   **ID-Specific Hooks**: These hooks apply only to a *specific* node or conditional edge, identified by its unique ID.

You can register multiple hooks, both global and specific.

Here are the methods available on the `StateGraph` class for adding hooks:

```java
// Node Hooks
StateGraph<State> addBeforeCallNodeHook(NodeHook.BeforeCall<State> hook)
StateGraph<State> addBeforeCallNodeHook(String nodeId, NodeHook.BeforeCall<State> hook)
StateGraph<State> addAfterCallNodeHook(NodeHook.AfterCall<State> hook)
StateGraph<State> addAfterCallNodeHook(String nodeId, NodeHook.AfterCall<State> hook)
StateGraph<State> addWrapCallNodeHook(NodeHook.WrapCall<State> hook)
StateGraph<State> addWrapCallNodeHook(String nodeId, NodeHook.WrapCall<State> hook)

// Edge Hooks
StateGraph<State> addBeforeCallEdgeHook(EdgeHook.BeforeCall<State> hook)
StateGraph<State> addBeforeCallEdgeHook(String edgeId, EdgeHook.BeforeCall<State> hook)
StateGraph<State> addAfterCallEdgeHook(EdgeHook.AfterCall<State> hook)
StateGraph<State> addAfterCallEdgeHook(String edgeId, EdgeHook.AfterCall<State> hook)
StateGraph<State> addWrapCallEdgeHook(EdgeHook.WrapCall<State> hook)
StateGraph<State> addWrapCallEdgeHook(String edgeId, EdgeHook.WrapCall<State> hook)
```

## Execution Order

When multiple hooks are registered for the same node or edge, they are executed in a specific order:

*   **`BeforeCall` Hooks**: Executed in a **LIFO** (Last-In, First-Out) order. The last hook added is the first one to be executed.
*   **`AfterCall` Hooks**: Also executed in a **LIFO** order.
*   **`WrapCall` Hooks**: Executed in a **FIFO** (First-In, First-Out) order. The first hook added is the first one to wrap the action, meaning its "before" logic runs first, and its "after" logic runs last.

## Retrying Nodes

`RetryPolicy` is a node-specific `WrapCall` hook for retrying transient failures. Failed attempts do not reach state merging or checkpointing; the graph continues only when the node action succeeds.

```java
var retry = RetryPolicy.builder()
        .maxAttempts(3)
        .retryDelay(Duration.ofMillis(500))
        .backoffFactor(2.0)
        .retryOn(IOException.class, TimeoutException.class)
        .retryOn(error -> error instanceof RemoteServiceException)
        .build();

var graph = new StateGraph<>(State.SCHEMA, State::new)
        .addNode("call_api", callApi)
        .addEdge(START, "call_api")
        .addEdge("call_api", END)
        .addWrapCallNodeHook("call_api", retry.asHook());
```

`retryOn(...)` is required. It accepts one or more exception classes or a predicate; multiple calls are combined with OR.

The other builder options are optional:

* `maxAttempts(int)` defaults to `3` and includes the initial invocation.
* `retryDelay(Duration)` defaults to `500 ms` and is the delay before the first retry.
* `backoffFactor(double)` defaults to `2.0`; each following retry delay is multiplied by this factor.

Apply retry policies only to idempotent operations, or operations that have their own idempotency key, to avoid repeated side effects.

## Code Examples

See [Hooks samples notebook](how-tos/hooks.ipynb)
