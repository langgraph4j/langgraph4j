# F003 Create JtFilePicker javelit component

## Description

The module [langgraph4j-javelit] contains [Javelit](https://docs.javelit.io) components.
Javelit is a Java framework that mimics the Python Streamlit framework, allowing to create web applications in Java with a simple and intuitive API.
Javelit frontend components are developed as web-elements based on the [Lit](https://lit.dev/) framework defined in a mustache template.
Javelit backend is a Java file that defines the component's behavior and properties.

## Instructions

I want create a new Javelit component called `JtFilePicker` that allows the user to select a single file or directory from the file system.

In the `JtFilePicker.Builder` add disabled property and the component result must be a `java.nio.file.Path` object.

I expect that will be created the files:

- `src/main/java/org/bsc/javelit/JtFilePicker.java` - the main component class that defines the behavior and properties of the component.
- `src/main/resources/JtFilePicker.register.html.mustache` - the mustache template that defines component implementation.
- `src/main/resources/JtFilePicker.render.html.mustache` - the mustache template that declare component in HTML.
- `src/main/test/java/JtFilePickerApp.java - The test application that demonstrates the usage of the `JtFilePicker` component.



