# F002 Make JtDataTable component scrollable

## Description

The module [langgraph4j-javelit] contains [Javelit](https://docs.javelit.io) components.
Javelit is a Java framework that mimics the Python Streamlit framework, allowing to create web applications in Java with a simple and intuitive API.
Javelit frontend components are developed as web-elements based on the [Lit](https://lit.dev/) framework defined in a mustache template.

## Instructions

I want improve a JtDataTable component to be scrollable, so that the user can scroll through the rows in the table.
Add a configuration property to set the component height.
If the table has more rows than can be displayed in the available vertical space (viewport), a vertical scrollbar will appear.


The JtDataTable backend component is defined in the file `javelit/src/main/java/org/bsc/javelit/JtDataTable.java`.
The JtDataTable frontend component is defined in the file `javelit/src/main/resources/DataTable.register.html.mustache`.


