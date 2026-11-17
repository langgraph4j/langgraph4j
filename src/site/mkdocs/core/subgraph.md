# Subgraphs

A subgraph is a graph that is used as a node in another graph. This is nothing more than a concept of encapsulation and composition, applied to LangGraph4j. 


## Reasons
Some reasons for using subgraphs are:

* building **multi-agent systems** 
* when you want to **reuse a set of nodes** in multiple graphs, which maybe share some state, you can define them once in a subgraph and then use them in multiple parent graphs
* when you want **different teams to work on different parts** of the graph independently, you can define each part as a subgraph, and as long as the subgraph interface (the input and output schemas) is respected, the parent graph can be built without knowing any details of the subgraph

## How to add subgraphs

There are three ways to add subgraphs to a parent graph:


* Add a node with the **compiled subgraph**.
  
  This is useful when the parent graph and the subgraph share state. 
    
    ```java
    stateGraph.addNode( "subgraph", workflowChild.compile() );
    ```

* Add a node with in a node action (ie. [`AsyncNodeActionWithConfig`][action] ) that invokes the subgraph.

  This is useful when the parent graph and the subgraph have different state schemas and you need to transform state before or after calling the subgraph
    
    ```java
    StateGraph.addNode( "subgraph",  (state, config) -> {

           final var input =  GraphInput.args(state.data());

           return compiledWorkflowChild.stream( input, RunnableConfig.empty() )
               .forEachAsync( System.out::println )
               .thenApply( res -> state.data() );

        })

    ```

* Add a node with the **state subgraph**:

  this is useful when the parent graph and the subgraph are strictly relate one to other, sharing everithing. In particular the **subgraph is merged with its parent** creating a seamless integration.

    ```java
    stateGraph.addNode( "subgraph", workflowChild );
    ```

## As a compiled graph

