# 🦜🕸️ LangGraph4j DSL

`langgraph4j-dsl` contains utilities for exporting LangGraph4j graphs to a
JSON DSL that can be consumed by visualizers and other graph tooling.

The module currently provides:

- `JsonDslGenerator`, a `GraphDefinition.Reducer` that serializes a compiled
  `StateGraph` to JSON.
- `langgraph4j-dsl.schema.json`, a JSON Schema for validating emitted DSL
  documents.
- Test/demo resources for rendering the generated DSL with the React Flow based
  visualizer web component.

## Installation

When using the LangGraph4j BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.bsc.langgraph4j</groupId>
      <artifactId>langgraph4j-bom</artifactId>
      <version>${langgraph4j.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-dsl</artifactId>
  </dependency>
</dependencies>
```

Without the BOM, add the module version explicitly:

```xml
<dependency>
  <groupId>org.bsc.langgraph4j</groupId>
  <artifactId>langgraph4j-dsl</artifactId>
  <version>${langgraph4j.version}</version>
</dependency>
```

Java 17 or later is required.

## Generating JSON DSL

Create a graph as usual, compile it, then reduce it with `JsonDslGenerator`.

```java
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.dsl.JsonDslGenerator;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

GraphDefinition.Reducer<AgentState, String> jsonDslGenerator = new JsonDslGenerator<>();

var workflow = new StateGraph<>(AgentState::new)
    .addNode("agent", state -> CompletableFuture.completedFuture(Map.of()))
    .addNode("action", state -> CompletableFuture.completedFuture(Map.of()))
    .addEdge(START, "agent")
    .addConditionalEdges(
        "agent",
        state -> CompletableFuture.completedFuture("continue"),
        EdgeMappings.builder()
            .to("action", "continue")
            .toEND("end")
            .build())
    .addEdge("action", END);

String json = workflow.compile().reduce(jsonDslGenerator);
```

## DSL Format

Generated documents use this top-level shape:

```json
{
  "type": "langgraph4j",
  "version": "1.0",
  "nodes": [],
  "edges": [],
  "subgraphs": []
}
```

Nodes include:

- `id`: unique node id.
- `type`: visual node type, one of `input`, `output`, `default`, or `group`.
- `data.label`: display label.
- `data.kind`: semantic kind, one of `start`, `end`, `node`, `parallel`, or
  `subgraph`.
- `parentId` and `extent`, when the node belongs to a subgraph.

Edges include:

- `id`: generated edge id, such as `e1`.
- `source` and `target`: source and target node ids.
- `type`: edge type, one of `default`, `conditional`, or `parallel`.
- `label` and `data.condition`, for conditional edges.
- `data.kind`: semantic edge kind.

Subgraphs are listed separately in `subgraphs` and represented as `group`
nodes in `nodes`. Child node ids are prefixed with the subgraph node id, for
example `tool_executor-call_tool`.

## JSON Schema

The schema is packaged as a classpath resource:

```text
/langgraph4j-dsl.schema.json
```

It validates the DSL document type, version, node and edge kinds, required
fields, subgraph parent metadata, and generated edge id format.

## Visualizer Demo

The test sources include a Spring Boot sample application that serves:

- `/api/graph`: a generated DSL document.
- `/index.html`: a shell page containing the `lg4j-dsl-view` web component.
- `/dsl-view.js`: the React Flow based visualizer module.

Run it from the repository root with:

```shell
mvn -pl langgraph4j-dsl test-compile org.springframework.boot:spring-boot-maven-plugin:3.4.2:run \
  -Dspring-boot.run.main-class=org.bsc.langgraph4j.dsl.LangGraphDslVisualizerApplication \
  -Dspring-boot.run.useTestClasspath=true
```

Then open:

```text
http://localhost:8080/index.html
```

## Development

Run the module tests with:

```shell
mvn -pl langgraph4j-dsl test
```
