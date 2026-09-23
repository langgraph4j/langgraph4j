import { HttpAgent } from "@ag-ui/client"

const HTTP_URL = process.env.HTTP_URL || "http://localhost:8081/sse";

export function createDefaultAgent(agentId: string): HttpAgent {
  return new HttpAgent({
    url: `${HTTP_URL}/${agentId}`,
    initialState: {
      'language': 'NL'
    },
    initialMessages: [
      {
        id: '1',
        role: 'user',
        content: 'Initial message of the user'
      },
      {
        id: '2',
        role: 'assistant',
        content: 'Hi user!'
      }
    ]
    
});
}
