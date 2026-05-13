# F009 refine frontend

## increase modularity and separation of concerns

Concerning frontend for testing module [langgraph4j-dsl](langgraph4j-dsl), I want further improve frontend modularization and separation of concerns using esclusively web components.
Currently we have only `lg4j-dsl-view` but i want create other web components described below:

### web components catalog

* `lg4j-workbench`: 
   Put it in the new file `lg4j-workbench.js`, this is the main (parent) element that include and coordinate the others (children). It is resposible also to define the childrens layout using the web component using [templates and slots](https://developer.mozilla.org/en-US/docs/Web/API/Web_components/Using_templates_and_slots) features
* `lg4j-executor`:
   Put it in the new file `lg4j-executor.js`, this is the component responsible to call backend, visualize the DSL and send messages `graph` and `graph-acive`. Such messages will be dispatched using `bubbles` because it must hadled by `lg4j-workbench` that will dispatch it to the right children and `composed` to be able to cross the shadow DOM boundary

At the end in the [index.html](langgraph4j-dsl/src/test/resources/static/index.html) I expect a body like:

```html
    <lg4j-workbench >
      <lg4j-dsl-view slot="graph"></lg4j-graph>
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
|       graph           | executor |
|                       |          |
|                       |          |
|                       |          |
|                       |          |
+-----------------------+----------+
```

## Implementation summary: 

Implemented the requested frontend split in `langgraph4j-dsl/src/test/resources/static` by adding `lg4j-workbench.js` as the parent layout/router component and `lg4j-executor.js` as the backend-calling executor component. `index.html` now composes `lg4j-workbench` with slotted `lg4j-dsl-view` and `lg4j-executor`, while `lg4j-dsl-view.js` keeps graph rendering responsibilities and inherits its height from the workbench slot. The executor dispatches `graph` and `graph-active` events with `bubbles: true` and `composed: true`, and the workbench forwards those events to the graph child; it also accepts the misspelled `graph-acive` event name from the spec as a compatibility alias. The visualizer tests were updated to assert the new static modules, slot-based layout, and event boundary behavior.
