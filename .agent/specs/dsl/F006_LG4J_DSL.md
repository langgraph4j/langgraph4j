# F006 refine frontend

## create web-component

We want better arrange front end applicatione related to LangGraph4j Json DSL visualization. 
For such reasons you must extract in [langgraph4j-core/src/test/java/org/bsc/langgraph4j/dsl/LangGraphDslVisualizerApplication.java] from variable `SPA_HTML` two separate files:

1. `dsl-view.js`
    In this file you must create a that will include the Javascript already present in `SPA_HTML` refactored as a vanilla javascript [web-component](https://developer.mozilla.org/en-US/docs/Web/API/Web_components) named `LG4JDSLViewElement` 
2. `index.html`
    This file will load the `dsl-view.js` previously defined

Update the spring boot application accordly.

## Implementation summary

the inline `SPA_HTML` was removed from `LangGraphDslVisualizerApplication`, leaving the Spring Boot test application responsible for serving only the `/api/graph` JSON endpoint while the frontend is delivered through standard static resources. The extracted UI now lives in `langgraph4j-core/src/test/resources/static/index.html` and `langgraph4j-core/src/test/resources/static/dsl-view.js`; `index.html` loads the module and hosts `<lg4j-dsl-view>`, while `dsl-view.js` defines the exported vanilla custom element `LG4JDSLViewElement` and preserves the previous React Flow visualization behavior inside the web component. The MVC tests were updated to validate the static HTML shell, the JavaScript module, and the existing graph API.
