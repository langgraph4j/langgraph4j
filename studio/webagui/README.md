# LangGraph4j Studio AG-UI frontend

TypeScript/Lit components reproduce the `studio/webui` workbench: a title and
execution status across the top, the graph above the executor on the left, and
thread tabs with execution/node accordions on the right. The result panel can
be toggled from the title bar. On narrow screens the panels stack vertically.
Vite serves and bundles the application; the graph retains the original Studio
React Flow/Dagre renderer, including zoom, fit, drag, nested subgraphs, resizing,
and session layout persistence, inside a Lit component.

Requires Node.js 20.19+ or 22.12+.

```sh
cd studio/webagui
npm ci
npm run dev
```

Open the URL printed by Vite. Enter a registered agent ID and click **Load** to
retrieve `/init?agent=<id>`, render its graph and generate its input fields. A
`?agent=<id>` query parameter or `agent-id` attribute loads the agent automatically.
Enter the inputs (or an optional message for agents without declared inputs),
optionally supply an initial JSON state, then click **Submit**. Inspect each node's
state in the result accordion. **New thread** creates a separate conversation;
subsequent runs in a selected thread keep its ID and use a fresh run ID.
The backend serializer's fallback `"default"` output label is associated with
the selected conversation so results from new threads remain separate.

**Cancel** aborts the SDK request and records a cancelled result. Disconnecting
the component also aborts any pending initialization or run request.

The executor uses the official [`@ag-ui/client` HttpAgent](https://docs.ag-ui.com/sdk/js/client/overview)
to POST `RunAgentInput` and decode SSE at `/stream/<agent id>`.
`url` sets the server base URL (default `http://localhost:8081`). Both initialization
and execution include credentials; a separate backend origin must allow that
origin, credentials, and the request's `Content-Type` header through CORS.

```html
<lg4j-workbench title="LangGraph4j">
  <lg4j-graph slot="graph"></lg4j-graph>
  <lg4j-result slot="result"></lg4j-result>
  <lg4j-executor slot="executor" url="http://localhost:8081" agent-id="my-agent"></lg4j-executor>
</lg4j-workbench>
```

The SDK event adapter in `src/agui-events.ts` maintains the existing Studio event
contract. Raw events remain available through the executor's `events` property
and bubbling `agui-event` events.

| AG-UI event | Studio event |
| --- | --- |
| `RUN_STARTED` | `state-updated: start` |
| `CUSTOM` named `output` | `result: [thread, {node, state, checkpoint, next, subgraphNode}]` |
| `STEP_STARTED` | `graph-active: {node: stepName}` |
| `STEP_FINISHED` | `graph-step-finished: {node: stepName}`; clears that step's active indicator |
| `RUN_FINISHED` success | `state-updated: stop` |
| `RUN_FINISHED` interrupt | `interrupted: {threadId, interrupts}`, then `state-updated: interrupted` |
| `RUN_ERROR` | `state-updated: error`, with its message shown in the executor |

Interruption follows `outcome.type`, including when the last output node is
`__END__`. Every interrupt's details appear in the result panel and its JSON
response field appears in the executor. **Resume** submits a fresh run on the
same wire thread with `resume: [{interruptId, status: "resolved", payload}, …]`
and the last output state. Both the structured outcome in F011 and its textual
`outcome: "interrupt"` / top-level `interrupts` variant are accepted.

A checkpoint state can also be edited through **Edit state** / **Save state**.
That enables **Resume**, carrying the edited state and
`forwardedProps: {resume: true, node, checkpoint}`. The backend agent must honor
AG-UI resume entries and, for checkpoint editing, those forwarded properties.
The current base `AGUILangGraphStudioAgent.graphInput()` returns `GraphInput.noArgs()`;
agents must implement their own input/resume handling to act on these values.

The executor exposes `initialize()`, `execute()`, `resume()`, `cancel()`, and
`createRunInput(resume?)` for integration.

```sh
npm run build       # Type-check and generate dist/ with relative asset URLs
npm test            # Verify transport, event adaptation, routing and resume
npm run preview     # Serve the production build locally
npm run package     # Build and archive dist/ as langgraph4j-studio-webagui.tgz
```

Extract the archive into a static server's document root to serve the frontend.
