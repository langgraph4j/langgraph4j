# Subgraphs

A subgraph is a graph that is used as a node in another graph. This is nothing more than a concept of encapsulation and composition, applied to LangGraph4j. 


## Reasons
Some reasons for using subgraphs are:

* building **multi-agent systems** 
* when you want to **reuse a set of nodes** in multiple graphs, which maybe share some state, you can define them once in a subgraph and then use them in multiple parent graphs
* when you want **different teams to work on different parts** of the graph independently, you can define each part as a subgraph, and as long as the subgraph interface (the input and output schemas) is respected, the parent graph can be built without knowing any details of the subgraph

## How to add subgraphs

There are three ways to add subgraphs to a parent graph:

* add a node with the **state subgraph**:
    this is useful when the parent graph and the subgraph are strictly relate one to other, sharing everithing.
    In particular the **subgraph is merged with its parent** creating a seamless integration.

    ```java
    stateGraph.addNode( "subgraph", workflowChild );
    ```

* add a node with the **compiled subgraph**: 
    this is useful when the parent graph and the subgraph share state. 
    
    ```java
    stateGraph.addNode( "subgraph", workflowChild.compile() );
    ```

* add a node with in a node action (ie. [`AsyncNodeAction`][action] ) that invokes the subgraph: 
    this is useful when the parent graph and the subgraph have different state schemas and you need to transform state before or after calling the subgraph
    
    ```java
    StateGraph.addNode( "subgraph",  state -> {
        var input =  Map.<String,Object>of( "subgraphKey", state.lastMessage().orElseThrow() );
        return compiledWorkflowChild.stream( input )
            .forEachAsync( System.out::println )
            .thenApply( ( res ) -> state.data() );
        });
    ```

## As a state graph

This is the most effective way to create subgraph nodes is by using a state subgraph. When doing so, the subgraph is merged into the parent one becoming a integral part of it, as conseguence it is important keep note that **the parent graph and the subgraph share everithing like one single graph**.

**Note**
_The merging happens during graph compilation and the subgraphs nodes are renamed to avoid clashing. From outside of graph to get the real name of merge subgraph nodes to must use_ [`SubGraphNode.formatId( subgraphId, subgraphNodeId )`][formatid]

see [example][state_sample]

## As a compiled graph

The simplest way to create subgraph nodes is by using a compiled subgraph directly. When doing so, it is important that **the parent graph and the subgraph state schemas share at least one key which they can use to communicate**. If your graph and subgraph do not share any keys, you should use write an action (ie. [`AsyncNodeAction`][action] ) invoking the subgraph instead (see below).

**Note**
_If you pass extra keys to the subgraph node (i.e., in addition to the shared keys), they will be ignored by the subgraph node. Similarly, if you return extra keys from the subgraph, they will be ignored by the parent graph._

see [example][compiled_sample]

## As a node action

You might want to define a subgraph with a completely different schema. In this case, you can create a node function that invokes the subgraph. This function will need to transform the input (parent) state to the subgraph state before invoking the subgraph, and transform the results back to the parent state before returning the state update from the node.

see [example][node_sample]


## Visualization

It's often important to be able to visualize graphs, especially as they get more complex. LangGraph4j comes with [`StateGraph.getGraph()`][sg_getgraph] method to get a visualization format (ie. diagram-as-a-code representation [PlantUML],[Mermaid.js]): 

**Note**
Only in the case of  **state subgraph** visualization, since adding it implies its merging into parent, if you call [`StateGraph.getGraph()`][sg_getgraph] you will got a visualization format before merging process, while if you call [`CompiledGraph.getGraph()`][cg_getgraph] you got a visualization format after merging process.

## Streaming

In the  adding both **state subgraph** and **compiled subgraph** the streaming is automatically enabled, while in the **subgraph as node action** the streaming is up to you


[formatid]: /langgraph4j/apidocs/org/bsc/langgraph4j/SubGraphNode.html#formatId
[action]: /langgraph4j/apidocs/org/bsc/langgraph4j/action/AsyncNodeAction.html
[state_sample]: /langgraph4j/how-tos/subgraph-as-stategraph
[compiled_sample]: /langgraph4j/how-tos/subgraph-as-compiledgraph
[node_sample]: /langgraph4j/how-tos/subgraph-as-nodeaction
[sg_getgraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/StateGraph.html#getGraph
[cg_getgraph]: /langgraph4j/apidocs/org/bsc/langgraph4j/CompiledGraph.html#getGraph
[plantUML]: https://plantuml.com
[Mermaid.js]: https://mermaid.js.org