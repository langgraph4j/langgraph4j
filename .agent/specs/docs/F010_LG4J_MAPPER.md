# F010 - Document the new feature Input/Output Mapper in subgraph context

## Instructions

LangGraph4j has introduced a new feature called Input/Output Mapper. This feature allows users to define custom mappings for the input and output of their agents, that is particular useful when dealing with subgraphs.

I want that you add documentation of this new feature in the official documentation of LangGraph4j and in subgraph related  `src/site/mkdocs/core/subgraph.md` page.

To document feature you must take a look to :

* The issue [#306](https://github.com/langgraph4j/langgraph4j/issues/306) where the feature was discussed.
* Source code langgraph4j-core/src/main/java/org/bsc/langgraph4j/StateGraph.java where there are the new method:
  * `withInputMapper(Function<GraphInput, GraphInput> inputMapper)`
  * `withOutputMapper(Function<GraphOutput, GraphOutput> outputMapper)`
* Method `testWithOutputMapper` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/CompiledSubGraphTest.java where there is a test that shows how to use the new feature.
* Methods `testOutputMapper` and `testInputMapper` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/LG4JTest.java where there are the new methods that allow to set the input and output mappers.

Moreover I want that you highlight the fact that the Input/Output Mapper implementation introduced a BREAKING CHANGE in how the subgraph propagate a `MARK_FOR_REMOVAL` request to the parent graph (refers to [issue #260](https://github.com/langgraph4j/langgraph4j/issues/260)
Before the Input/Output Mapper implementation, the subgraph result was assumed as source of truth and assigned "as-is" as parent result.
Now the subgraph result is merged with parent result and the only way to propagate `MARK_FOR_REMOVAL` requests  is to adding them using an Output Mapper as showed in:

* method `experimentB2_childFirstNodeBeforeCallOnly` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/Issue306Test.java
* method `getCompiledGraph` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/Issue260Test.java


