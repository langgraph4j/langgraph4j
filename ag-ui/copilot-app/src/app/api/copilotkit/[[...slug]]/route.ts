
import { createDefaultAgent } from "@/agent";
import { CopilotRuntime, createCopilotHonoHandler } from "@copilotkit/runtime/v2";
import { handle } from "hono/vercel";



const runtime = new CopilotRuntime({
  agents: {
      'INTERRUPT': createDefaultAgent('INTERRUPT'),
      'HITL': createDefaultAgent('HITL'),
  }
});

const app = createCopilotHonoHandler({
  runtime,
  basePath: "/api/copilotkit",
});


export const GET = handle(app);
export const POST = handle(app);
export const PATCH = handle(app);
export const DELETE = handle(app);