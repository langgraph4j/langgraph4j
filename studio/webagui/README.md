# LangGraph4j Studio AG-UI frontend

Standalone TypeScript/Lit frontend, following `studio/webui`'s layout: components
in `src/`, an HTML entry point, a stylesheet, TypeScript configuration, and build
scripts at the project root. Vite serves and bundles this project.

Requires Node.js 20.19+ or 22.12+.

```sh
cd studio/webagui
npm ci
npm run dev
```

Open the URL printed by Vite. Enter a registered agent ID, an optional user
message, and the initial workflow state as a JSON object, then click **Run
workflow**. Every received event appears in order with its complete JSON payload.
**Cancel** aborts the request; removing the component also cancels an active run.
Each execution creates a fresh thread ID, run ID, and optional user message ID.

The component uses the official [`@ag-ui/client` HttpAgent](https://docs.ag-ui.com/sdk/js/client/http-agent)
to POST a `RunAgentInput` and decode the event stream at
`http://localhost:8081/stream/<agent id>`. The backend must expose that route and
allow cross-origin POST requests from the Vite origin (including `Content-Type`).

```html
<lg4j-executor url="http://localhost:8081" agent-id="my-agent"></lg4j-executor>
```

`url` sets the server base URL; `agent-id` preselects the agent. The component
also exposes `execute()`, `cancel()`, and `createRunInput()` for integration.

```sh
npm run build       # Type-check and generate dist/ with relative asset URLs
npm test            # Verify requests, streamed events, errors, and cancellation
npm run preview     # Serve the production build locally
npm run package     # Build and archive dist/ as langgraph4j-studio-webagui.tgz
```

The distribution archive contains `index.html` and its bundled assets. Extract
it into a static web server's document root to serve the frontend.
