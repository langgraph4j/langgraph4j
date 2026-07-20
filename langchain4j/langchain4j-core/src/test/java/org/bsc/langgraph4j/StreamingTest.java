package org.bsc.langgraph4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamingTest {

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");

    private String now() {
        return simpleDateFormat.format(new Date());
    }

    private ChatResponse createChatResponse( String result ) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(result))
                .build();
    }

    /**
     * refer issue <a href="https://github.com/langgraph4j/langgraph4j/issues/355">#335</a>
     */
    @Test
    public void issue335() throws Exception {

        final var simpleDateFormat = new SimpleDateFormat("HH:mm:ss");

        var generator = StreamingChatGenerator.<MessagesState<ChatMessage>>builder()
                .mapResult(res -> Map.of("messages", res.aiMessage()))
                .startingState( new MessagesState<>( Map.of() ))
                .startingNode("node")
                .build();

        StreamingChatResponseHandler handler = generator.handler();

        final var firstChunk = "%s_first".formatted(now());
        handler.onPartialResponse(firstChunk);

        // Mock blocking
        CompletableFuture.runAsync(() -> {
            // For example, the first streaming chunk tells the front end that there are several loading animations, with an interval of 5 seconds in the middle.
            // The second streaming chunk is to render the content after loading.
            try {
                // mock the interval of two streaming outputs
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final var secondChunk = "%s_second".formatted(now());
            handler.onPartialResponse(secondChunk);
            handler.onCompleteResponse(createChatResponse( firstChunk + secondChunk ));
        });

        for (var agentStateStreamingOutput : generator) {
            final var date = simpleDateFormat.format(new Date());
            final var chunk = agentStateStreamingOutput.chunk();

            System.out.printf("Actual output time: %s, chunk: %s%n", date, chunk);
            assertTrue( chunk.startsWith(date));

        }

    }

}
