# F010 add new project to manage web interface for Studio based on AG-UI protocol

## Description

Setup a new typescript project based on [Lit](https://lit.dev/docs/) framework for developing web components.
This project aims to create a web interface for Studio that is based on the AG-UI protocol.

## Instructions

I want that you create a new typescript project in the `studio/webagui` folder.
The project's structure must be same of previous project `studio/webui`.
In this first step I want create a new web component called `lg4j-executor` that is responsible for managing execution of LangGraph4j workflow using  communication with AG-UI protocol.
The component must use the standard package [@ag-ui/client](https://docs.ag-ui.com/sdk/js/client/overview) to communicate through the AG-UI protocol.
The component must create a `RunAgentInput` object and send it to the AG-UI protocol using the url `http://localhost:8081/stream/<agent id>` and listen for events.
for each event received, the component will show it in a list of events in the component.
create an index.html for starting the component in a browser and scripts, using [Vite](https://vitejs.dev/), for
* run it in dev mode
* build it for production
* package it for distribution


