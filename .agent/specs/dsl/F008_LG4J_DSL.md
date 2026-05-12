# F008 refine frontend

## Allow to highlight a specific node inside graph

I want that web component `LG4JDSLViewElement` in file [langgraph4j-dsl/src/test/resources/static/dsl-view.js] on receiving the `CustomEvent` named `graph-active`, where `detail` that contain the node that must be highlighted, put such node in evidence (i.e. coloring border) and show up on the top-right of the node box a spinner that make in evidence that node is involved and it is working

For testing feature, in the [langgraph4j-core/src/test/resources/static/index.html] I want that you add a left-side-panel that visualize (read-only) the JSON DSL and give the possibility to specify the node that must be hightlighted.
All the code related to fetch the JSN DSL string from backend must be moved in 

## Implementation summary: 

Implemented active-node highlighting by making `LG4JDSLViewElement` listen for `graph-active` events, store the requested node id from `detail.node`, and pass it into the React Flow renderer so matching nodes receive an `active-node` class. The visualizer CSS now emphasizes the active node with a blue border/shadow and an animated top-right spinner. The static test page now includes a left side panel with the fetched DSL rendered read-only, an active-node input, and a Highlight button that dispatches `CustomEvent('graph-active', { detail: { node } })`; the same static resources were kept aligned under both `langgraph4j-dsl` and `langgraph4j-core`, and MockMvc assertions were updated for the new page and component contract.
