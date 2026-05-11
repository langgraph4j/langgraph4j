package org.bsc.langgraph4j.spring.ai.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.Objects;

class DefaultChatService implements ChatService {
    final ChatClient chatClient;

    public DefaultChatService(BaseReactAgentBuilder<?,?> builder ) {
        Objects.requireNonNull(builder.chatModel,"chatModel cannot be null!");
        var toolOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false) // MANDATORY: Disable automatic tool execution
                .build();

        var chatClientBuilder = ChatClient.builder(builder.chatModel)
                .defaultOptions(toolOptions)
                .defaultSystem( builder.systemMessage().orElse(
                        "You are a helpful AI Assistant answering questions." ));
                        
        if (!builder.tools.isEmpty()) {
            chatClientBuilder.defaultToolCallbacks(builder.tools());

        }

        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public final ChatClient chatClient() {
        return chatClient;
    }

}
