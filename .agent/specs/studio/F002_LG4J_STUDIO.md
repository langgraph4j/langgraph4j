# F002 fix graph visualization

This fix concerns webui in folder [studio/webui] that has been developed using web-elements and react js

The application layout is managed by web component `lg4j-workbench` in [studio/webui/src/lg4j-workbench.js] suche layout is described below:

+-----------------------+----------+
|             Title                |
+-----------------------+----------+
|                       |          |
|                       |          |
|                       |          |
|       graph           |          |
|                       |          |
|                       |  result  |
|                       |          |
+-----------------------+          |
|                       |          |
|       executor        |          |
|                       |          |
+-----------------------+----------+

Currently the content of `graph` area concerning component `lg4j-graph` in [studio/webui/src/lg4j-graph.js] does not fit
well the available space managed by workbench displaying a partial graph diagram.
I want that you fix this issue by making the graph component to fit the available space in workbench taking in account
that the `lg4j-graph` component is developed using the react flow library  take a look to [react-flow](https://reactflow.dev/learn) that is a library for building node-based applications.
