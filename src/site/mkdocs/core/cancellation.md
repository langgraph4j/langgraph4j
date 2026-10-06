# LangGraph4j - Graph Execution Cancellation

Graph streams execute eagerly: the graph can continue running while its outputs are queued. Stopping a `for-each` loop or abandoning an asynchronous consumer does not cancel graph execution. Call `cancel(boolean mayInterruptIfRunning)` when the caller no longer needs the result.

## Cancelling a Graph Stream

Both `stream` and `streamSnapshots` return a cancellable generator.

- `cancel(false)` stops the graph before it starts another node. The current node may finish.
- `cancel(true)` also requests interruption of the graph execution thread. A node's external operation or future may not support interruption; cancellation does not guarantee that an already running operation stops.

After cancellation, the generator stops delivering buffered outputs and completes with a `CANCELLED` result. Cancelling a stream after its final result has already been consumed preserves that result.

```java
var generator = workflow.stream(GraphInput.args(Map.of()), RunnableConfig.builder().build());
var completion = generator.forEachAsync(output -> System.out.println(output))
        .thenApply(GraphResult::from);

// For example, request cancellation when the client disconnects.
generator.cancel(false);

var result = completion.join();
if (result.isCancelled()) {
    System.out.println("Graph execution was cancelled");
}
```

Explicit cancellation also ends an iterator's loop. Simply stopping iteration still leaves the eager graph running.

## Subgraphs and Parallel Execution

Cancelling a parent graph propagates to its compiled subgraphs, including subgraphs in parallel branches. They stop before starting another node, and the parent does not proceed to its next node.

Work already started in a parallel node may finish. Cancellation does not shut down an executor supplied by the caller or forcibly cancel external services.

If an embedded generator returns `CANCELLED`, the parent ends with that result instead of merging it into the state or evaluating the next edge.

## Checkpoints

Cancellation retains active checkpoints instead of releasing the thread as a normally completed graph. Use the same thread configuration with `GraphInput.resume()` to continue from the retained checkpoint. A running node may have completed and saved its checkpoint before cancellation takes effect.

## Checking Cancellation

```java
if (generator.isCancelled()) {
    // A cancellation request was accepted for this generator.
}
```

`LG4JCancellationTest` covers graph and snapshot streams, compiled and parallel subgraphs, asynchronous consumers, and checkpoint recovery using controlled futures and synchronization signals.

## Further Reading

See the [java-async-generator cancellation documentation](https://github.com/bsorrentino/java-async-generator/blob/main/CANCELLATION.md) for the underlying generator mechanism.
