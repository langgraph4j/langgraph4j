# F003 improve graph visualization

This feature concerns webui in folder [studio/webui] that has been developed using web-elements and react js

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

I want that the result panel must be toggable. The user should be able to hide or show the result panel by clicking on a button (hamburger icon).
When the result panel is hidden, the graph area should expand to fill the available space in the workbench.
