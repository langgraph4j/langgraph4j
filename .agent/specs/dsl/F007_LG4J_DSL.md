# F007 refine frontend

## increase modularity and separation of concerns

After apply feature [.agent/specs/dsl/F006_LG4J_DSL.md] I want further improve frontend modularization and separation of concerns.

I want that web component `LG4JDSLViewElement` in file [langgraph4j-core/src/test/resources/static/dsl-view.js] render on receiving the `CustomEvent` named `graph` where `detail` contain the JSON DSL string.

All the code related to fetch the JSN DSL string from backend must be moved in [langgraph4j-core/src/test/resources/static/index.html] and must be invoked on page load. Once got teh DSL source from backed you must send a message named `graph` to the web component.

## Implementation summary: 

the visualizer web component now listens for the `graph` CustomEvent and renders the JSON DSL string carried in `event.detail`, with DSL parsing and React Flow rendering kept inside `dsl-view.js`. The static `index.html` page now owns loading `/api/graph` during `DOMContentLoaded` and dispatches that payload to `<lg4j-dsl-view>`, and the same separation was applied to both the core test resources and the `langgraph4j-dsl` visualizer resources with tests updated to assert the new contract.
