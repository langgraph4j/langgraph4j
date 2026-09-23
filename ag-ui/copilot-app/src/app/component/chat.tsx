import { CopilotChat } from "@copilotkit/react-core/v2";

 
export function SimpleChat() {
  return (
    <CopilotChat    
      labels={{
        modalHeaderTitle: "Your Assistant",
        welcomeMessageText: "Hi! 👋 How can I assist you today?",
      }}
    />
  );
}