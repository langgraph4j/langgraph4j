package org.bsc.langgraph4j.spring.ai;


import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamingTest {

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");

    private String now() {
        return simpleDateFormat.format(new Date());
    }

    private ChatResponse createChatResponseWithChunk( String chunk ) {
        final var message = AssistantMessage.builder().content(chunk).build();

        final var generation  = new Generation( message );

        return ChatResponse.builder().generations( List.of(generation) ).build();
    }

    /**
     * refer issue <a href="https://github.com/langgraph4j/langgraph4j/issues/355">#335</a>
     */
    @Test
    public void issue335() throws Exception {

        System.out.printf( "main thread %s%n", Thread.currentThread().getName() );

        final Flux<ChatResponse> flux = Flux.create(sink -> {
            System.out.printf( "flux thread %s%n", Thread.currentThread().getName() );
            final var firstChunk = "%s_first".formatted(now());

            sink.next( createChatResponseWithChunk(firstChunk) );

            // For example, the first streaming chunk tells the front end that there are several loading animations, with an interval of 5 seconds in the middle.
            // The second streaming chunk is to render the content after loading.
            try {
                // mock the interval of two streaming outputs
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            final var secondChunk = "%s_second".formatted(now());

            sink.next(createChatResponseWithChunk(secondChunk));

            sink.complete();

        });

        final var generator = StreamingChatGenerator.<MessagesState<Message>>builder()
                .mapResult(res -> Map.of("messages", res ))
                .startingNode("node")
                .startingState(new MessagesState<>( Map.of() ))
                .build( flux.subscribeOn(Schedulers.parallel()) );

        for (var output : generator) {

            if( output instanceof StreamingOutput<MessagesState<Message>> streamingOutput ) {
                final var date = now();
                final var chunk = streamingOutput.chunk();

                System.out.printf("Actual output time: %s, chunk: %s%n", date, chunk);
                assertTrue(chunk.startsWith(date));
            }

        }

    }

}
