# F003 Create JtJsonEditor javelit component

## Description

The module [langgraph4j-javelit] contains [Javelit](https://docs.javelit.io) components.
Javelit is a Java framework that mimics the Python Streamlit framework, allowing to create web applications in Java with a simple and intuitive API.
Javelit frontend components are developed as web-elements based on the [Lit](https://lit.dev/) framework defined in a mustache template.
Javelit backend is a Java file that defines the component's behavior and properties.

## Instructions

I want create a new Javelit component called `JtJsonEditor` that allows the user to edit a JSON object.

### Frontend implementation (JavaScript)

The frontend implementation  must consist in a Lit web-element that use as underlying library the [Jedison](https://www.npmjs.com/package/jedison) package which repo on [github](https://github.com/germanbisurgi/jedison).

### Backend implementation (Java)

The backend implementation must consist in a Java class that defines the behavior and properties of the component.
The component must accept a JSON string and its schema an return the edited JSON string.

In the `JtJsonEditor.Builder` add disabled property that will forbid editing.

### Test application

Generate a Javelit test application that demonstrates the usage of the `JtJsonEditor` component in `src/test/java/JtJsonEditorApp.java`.

### Expected generated files
I expect that will be created the files:

- `src/main/java/org/bsc/javelit/JtJsonEditor.java` - the main component class that defines the behavior and properties of the component.
- `src/main/resources/JtJsonEditor.register.html.mustache` - the mustache template that defines component implementation.
- `src/main/resources/JtJsonEditor.render.html.mustache` - the mustache template that declare component in HTML.
- `src/test/java/JtJsonEditorApp.java` - The test application that demonstrates the usage of the `JtJsonEditor` component.




