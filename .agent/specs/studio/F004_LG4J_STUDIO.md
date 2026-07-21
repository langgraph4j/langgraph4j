# F004 improve UI configurability

This feature concerns webui in folder [studio/webui] that has been developed using web-elements and react js

The application layout is managed by web component `lg4j-workbench` in [studio/webui/src/lg4j-workbench.js] such layout is described below:

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
In particular the `Title` panel is part of the `lg4j-workbench` component while the `result` and `executor` panels are
`lg4j-result` and `lg4j-executor` web components in [studio/webui/src/lg4j-result.js] and [studio/webui/src/lg4j-executor.js] .

I want that the panels: `Title`, `result` and `executor` must align font size to `graph` panel and, in particular, the
`result and `executor` being web-components you must use the CSS Custom Properties (CSS Variables) to accomplish this from the `lg4j-workbench` component.
The user should be able to change the font size of all panels by changing a single CSS variable in the `lg4j-workbench` component.
