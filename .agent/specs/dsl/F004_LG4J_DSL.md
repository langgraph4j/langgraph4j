# F004 Refine Graph DSL Visualization

## Refinement/Refactor

Concerning the implemented feature @.agent/specs/F003_LG4J_DSL.md we need to fix the following UI aspects.

* The `__START__` and `__END__` must be visualized not as rectangle but as circle shape.
* The subgraph must be enclosed in a resizable and collapsable box.
* The node that refers the subgraph must be connected to the `start` of the subgraph itself.
* The subgraph `__END__` node must be connected with the related node in parent graph.
* Allow that the nodes inside subgraph group can be moved limited in the subgraph boundary

## Implementation summary

Refined the test-scope React Flow visualizer served by `LangGraphDslVisualizerApplication` so `__START__` and `__END__` nodes render through a custom circular node component instead of rectangular defaults. Added a custom subgraph node component that encloses nested graph content in a resizable `NodeResizer` box with a collapse/expand control, hiding descendant nodes and rerouting hidden edges to the collapsed subgraph shell. Added render-time subgraph boundary edge rewriting so parent edges targeting a subgraph are connected to that subgraph's `__START__` node, while edges leaving a subgraph originate from the subgraph's `__END__` node. Updated the Spring MVC test to assert the SPA includes the custom node components, subgraph collapse state, and boundary-edge rewrite logic.
