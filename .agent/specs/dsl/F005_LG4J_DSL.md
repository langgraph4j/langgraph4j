# F005 Refine Graph DSL Visualization

## Refinement/Refactor

We need to fix the following UI aspects.

* The `__START__` and `__END__` must have the connection points only on top and bottom.
* The render function must be arrange automatically the graph's layout from top to bottom calculate the right gaps between nodes.
* The changed positions must be preserved between the renders
* The collapsed subgraph must have the connection points only on top and bottom.


## Implementation summary

Refined the test-scope React Flow visualizer so circular `__START__` and `__END__` nodes expose only top target and
bottom source handles, with normalized edges using top-to-bottom connection positions.
Added a render-time automatic layout pass that groups nodes by parent graph/subgraph, ranks them from each
group's start node, and spaces nodes vertically from top to bottom with separate root and subgraph gaps.
Added a `savedPositionsRef` position cache updated from React Flow node position changes, so manually moved nodes keep
their coordinates across rerenders, collapse/expand operations, and repeated DSL application while newly rendered nodes
continue to receive automatic layout positions.
