package org.bsc.langgraph4j.spring.ai.generators;

    import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.streaming.StreamingOutputEnd;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;

public class StreamingChatGenerator<State extends AgentState> extends AsyncGenerator.WithResult<StreamingOutput<State>> {

    private static class Metadata implements HasMetadata {

        public static Metadata of( ChatResponse response ) {
            return new Metadata( response );
        }

        private final ChatResponseMetadata responseMetadata;
        private final ChatGenerationMetadata generationMetadata;
        private final Map<String,Object> assistantMessageMetadata;

        private Metadata(ChatResponse response  ) {
            this.responseMetadata = response.getMetadata();

            final var generation = response.getResult();
            if( generation != null ) {
                this.generationMetadata = generation.getMetadata();
                this.assistantMessageMetadata = generation.getOutput().getMetadata();
            }
            else {
                this.generationMetadata = null;
                this.assistantMessageMetadata = null;
            }
        }

        @Override
        public Optional<Object> metadata(String key) {
            if( Objects.equals(key, "chatResponseMetadata") ) {
                return Optional.of( responseMetadata );
            }
            else if( Objects.equals(key, "chatGenerationMetadata") ) {
                return Optional.of( generationMetadata );
            }
            return ofNullable(assistantMessageMetadata).map( m -> m.get(key));
        }

        @Override
        public Set<String> metadataKeys() {
            final var keys = new HashSet<String>();
            keys.add( "chatResponseMetadata" );
            if( generationMetadata != null ) {
                keys.add( "chatGenerationMetadata" );
            }
            if( assistantMessageMetadata != null ) {
                keys.addAll( assistantMessageMetadata.keySet() );
            }
            return keys;
        }


    }
    public static class Builder<State extends AgentState> {
        private BlockingQueue<Data<StreamingOutput<State>>> queue;
        private Function<ChatResponse, Map<String,Object>> mapResult;
        private String startingNode;
        private State startingState;
        private boolean emitStreamingOutputEnd;

        public Builder<State> emitStreamingOutputEnd( boolean emitStreamingOutputEnd ) {
            this.emitStreamingOutputEnd = emitStreamingOutputEnd;
            return this;
        }

        /**
         * Sets the queue for the builder.
         *
         * @param queue the blocking queue for async generator data
         * @return the builder instance
         */
        public Builder<State> queue(BlockingQueue<Data<StreamingOutput<State>>> queue ) {
            this.queue = queue;
            return this;
        }

        /**
         * Sets the mapping function for the builder.
         *
         * @param mapResult a function to map the response to a result
         * @return the builder instance
         */
        public Builder<State> mapResult(Function<ChatResponse, Map<String,Object>> mapResult ) {
            this.mapResult = mapResult;
            return this;
        }

        /**
         * Sets the starting node for the builder.
         *
         * @param node the starting node
         * @return the builder instance
         */
        public Builder<State> startingNode(String node ) {
            this.startingNode = node;
            return this;
        }

        /**
         * Sets the starting state for the builder.
         *
         * @param state the initial state
         * @return the builder instance
         */
        public Builder<State> startingState(State state ) {
            this.startingState = state;
            return this;
        }

        /**
         * Builds and returns an instance of LLMStreamingGenerator.
         *
         * @return a new instance of LLMStreamingGenerator
         */
        public AsyncGenerator<? extends NodeOutput<State>> build( Flux<ChatResponse> flux ) {
            requireNonNull(mapResult, "mapResult cannot be null");

            if( queue == null )
                queue = new LinkedBlockingQueue<>();

            return new StreamingChatGenerator<>(this,
                    requireNonNull(flux, "flux cannot be null"));

        }
    }

    public static <State extends AgentState> Builder<State> builder() {
        return new Builder<>();
    }


    private StreamingChatGenerator(Builder<State> builder, Flux<ChatResponse> flux ) {
        super(new AsyncGeneratorQueue.Generator<>(Objects.requireNonNull(builder.queue, "queue cannot be null")));

        flux
                .filter( response -> !response.getResults().isEmpty())
                .handle(new BiConsumer<ChatResponse, SynchronousSink<ChatResponse>>() {
                    ChatResponse last = null;

                    @Override
                    public void accept(ChatResponse current, SynchronousSink<ChatResponse> sink) {
                        last = mergeResponses(last, current);


                        builder.queue.add( Data.of(
                                new StreamingOutput<>( textFromResponse(current).orElse(""),
                                        builder.startingNode,
                                        builder.startingState,
                                        Metadata.of(current) )));

                        sink.next(last);

                    }
                })
                .last()
                .doOnSuccess( last -> {
                    if( builder.emitStreamingOutputEnd ) {
                        builder.queue.add(Data.of(
                                new StreamingOutputEnd<>(  textFromResponse(last).orElse(null),
                                        builder.startingNode,
                                        builder.startingState,
                                        Metadata.of(last) )));
                    }
                    builder.queue.add(Data.done( builder.mapResult.apply(last) ));
                })
                .doOnError( error -> builder.queue.add( Data.error(error) ))
                .subscribe( ) ;

    }

    private static Optional<String> textFromResponse( ChatResponse response ) {
        if( response.getResults().isEmpty() ) {
            return Optional.empty();
        }

        return ofNullable( response.getResult() )
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText);
    }

    /**
     * Merges two ChatResponse objects by combining their messages.
     * Fixes the bug where toolCalls were being lost in the original implementation.
     *
     * @return the merged ChatResponse
     */
    private ChatResponse mergeResponses(ChatResponse last, ChatResponse current) {
        if( last == null || last.getResults().isEmpty() ) {
            return current;
        }

        var lastMessage = last.getResult().getOutput();
        var currentMessage = current.getResult().getOutput();

        var mergedMessage = AssistantMessage.builder()
                .content(requireNonNullElse(mergeText(lastMessage.getText(), currentMessage.getText()), ""))
                .properties(currentMessage.getMetadata())
                .toolCalls(mergeToolCalls(lastMessage.getToolCalls(), currentMessage.getToolCalls()))
                .media(currentMessage.getMedia())
                .build();

        var newGeneration = new Generation(mergedMessage, current.getResult().getMetadata());
        return new ChatResponse(List.of(newGeneration), current.getMetadata());
    }

    /**
     * Merges text from two messages.
     *
     * @return the merged text
     */
    private String mergeText(String lastText, String currentText) {
        if( lastText == null ) {
            return currentText;
        }
        if( currentText == null ) {
            return lastText;
        }
        return lastText.concat(currentText);
    }

    /**
     * Merges tool calls from two messages.
     * Tool calls with the same id will be merged.
     *
     * @return the merged list of tool calls
     */
    private List<AssistantMessage.ToolCall> mergeToolCalls(
            List<AssistantMessage.ToolCall> lastToolCalls,
            List<AssistantMessage.ToolCall> currentToolCalls) {

        if( lastToolCalls == null || lastToolCalls.isEmpty() ) {
            return currentToolCalls != null ? currentToolCalls : List.of();
        }
        if( currentToolCalls == null || currentToolCalls.isEmpty() ) {
            return lastToolCalls;
        }

        Map<String, AssistantMessage.ToolCall> toolCallMap = new LinkedHashMap<>();

        lastToolCalls.forEach(tc -> toolCallMap.put(tc.id(), tc));

        // Merge tool calls with the same id
        currentToolCalls.forEach(tc -> {
            if( !toolCallMap.containsKey(tc.id()) ) {
                toolCallMap.put(tc.id(), tc);
            }
        });

        return toolCallMap.values().stream().toList();
    }

}