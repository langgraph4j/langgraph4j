package org.bsc.langgraph4j.spring.ai.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

class DefaultChatService implements ReactAgent.ChatService {
    final ChatModel chatModel;
    final ChatOptions chatOptions;
    final SystemMessage defaultSystem;

    public DefaultChatService(ReactAgentBuilder<?,?> builder ) {
        this.chatModel = requireNonNull(builder.chatModel, "chatModel cannot be null!");

        if (!builder.tools.isEmpty() && chatModel.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            chatOptions = toolCallingChatOptions.mutate().toolCallbacks(builder.tools()).build();
        }
        else {
            chatOptions = null;
        }

        defaultSystem = SystemMessage.builder()
                .text(builder.systemMessage().orElse(
                        "You are a helpful AI Assistant answering questions." ))
                .build() ;
    }

    @Override
    public ChatModel chatModel() {
        return chatModel;
    }

    @Override
    public Optional<ChatOptions> chatOptions() {
        return ofNullable(chatOptions);
    }

    @Override
    public ChatResponse execute(List<Message> messages) {
        final var newMessages = new LinkedList<Message>();
        newMessages.add(defaultSystem);
        newMessages.addAll(messages);
        return ReactAgent.ChatService.super.execute(newMessages);
    }

    @Override
    public Flux<ChatResponse> streamingExecute(List<Message> messages) {
        final var newMessages = new LinkedList<Message>();
        newMessages.add(defaultSystem);
        newMessages.addAll(messages);
        return ReactAgent.ChatService.super.streamingExecute(newMessages);
    }
}
