# F009 highlight node on graph interruption event

## Description

In the @studio/webui/src/lg4j-graph.js source code, I want to implement a feature that highlights a node when an interruption event occurs in the graph.
This will help users easily identify which node is affected by the interruption.
The interruption event can be triggered in method `onActive(event)` and occur when the `event.detail.node` is equal to `__INTERRUPTED__`

## Instructions

When an interruption occur, I want that the current working node will be highlighted with a red border.
The highlight should be removed when the interruption is resolved that is when a new event coming to `onActive` with a valid node name.






