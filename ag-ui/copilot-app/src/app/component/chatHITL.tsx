"use client";

import { CopilotChat, useHumanInTheLoop } from "@copilotkit/react-core/v2";
import { z } from "zod";
import { EmailApprovalCard } from "./emailApprovalCard";


export function SimpleChatHITL() {
  useHumanInTheLoop({
    agentId: "HITL",
    name: "sendEmail",
    description: "Ask the user to approve sending an email.",
    parameters: z.object({
      to: z
        .string()
        .describe("Who the email is being sent to (e.g. 'Alice from Sales')"),
      subject: z.string().describe("The subject of the email"),
      body: z.string().describe("The body content of the email"),
    }),
    render: ({ args, status, respond }) => (
      <EmailApprovalCard
        to={args?.to}
        subject={args?.subject}
        body={args?.body}
        status={status}
        onSubmit={(result) => respond?.(result)}
      />
    ),
  });

  return <CopilotChat agentId="HITL" className="h-full" />;
}