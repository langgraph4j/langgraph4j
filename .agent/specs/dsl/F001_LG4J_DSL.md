# F001 Create a custom  Langgraph4j DSL


## Introduction 

Concerning the project Langgraph4j it creates agentic architecture graph based.

Currently the framework is able to generate from graph definition (code based) the plantUML and Mermaid script. 

To do this the main class is @langgraph4j-core/src/main/java/org/bsc/langgraph4j/DiagramGenerator.java that has two
subclasses
* @langgraph4j-core/src/main/java/org/bsc/langgraph4j/diagram/MermaidGenerator.java for generate Mermaid script.
* @langgraph4j-core/src/main/java/org/bsc/langgraph4j/diagram/PlantUMLGenerator.java for generate PlantUML script.


To start the process is involved @langgraph4j-core/src/main/java/org/bsc/langgraph4j/CompiledGraph.java and in
particular the method `reduce( Reducer<State, Output> reducer)` where reducer is essentially a
`BiFunction<Nodes<State>,Edges<State>,Output>` interface that consume `nodes` and `edges` that represent
graph definition and produce a generic output that depends on needs.

## DSL generation

Idea is to reuse such code infrastructure to create a DSL (Domain Specifi Language) JSON based that represent the graph.

Important consideration is that this DSL must be thought to be consumed by a react-flow based component to provide a
meaningful graphic representation.

Proceed to implement a new method in `CompiledGraph` class name `toJSON()` that provide such representation as json
formatted `String`.

You must take also in consideration the possibility to represent subgraph.

## Implementation summary

Implemented `CompiledGraph.toJSON()` by introducing a reducer-style `LangGraphJsonGenerator` that emits a JSON DSL
with top-level `type`, `version`, `nodes`, `edges`, and `subgraphs` fields.
The generated nodes and edges are shaped for React Flow consumption, including `id`, `type`, `data`, `position`,
conditional edge labels, and subgraph group nodes with `parentId`/`extent` metadata for nested graph rendering.

Added tests covering plain compiled graphs, conditional routing, and compiled subgraph rendering,
verified with `./mvnw -pl langgraph4j-core -Dtest=StateGraphRepresentationTest test`.
