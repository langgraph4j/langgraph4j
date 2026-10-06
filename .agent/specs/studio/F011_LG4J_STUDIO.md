# F011 replicate the previous project `studio/webui` to create a new project `studio/webagui` for AG-UI protocol

## Instructions

Migrate the previous project `studio/webui` to  new project `studio/webagui` for AG-UI protocol. The new project will be based on [Lit](https://lit.dev/docs/) framework for developing web components.

The previous Studio project `studio/webui` was based by the following components:

* [studio/webui/src/lg4j-executor.js]
   > This component provides a form to give input for start/resume/stop process
* [studio/webui/src/lg4j-result.js]
   > This component provides an Accordion that show the results (steps) of the process execution
* [studio/webui/src/lg4j-workbench.js]
   > This component arrange the children components layout and route the bubbled events
* [studio/webui/src/lg4j-graph.js]
   > This component provides a graph visualization of the process execution

The application layout below is managed by web component `lg4j-workbench`
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

I want that the new project `studio/webagui` will replicate the Layout & UI of the previous project `studio/webui` but using the AG-UI protocol so
you must use [@ag-ui/client](https://docs.ag-ui.com/sdk/js/client/overview) to communicate through the AG-UI protocol and use/adapt the AG-UI event to the previous event format used in `studio/webui` project.
Take a look to the AG-UI events used by Studio below and adapt them to the previous event format used in `studio/webui` project, take in consideration
that in AG-UI interruption is notified by a `RUN_FINISHED` event with `outcome` field set to `interrupt` and the `interrupts` array containing the interruption details.

The new project `studio/webagui` will be typescript based an manage using [Vite](https://vitejs.dev/).

## AG-UI Events used by Studio

### RUN_STARTED
Raw event received from AG-UI protocol when a new run is started. The event contains the following fields:
```json
{
  "type": "RUN_STARTED",
  "threadId": "6830d613-b96f-4b18-ad95-a27fed26b9b6",
  "runId": "90235123-1e19-4fd9-b5c2-b51a418f52aa",
  "timestamp": 1791225437899
}
```
### CUSTOM(name: "output")
Raw event received from AG-UI protocol when a new output is generated. The event contains the following fields:
```json
{
  "type": "CUSTOM",
  "name": "output",
  "value": [
    "default",
    {
      "checkpoint": "<checkpoint id>",
      "node": "<node id>",
      "state": { // state attributes of the node
      },
      "next": "<next node id>"
    }
  ]
}
```

### STEP_STARTED
Raw event received from AG-UI protocol when a new step is started. The event contains the following fields:
```json
{
  "type": "STEP_STARTED",
  "stepName": "<node id>>",
  "timestamp": <timestamp long>
}
```
### STEP_FINISHED
Raw event received from AG-UI protocol when a step is finished. The event contains the following fields:
```json
{
  "type": "STEP_FINISHED",
  "stepName": "<node id>>",
  "timestamp": <timestamp long>
}
```
### RUN_FINISHED
Raw event received from AG-UI protocol when a run is finished. The event contains the following fields:
The `outcome` field can be either `success` or `interrupt`.

#### Success Outcome
```json
{
  "type": "RUN_FINISHED",
  "threadId": "<thread id>",
  "runId": "<run id>", // generated UUID
  "outcome": {
    "type": "success"
  },
  "timestamp": <timestamp long>
}
```

#### Interrupt Outcome
```json
{
  "type": "RUN_FINISHED",
  "threadId": "<thread id>",
  "runId": "<run id>", // generated UUID
  "outcome": {
    "type": "interrupt"
    interrupts: [
      {
        "id": "<interrupt id>",
        "reason": "<interrupt reason>",
        "message": "<interrupt message>",
        "toolCallId": "<tool call id>",
        "responseSchema": {},
        "expiresAt": "<expires at>",
        "metadata": {}
      }
    ]
  },
  "timestamp": <timestamp long>
}
```