The recomended (_simplest_) way to create subgraph nodes is by using a compiled subgraph directly. The parent and child can communicate through shared state keys, or through [input/output mappers](#inputoutput-mappers) that translate keys and values at the child boundary. A [node action](#as-a-node-action) is useful when you need to control the invocation yourself.

### Note
* A schema defines channels, defaults, and reducers; it does not automatically filter all undeclared keys. Use input/output mappers to select the fields exchanged with a compiled subgraph.
* Interruptions are supported 

see [example][compiled_sample]


## Input/Output Mappers

Since **1.9.10**, `StateGraph.withInputMapper(...)` and `StateGraph.withOutputMapper(...)` let you define a graph's input and output boundary. Both accept a `Function<Map<String, Object>, Map<String, Object>>` and return the same builder. Configure them on the child **before compiling it**, then pass the compiled child to `addNode(...)`. This keeps the subgraph visible to graph visualization and preserves its built-in streaming and interruption support, without a wrapper action.

| Mapper | When it runs | Typical use |
| --- | --- | --- |
| `withInputMapper(...)` | At the start of a fresh invocation, after state initialization | Select parent fields the child needs, rename keys, or transform values. Schema defaults are also visible to the mapper. |
| `withOutputMapper(...)` | On normal completion, including the checkpoint whose next node is `END` | Return only public results, transform values, or explicitly request removal of parent fields. |

Each mapper returns the complete map for that side of the boundary. An unset mapper passes state through unchanged; passing `null` to a setter disables that mapper. A mapper returning `null` produces an empty map. Return a new map rather than mutating the supplied state, and keep mappers free of side effects: output mapping can run for both the completion checkpoint and the final result.

Input mapping is not reapplied when resuming from a checkpoint. Output mapping does not filter intermediate streamed node states or earlier checkpoints, and an interruption is not completed output. Internal fields can still appear there. See the [core mapper guide](core-library.md#inputoutput-mapper) for standalone usage and persistence details, and [issue #306][issue306] for the design discussion.

### Map a compiled child's input and output

The following example uses `AgentState`, `Channels`, `GraphInput`, `RunnableConfig`, and `StateGraph` from LangGraph4j, `ArrayList`, `List`, and `Map` from `java.util`, and these static imports:

```java
// Use an appender so the parent's messages accumulate the child's mapped messages.
var schema = AgentState.Schema.of(Map.of(
        "messages", Channels.appender(ArrayList::new)));

var child = new StateGraph<>(schema, AgentState::new)
        // Copy only messages into the child; keep_me stays outside its working state.
        .withInputMapper(state -> {
            var input = new java.util.LinkedHashMap<>(state);
            input.keySet().retainAll(java.util.Set.of("messages"));
            return input;
        })
        // Publish a transformed result and one message, excluding the scratchpad.
        .withOutputMapper(state -> Map.of(
                "sent_back", "no", "messages", List.of("child")))
        // Produce internal working data and a value the output mapper will replace.
        .addNode("only", node_async(state -> Map.of(
                "sent_back", "yes", "scratchpad", "internal work")))
        .addEdge(START, "only")
        .addEdge("only", END)
        .compile();

var parent = new StateGraph<>(schema, AgentState::new)
        .addNode("child", child)
        // Add a parent message after the child's mapped output has been merged.
        .addNode("after", node_async(state -> Map.of("messages", "after")))
        .addEdge(START, "child")
        .addEdge("child", "after")
        .addEdge("after", END)
        .compile();

// Start a fresh run with both shared data and a parent-only field.
var result = parent.invokeSync(GraphInput.args(Map.of(
        "keep_me", "parent-value", "messages", "parent")), RunnableConfig.empty());

System.out.println(result.asStateData());
// Contains keep_me=parent-value, sent_back=no, messages=[parent, child, after].
// The child's scratchpad is absent.
```

The child's mapped output is **merged into the existing parent state using the parent's channels and reducers**. Omitted parent keys retain their values; an output mapper returning only `sent_back` would also preserve `keep_me`. Returned message values pass through the parent's appender. This behavior is covered by [`CompiledSubGraphTest.testWithOutputMapper`][mapper_test]; standalone input and output filtering are covered by [`LG4JTest.testInputMapper` and `testOutputMapper`][standalone_tests].

This boundary applies to **compiled subgraphs**. With `addNode("child", childStateGraph)`, the uncompiled child's nodes are merged into the parent at compilation, so the child's mappers do not run as a separate invocation boundary.

### Breaking change: propagate removals explicitly

‼️⚠️ warning "BREAKING CHANGE: subgraph output is merged into parent state"

Before the Input/Output Mapper implementation, the completed subgraph state was treated as the source of truth and assigned as-is to the parent. Now the child result is merged with the parent state. A key missing from the child result no longer removes that key from the parent, even when a child node removed it with `AgentState.MARK_FOR_REMOVAL`.

A child node's removal marker is consumed while updating the child's state, leaving the key absent from its completed result. To remove the same key from the parent, **add the marker back in the child's output mapper**. Filtering a key out of the output only prevents an update to it; it does not delete the parent's existing value. This changes the behavior of applications that relied on the previous fix for [issue #260][issue260].

```java
var removalChild = new StateGraph<>(AgentState.Schema.empty(), AgentState::new)
        // Copy the completed state and reinsert the removal marker for the parent merge.
        .withOutputMapper(state -> AgentState.markKeysForRemoval(state, "my_value"))
        // Remove the value locally; this marker is consumed inside the child.
        .addNode("remove_value", node_async(state ->
                Map.of("my_value", AgentState.MARK_FOR_REMOVAL)))
        .addEdge(START, "remove_value")
        .addEdge("remove_value", END)
        .compile();

var removalParent = new StateGraph<>(AgentState.Schema.empty(), AgentState::new)
        .addNode("child", removalChild)
        .addEdge(START, "child")
        .addEdge("child", END)
        .compile();

// The explicit output marker removes my_value from the parent as well as the child.
var removalResult = removalParent.invokeSync(GraphInput.args(Map.of(
        "my_value", "parent-value", "keep_me", "unchanged")), RunnableConfig.empty());

System.out.println(removalResult.asStateData()); // {keep_me=unchanged}
```

`AgentState.markKeysForRemoval(state, keys...)` returns a copy of the supplied map with each named key set to `MARK_FOR_REMOVAL`, preserving other outputs. Only mark keys that should be removed from the parent. For multiple keys, use `state -> AgentState.markKeysForRemoval(state, "secret", "internal_data")`. If removal is conditional, add markers only when the corresponding removal is intended.

The same rule applies when a child hook removes fields before its first node: the hook removes them locally, and the output mapper propagates their removal to the parent. See [`Issue306Test.experimentB2_childFirstNodeBeforeCallOnly`][removal_hook_test] and [`Issue260Test.getCompiledGraph`][removal_test] for examples, including use with a checkpoint saver.

## As a node action

You might want to **define a subgraph with a completely different schema**. In this case, you can create a node function that invokes the subgraph. This function will need to transform the input (parent) state to the subgraph state before invoking the subgraph, and transform the results back to the parent state before returning the state update from the node.

### Note
* Interruption support is up to you

see [example][node_sample]

## As a state graph

This is the most naif way to create subgraph nodes is by using a state subgraph. When doing so, the subgraph is merged into the parent one becoming a integral part of it, as conseguence it is important keep note that **the parent graph and the subgraph share everithing like one single graph**.

### Note
* The merging happens during graph compilation and the subgraphs nodes Ids are renamed to avoid clashing. From outside of graph to get the real Id of merge subgraph nodes to must use[`SubGraphNode.formatId( subgraphId, subgraphNodeId )`][formatid]
* Interruptions are supported passing the renamed subgraph node Id got from [`SubGraphNode.formatId( subgraphId, subgraphNodeId )`][formatid]

see [example][state_sample]


## Visualization

It's often important to be able to visualize graphs, especially as they get more complex. LangGraph4j comes with [`StateGraph.getGraph()`][sg_getgraph] method to get a visualization format (ie. diagram-as-a-code representation [PlantUML],[Mermaid.js]): 

### Note
Only in the case of  **state subgraph** visualization, since adding it implies its merging into parent, if you call [`StateGraph.getGraph()`][sg_getgraph] you will got a visualization format before merging process, while if you call [`CompiledGraph.getGraph()`][cg_getgraph] you got a visualization format after merging process.

## Streaming

In the  adding both **state subgraph** and **compiled subgraph** the streaming is automatically enabled, while in the **subgraph as node action** the set up of streaming is up to you.


[formatid]: /langgraph4j/apidocs/org/bsc/langgraph4j/SubGraphNode.html#formatId
[action]: /langgraph4j/apidocs/org/bsc/langgraph4j/action/AsyncNodeActionWithConfig.html
[state_sample]: /langgraph4j/how-tos/subgraph-as-stategraph
[compiled_sample]: /how-tos/subgraph-as-compiledgraph
[node_sample]: /how-tos/subgraph-as-nodeaction
[sg_getgraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html#getGraph
[cg_getgraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#getGraph
[issue306]: https://github.com/langgraph4j/langgraph4j/issues/306
[issue260]: https://github.com/langgraph4j/langgraph4j/issues/260
[mapper_test]: https://github.com/langgraph4j/langgraph4j/blob/develop/langgraph4j-core/src/test/java/org/bsc/langgraph4j/CompiledSubGraphTest.java
[standalone_tests]: https://github.com/langgraph4j/langgraph4j/blob/develop/langgraph4j-core/src/test/java/org/bsc/langgraph4j/LG4JTest.java
[removal_hook_test]: https://github.com/langgraph4j/langgraph4j/blob/develop/langgraph4j-core/src/test/java/org/bsc/langgraph4j/Issue306Test.java
[removal_test]: https://github.com/langgraph4j/langgraph4j/blob/develop/langgraph4j-core/src/test/java/org/bsc/langgraph4j/Issue260Test.java
[plantUML]: https://plantuml.com
[Mermaid.js]: https://mermaid.js.org
