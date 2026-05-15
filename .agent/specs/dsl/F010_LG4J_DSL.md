# F010 refine frontend

## Increase modularity and separation of concerns

Concerning frontend for testing module [langgraph4j-dsl](langgraph4j-dsl), I want further improve frontend modularization and separation of concerns using esclusively web components.

### currently web components

Currently we have the following web components: 

* `lg4j-workbench` that coordinate and layout children components. 
* `lg4j-graph` that visualize the graph.
* `lg4j-executor-test` that visualize the JSON DSL allowing also to emulate node activities.

So in the [index.html](langgraph4j-dsl/src/test/resources/static/index.html) we have:

```html
    <lg4j-workbench >
      <lg4j-dsl-view slot="graph"></lg4j-graph>
      <lg4j-executor-test slot="executor"></lg4j-executor-test>
    </lg4j-workbench>
```

### new web components

I want split the `lg4j-executor-test` content in two components:

* `lg4j-executor`:
   Put it in the new file `lg4j-executor.js`, this is the component responsible to call backend and dispatch messages `graph` and `graph-acive`. Such messages will be dispatched using `bubbles` because it must hadled by `lg4j-workbench` that will dispatch it to the right children and `composed` to be able to cross the shadow DOM boundary
* `lg4j-result`: 
   Put it in the new file `lg4j-result.js`, This component will contains the panel that visalize the JSON DSL listening for the `graph` event routed by `lg4j-workbench.js`

At the end in the [index.html](langgraph4j-dsl/src/test/resources/static/index.html) I expect a body like:

```html
    <lg4j-workbench >
      <lg4j-dsl-view slot="graph"></lg4j-graph>
      <lg4j-result slot="result"></lg4j-result>
      <lg4j-executor slot="executor"></lg4j-executor>
    </lg4j-workbench>
```


### Layout

I want the following layout 

```
+-----------------------+----------+
|                       |          |
|                       |          |
|                       |          |
|                       |  result  |
|                       |          |
|       graph           |          |
|                       |          |
|                       +----------+
|                       |          |
|                       | executor |
|                       |          |
+-----------------------+----------+
```

## Implementation summary: 

Implemented the frontend split by restoring `lg4j-executor.js` as the backend-calling and active-node event component, adding `lg4j-result.js` as the dedicated read-only DSL JSON panel, and updating `index.html` to compose `lg4j-graph`, `lg4j-result`, and `lg4j-executor` inside `lg4j-workbench`. The workbench now lays out the graph across the left column with result over executor in the right column, and routes bubbled/composed `graph` events to both graph and result while continuing to route active-node events to the graph. The visualizer tests were updated for the new module names, slots, routing behavior, and result component, and `./mvnw -pl langgraph4j-dsl -Dtest=LangGraphDslVisualizerApplicationTest test` passes.
