# F003 Graph hooks guide

## Instructions

Write a tecnical documentation relate to Hooks usage in LangGraph4j. The examples are in the source file @langgraph4j-core/src/test/java/org/bsc/langgraph4j/GraphTest.java in the methods `testNestedNodeWrapHooks` and `testNestedNodeAndEdgeWrapHooks` 
Hooks implementation details are in the following source files:

1. @langgraph4j-core/src/main/java/org/bsc/langgraph4j/hook/NodeHook.java - This is the interface that define the Hooks functions relate to the graph's nodes
2. @langgraph4j-core/src/main/java/org/bsc/langgraph4j/hook/EdgeHook.java - This are the interface that define the Hooks functions relate to the graph's conditional edge the only one having an associated function
3. @langgraph4j-core/src/main/java/org/bsc/langgraph4j/StateGraph.java - This is the `StateGraph` class that expose the methods to add hooks

Important to take note that the Hooks are available with three different features either for Nodes and Edges:

* Before a Call
* After a Call 
* Wrap a Call 

The Hooks can be added either as a global hook, that affect all nodes or conditiona edges, then by Id so you can add an hook on a specific node or conditional edge

You can specify multiple hooks for both nodes and edges either global than specific. when there are multiple hooks their execution are arranged in the following way:

* Berfore Call hooks are executed using a LIFO (last Input - Last Output ) strategy
* After Call hooks are executed using a LIFO (last Input - Last Output ) strategy
* Wrap Call hooks are executed using a FIFO ( Firt Input - First Out ) strategy 

