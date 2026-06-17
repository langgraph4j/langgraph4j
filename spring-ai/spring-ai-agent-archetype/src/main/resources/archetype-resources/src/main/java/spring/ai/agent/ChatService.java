#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

public interface ChatService {

    ChatModel chatModel();
    default Optional<ChatOptions> chatOptions() {
        return Optional.empty();
    };

    default ChatResponse execute(List<Message> messages) {
        final var  prompt = Prompt.builder()
                .messages( messages )
                .chatOptions( chatOptions().orElseGet( () -> chatModel().getOptions()))
                .build();

        return chatModel().call( prompt );
    }

    default Flux<ChatResponse> streamingExecute(List<Message> messages) {
        final var  prompt = Prompt.builder()
                .messages(messages)
                .chatOptions( chatOptions().orElseGet( () -> chatModel().getOptions()))
                .build();

        return chatModel().stream( prompt );
    }
}
