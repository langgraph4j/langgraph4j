# F004 Interrupt Graph using Excepiton

## Instructions

Write a tecnical documentation relate to Graph interruption usage in LangGraph4j adding it in file @src/site/mkdocs/core/core-library.md under the parapgraph `### Use the GraphInterruptException`.

The main class is `GraphInterruptException` in file @langgraph4j-core/src/main/java/org/bsc/langgraph4j/GraphInterruptException.java this is a built-in exception that could be raised by a Node Action to inform the Graph engine that the Graph must be interrupted.
It is important take note that this will be not considered as an error but as a classical interruption request that can be safely resumed later.

Add also a section **Here’s how it works** where put all the steps performed by the graph engine in handlind exception and tranform it in a `InterruptionMetadata` object, the code is in @langgraph4j-core/src/main/java/org/bsc/langgraph4j/CompiledGraph.java take a look to `Emitter.accept(AsyncGeneratorFlow.Dispatcher<Output> )` method. 

How to use example:

```java
// From a NodeAction
public Map<String, Object> apply(T state) throws Exception {
    throw new GraphInterruptException(config, "Interrupting with exception!");
}

// From an AsyncNodeAction
public CompletableFuture<Map<String, Object>> apply(T state, RunnableConfig config) {
    return failedFuture(GraphInterruptException(config, "Interrupting with exception!"));
}
```
