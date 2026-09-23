"use client";

import { CopilotChat,  useInterrupt } from "@copilotkit/react-core/v2";
import { EmailApprovalCard } from "./emailApprovalCard";


export function SimpleChatINTERRUPT() {

  useInterrupt({
    agentId: "INTERRUPT",
    render: ({ interrupt, resolve }) => {
      // where these fields live depends on what your backend puts on the interrupt
      const { to, subject, body } = (interrupt?.metadata ?? {}) as {
        to?: string;
        subject?: string;
        body?: string;
      };

      return (
        <EmailApprovalCard
          to={to}
          subject={subject}
          body={body}
          status="executing"
          onSubmit={(result) => resolve({ approval_result: result })}
        />
      );
    },
  });

  return <CopilotChat agentId="INTERRUPT" className="h-full" />;
}
