package org.bsc.langgraph4j.spring.ai.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    ChatClient chatClient();

    default ChatResponse execute(List<Message> messages) {
        return chatClient()
                .prompt()
                .messages( messages )
                .call()
                .chatResponse();
    }

    default Flux<ChatResponse> streamingExecute(List<Message> messages) {
        return chatClient()
                .prompt()
                .messages( messages )
                .stream()
                .chatResponse();
    }
}