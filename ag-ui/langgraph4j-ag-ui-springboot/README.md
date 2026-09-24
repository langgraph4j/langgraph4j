# LangGraph4j AG-UI Spring Boot

Spring Boot web and SSE integration for `langgraph4j-ag-ui-sdk`.

## Classes overview

### AGUISSEController

Spring Boot `@Controller` exposing a `POST /sse/{agentId}` Server-Sent
  Events endpoint compliant with the AG-UI protocol. It resolves the target `AGUIAgent` from an
  injected `AGUIAgentRegistry`, deserializes the request body into an `AGUIRunAgentInput`, starts
  the agent run and streams back every produced AG-UI `Event` as an SSE message, completing the
  connection when the run finishes or fails.

## Sample: reference implementations (test sources)

The test sources of this module provide a runnable, end-to-end reference implementation showing
how to expose a LangGraph4j `AgentExecutor` (built with `spring-ai`) as an AG-UI agent, supporting
both the **CopilotKit HITL (Human-In-The-Loop)** approval flow and a **custom interruption** flow
based on the AG-UI `Interrupt`/`Resume` model.

### AGUIApplication
The Spring Boot application entry point. It wires the `AGUIJacksonSerializer`
  bean and an `AGUIAgentRegistry` bean registering the two reference agents below
  (`AGUIAgentExecutorINTERRUPT` under id `"INTERRUPT"`, `AGUIAgentExecutorHITL` under id `"HITL"`),
  so they are both served through `AGUISSEController`.

### AGUIAgentExecutorHITL
`AGUIAgentBase` implementation showing the **CopilotKit HITL** pattern: it builds an `AgentExecutorEx` graph with an `approvalOn("sendEmail", ...)` interruptionn
  point, and, when the graph interrupts, overrides `onCompleteEvents(...)` to emit the pending tool
  call together with a `StateDeltaEvent` that flips a `resume` flag in the shared state (the
  convention expected by CopilotKit's HITL UI). On the next run, `graphInput(...)` detects that
  `resume` flag in the incoming state to resume the graph with the user's approval decision.

### AGUIAgentExecutorINTERRUPT
`AGUIAgentBase` implementation showing a **custom interruption** pattern based on AG-UI's native `Interrupt`/`Resume` messages: on interruption, it
  overrides `onCompleteEvents(...)` to emit an `InterruptOutcome` (via `RunFinishedEvent`) carrying
  one AG-UI `Interrupt` per pending tool call; `graphInput(...)` resumes the graph by reading the
  approval result from the `resume` payload of the incoming `RunAgentInput`, without relying on any
  extra state flag.




