# F006 add layout management for in react flow

## Description

To render the graph in a more readable way, we need to add layout management for react flow. This will allow us to automatically arrange the nodes and edges in a way that is easy to understand and navigate.
currently , the graph is based on [react flow](https://reactflow.dev/) library and its layout is managed by custom code.
I want remove such custom code and introduce a library to manage it.
Idea is to evaluate and introduce [dagre](https://github.com/dagrejs/dagre/wiki) library to fit the purpose.
React Flow provide an example how to do it [here](https://reactflow.dev/examples/layout/dagre) and  the code snippet from example is in source file @.agent/specs/studio/react-flow-degre-integration.js.

## Instructions

* Install dependency dagre `@dagrejs/dagre` in the project.
* Refactor the code in `studio/webui/src/lg4j-graph.js` to use dagre for layout management instead of the custom code that must be removed.
* During process ensure that code is refactored in a way that is compatible with typescript and includes type definitions for all functions, parameters, and return values.
  Moreover it must be written clear and concise, providing useful information for developers who will be using or maintaining the code.




