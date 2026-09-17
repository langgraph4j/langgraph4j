# Emit custom output from a node

## Graph output is a stream

Graph execution produces output incrementally. The `graph.stream(...)` method returns an
`AsyncGenerator` and yields a `NodeOutput` as the graph progresses: the initial `START`
output, the output produced after each node, and the final `END` output. You can consume
the generator synchronously with a `for` loop or asynchronously with `forEachAsync`.

This streaming model also lets a node publish additional output while it is executing.
Those values are delivered through the same stream, before the node returns its ordinary
state update. This is useful for progress notifications, intermediate results, or custom
events that should be visible to the caller without adding them to the graph state.

## Custom `NodeOutput`

To emit a custom value, create a subclass of `NodeOutput<State>`. The subclass can carry
additional fields while retaining the node name and state available on every graph output.
For example:

```java
public final class ElapsedOutput extends NodeOutput<State> {
    private final Duration elapsed;

    public ElapsedOutput(String node, State state, Instant start) {
        super(node, state);
        this.elapsed = Duration.between(start, Instant.now());
    }

    public Duration elapsed() {
        return elapsed;
    }
}
```

The node obtains a typed dispatcher from its `RunnableConfig` and sends the custom output
to the active graph stream. `dispatchSync` waits until the value has been accepted by the
stream; `dispatchAsync` submits it without waiting.

```java
AsyncNodeActionWithConfig<State> process = (state, config) -> {
    
    final var dispatcher = config.<State, ElapsedOutput>customDispatcher();

    final var start = Instant.now();

    // Perform work and optionally emit more progress. The example reuses the
    // current state; an application can provide a state instance with its
    // intermediate values instead.
    
    dispatcher.dispatchSync(
            new ElapsedOutput(config.nodeId(), state, startTime));

    // The returned map is still the node's normal state update.
    return completedFuture(Map.of("result", "....."));
};
```

## Reading custom values from `graph.stream`

Custom outputs are emitted in order with the regular graph outputs. A consumer can inspect
the common `NodeOutput` API and use `instanceof` or pattern matching for the custom type:

```java
for (NodeOutput<State> output : graph.stream(GraphInput.noArgs(), RunnableConfig.empty())) {
    if (output instanceof ElapsedOutput customOutput) {
        System.out.printf("Elapsed time for node %s: (%dms)%n", customOutput.node(), customOutput.elapsed().toMills());
    } else {
        System.out.println("Graph step: " + output.node());
    }
}
```

For a graph containing one `process` node, the sequence can be:

```text
START
Elapsed time for node 'process': (1500ms)" // custom output
process                 // the normal output after the node returns
END
```

The exact state in each output is the state supplied when that output is created. Custom
outputs do not update or replace graph state; the map returned by the node continues to be
the state update that drives the graph. If an output should affect routing or later nodes,
return the corresponding state values as part of the node result as well.

## Dispatcher availability and error handling

The custom dispatcher is installed only while a node is being run as part of a graph
stream. Calling `customDispatcher()` outside that execution context raises an
`IllegalStateException`. Keep the dispatcher use inside the node action and do not retain it
for use after the action has completed.

`dispatchSync` may throw `InterruptedException`. A node should preserve interruption
semantics and complete or fail its action accordingly. `dispatchAsync` does not throw for
the dispatch operation, but the graph stream can still fail if the graph execution itself
raises an exception.

This feature is intended for `graph.stream(...)` consumers. It does not turn custom output
into persisted state or change the value returned by `graph.invoke(...)`; use the regular
state update when the value must be part of the graph result.

## Hooks

**CustomDispatcher()** is also available in graph [hooks](hooks.md) see example below:

```java
public final class ElapsedNodeHook implements NodeHook.WrapCall<State> {

    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

        final var dispatcher = config.<State, ElapsedOutput>customDispatcher();

        final var start = Instant.now();

        return action.apply(state, config)
                .whenComplete((result, exception) -> {

                    if (exception == null) {
                        dispatcher
                                .dispatchAsync(new ElapsedOutput(config.nodeId(), state, start));
                    }
                });
    }

}

```

```java
var graph = new StateGraph<>( State::new )
                    .addAfterCallNodeHook( new ElapsedNodeHook() ) // this apply hook to all nodes belong to the graph
                    .addNode( "task1", task1Action )
                    .addNode( "task2", task2Action )
                    .addEdge( START, "task1" )
                    .addEdge( "task1", "task2" )
                    .addEdge( "task2", END )
```
