package org.bsc.langgraph4j.langchain4j.generators;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.bsc.langgraph4j.HasMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.streaming.StreamingOutputEnd;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import static java.util.Optional.ofNullable;


public class StreamingChatGenerator<State extends AgentState> extends AsyncGenerator.WithResult<StreamingOutput<State>> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StreamingChatGenerator.class);

    private static class Metadata implements HasMetadata {

        public static Metadata of( ChatResponse response ) {
            return new Metadata( response );
        }

        private final ChatResponseMetadata responseMetadata;
        private final Map<String,Object> aiMessageAttributes;

        private Metadata(ChatResponse response) {

            this.responseMetadata = response.metadata();
            this.aiMessageAttributes = response.aiMessage().attributes();

        }

        @Override
        public Optional<Object> metadata(String key) {
            if( Objects.equals(key, "chatResponseMetadata") ) {
                return Optional.of( responseMetadata );
            }
            return ofNullable(aiMessageAttributes).map( m -> m.get(key));
        }

        @Override
        public Set<String> metadataKeys() {
            final var keys = new HashSet<String>();
            keys.add( "chatResponseMetadata" );
            if( aiMessageAttributes != null ) {
                keys.addAll( aiMessageAttributes.keySet() );
            }
            return keys;
        }

    }
    /**
     * Builder class for constructing instances of LLMStreamingGenerator.
     *
     * @param <State> the type of the state extending AgentState
     */
    public static class Builder<State extends AgentState> {
        private BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue;
        private Function<ChatResponse,  Map<String,Object>> mapResult;
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
        public Builder<State> queue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue ) {
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
        public StreamingChatGenerator<State> build() {
            if( queue == null )
                queue = new LinkedBlockingQueue<>();
            return new StreamingChatGenerator<>( this );
        }
    }

    /**
     * Creates a new Builder instance for LLMStreamingGenerator.
     *
     * @param <State> the type of the state extending AgentState
     * @return a new Builder instance
     */
    public static <State extends AgentState> StreamingChatGenerator.Builder<State> builder() {
        return new StreamingChatGenerator.Builder<>();
    }

    final StreamingChatResponseHandler handler;

    /**
     * Constructs an LLMStreamingGenerator with the specified parameters.
     *
     * @param builder a builder for constructing the  async generator
     */
    private StreamingChatGenerator( Builder<State> builder) {
        super(new AsyncGeneratorQueue.Generator<>( Objects.requireNonNull(builder.queue, "queue cannot be null" )  ));

        this.handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                log.trace("onNext: {}", token);
                final var output = new StreamingOutput<>( token,
                        builder.startingNode,
                        builder.startingState,
                        null );
                builder.queue.add( AsyncGenerator.Data.of( output ) );

            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                log.trace("onComplete: {}", chatResponse);

                if( builder.emitStreamingOutputEnd ) {

                    final var output = new StreamingOutputEnd<>(
                            chatResponse.aiMessage().text(),
                            builder.startingNode,
                            builder.startingState,
                            Metadata.of( chatResponse ) );

                    builder.queue.add(AsyncGenerator.Data.of(output));
                }
                builder.queue.add(AsyncGenerator.Data.done( builder.mapResult.apply(chatResponse) ));

            }

            @Override
            public void onError(Throwable error) {
                log.trace("onError", error);
                builder.queue.add( AsyncGenerator.Data.error(error) );
            }
        };
    }

    /**
     * Returns the StreamingResponseHandler associated with this generator.
     *
     * @return the handler for streaming responses
     */
    public StreamingChatResponseHandler handler() {
        return handler;
    }

}