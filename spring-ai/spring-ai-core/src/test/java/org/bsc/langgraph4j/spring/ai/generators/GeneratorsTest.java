package org.bsc.langgraph4j.spring.ai.generators;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;


public class GeneratorsTest {

    record Message( List<String> elements ) {

        public Message( String arg ) {
            this( List.of(arg) );
        }
    }

    @Test
    public void issue284Test() {

        final var elements = List.of( new Message("a"), new Message("b"), new Message("c"));

        var result1 = new AtomicReference<Message>(null);

        //////////////////////
        // ORIGINAL SOLUTION
        //////////////////////
        var processedFlux1 = Flux.fromIterable( elements )
                .scan( (lastMessage, newMessage) -> {

                    var mergeStream = Stream.concat(
                            lastMessage.elements().stream(),
                            newMessage.elements().stream());

                    return new Message(mergeStream.toList());
                })
                .doOnNext(result1::set)
                .map(next ->
                        String.join(" - ", next.elements())
                );

        processedFlux1.subscribe(
                System.out::println,
                e -> fail(),
                () -> System.out.println(result1.get()) );

        //////////////////////
        // PR#285 SOLUTION
        //////////////////////
        var result2 = new AtomicReference<Message>(null);

        var processedFlux2 = Flux.fromIterable( elements )
                .doOnNext( message -> {
                    result2.updateAndGet( lastMessage -> {
                        if( lastMessage == null ) {
                            return message;
                        }
                        var mergeStream = Stream.concat(
                                result2.get().elements().stream(),
                                message.elements().stream());

                        return new Message(mergeStream.toList());
                    });
                })
                .map(next ->
                        String.join(" - ", next.elements())
                );
        processedFlux2.subscribe(
                System.out::println,
                e -> fail(),
                () -> System.out.println(result2.get()) );

    }
}
