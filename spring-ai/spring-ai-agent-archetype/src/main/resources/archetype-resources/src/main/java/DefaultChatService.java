#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Objects;

class DefaultChatService implements AgentExecutor.ChatService {
    final ChatClient chatClient;

    public DefaultChatService( AgentExecutorBuilder<?,?> builder ) {
        Objects.requireNonNull(builder.chatModel,"chatModel cannot be null!");
        var toolOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false) // MANDATORY: Disable automatic tool execution
                .build();

        var chatClientBuilder = ChatClient.builder(builder.chatModel)
                .defaultOptions(toolOptions)
                .defaultSystem( builder.systemMessage().orElse(
                        "You are a helpful AI Assistant answering questions." ));
                        
        if (!builder.tools.isEmpty()) {
            chatClientBuilder.defaultToolCallbacks(builder.tools);
        }

        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public final ChatClient chatClient() {
        return chatClient;
    }

}
