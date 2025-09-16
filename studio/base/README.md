# LangGraph4j Studio SDK

The LangGraph4j Studio SDK provides a set of tools to create and manage a web-based user interface for LangGraph4j graphs. This document describes the components of the SDK and how to use them.

## Overview

LangGraph4j Studio provides a server that can host one or more LangGraph4j graph instances. Each instance is a `StateGraph` that can be compiled and executed. The studio provides a web-based UI to visualize the graph, execute it, and inspect the state at each step.

The communication between the browser and the server is handled via HTTP and results is consuming via HTTP streaming.

## Getting Started

To create a LangGraph4j Studio server, you need to:

1.  Create on or more `StateGraph` instances.
2.  Create a `LangGraphStudioServer.Instance` for each `StateGraph`.
3.  Create an implementation of `LangGraphStudioServer`.
4.  Register the Servlets `GraphInitServlet` and `GraphStreamServlet` in your preferred Servlet Engine
4.  Start the Servlet Engine.

## Core Concepts

### `LangGraphStudioServer`

This is the main interface of the SDK. It defines the nested classes and interfaces used to configure and run the server.

### `LangGraphStudioServer.Instance`

An `Instance` represents a single graph that is hosted by the server. It is created using the `LangGraphStudioServer.Instance.Builder`.

The builder allows you to configure:

-   `title`: The title of the graph, displayed in the UI.
-   `graph`: The `StateGraph` to be hosted.
-   `compileConfig`: The `CompileConfig` to be used when compiling the graph. A `CheckpointSaver` is required.
-   `input arguments`: The input arguments that the graph expects. These are displayed as input fields in the UI. You can add string and image arguments.

### `LangGraphStudioServer.GraphInitServlet`

This servlet is responsible for providing the frontend with the necessary information to initialize the UI for a specific graph instance.

-   **Registration**: This server must be registered on `/init` path spec.
-   **Endpoint**: It handles `GET` requests to `/init`.
-   **Functionality**: It requires an `instance=<instance_id>` query parameter to identify the graph. It returns a JSON object containing:
    -   The graph's title.
    -   A [Mermaid](https://mermaid.js.org/) diagram representing the graph's structure.
    -   The input arguments the graph expects, which are used to build the input form in the UI.
    -   A list of existing execution threads.

### `LangGraphStudioServer.GraphStreamServlet`

This servlet is the main endpoint for executing the graph and streaming the results back to the client.

-   **Registration**: This server must be registered on `/stream/*` path spec.
-   **Endpoint**: It handles POST requests to `/stream/<instance_id>`, where `<instance_id>` identifies the graph to run.
-   **Functionality**:
    -   It accepts a JSON payload in the request body containing the input values for the graph execution.
    -   It uses asynchronous processing (`jakarta.servlet.AsyncContext`) to handle long-running graph executions without blocking the server thread.
    -   It invokes the `compiledGraph.streamSnapshots(...)` method.
    -   As the graph executes, this servlet streams each `NodeOutput` (the result of a single node's execution) back to the client as a server-sent event. The UI then uses these events to visualize the execution flow and display the state at each step in real-time.
    -   It supports resuming from a previous state, allowing for features like human-in-the-loop interaction.

    