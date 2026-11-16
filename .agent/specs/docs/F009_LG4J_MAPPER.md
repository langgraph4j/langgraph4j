# F008 - Document the new feature Input/Output Mapper

## Instructions

LangGraph4j has introduced a new feature called Input/Output Mapper. This feature allows users to define custom mappings for the input and output of their agents, that is particular useful when dealing with subgraphs.

I want that you add documentation of this new feature in the official documentation of LangGraph4j and in particular in src/site/mkdocs/core/core-library.md file.

To document feature you must take a look to :

* The issue [#306](https://github.com/langgraph4j/langgraph4j/issues/306) where the feature was discussed.
* Source code langgraph4j-core/src/main/java/org/bsc/langgraph4j/StateGraph.java where there are the new method:
  * `withInputMapper(Function<GraphInput, GraphInput> inputMapper)`
  * `withOutputMapper(Function<GraphOutput, GraphOutput> outputMapper)`
* Method `testWithOutputMapper` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/CompiledSubGraphTest.java where there is a test that shows how to use the new feature.
* Methods `testOutputMapper` and `testInputMapper` in langgraph4j-core/src/test/java/org/bsc/langgraph4j/LG4JTest.java where there are the new methods that allow to set the input and output mappers.


