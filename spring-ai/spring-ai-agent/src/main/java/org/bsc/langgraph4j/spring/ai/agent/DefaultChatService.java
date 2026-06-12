package org.bsc.langgraph4j.spring.ai.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

class DefaultChatService implements ReactAgent.ChatService {
    final ChatModel chatModel;
    final ChatOptions chatOptions;

    public DefaultChatService(ReactAgentBuilder<?,?> builder ) {
        this.chatModel = requireNonNull(builder.chatModel, "chatModel cannot be null!");

        if (!builder.tools.isEmpty() && chatModel.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            chatOptions = toolCallingChatOptions.mutate().toolCallbacks(builder.tools()).build();
        }
        else {
            chatOptions = null;
        }
    }

    @Override
    public final ChatModel chatModel() {
        return chatModel;
    }

    @Override
    public Optional<ChatOptions> chatOptions() {
        return ofNullable(chatOptions);
    }

}
