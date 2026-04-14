package org.bsc.langgraph4j.langchain4j.generators;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

/**
 * LLMStreamingGenerator is a class that extends AsyncGenerator to handle streaming outputs
 * for a given type T and state that extends AgentState.
 *
 * @param <T> the type of the data being processed
 * @param <State> the type of the state extending AgentState
 * @deprecated use {@link StreamingChatGenerator} instead
 */
@Deprecated(forRemoval = true)
public class LLMStreamingGenerator<T, State extends AgentState> extends AsyncGenerator.WithResult<StreamingOutput<State>> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LLMStreamingGenerator.class);
    /**
     * Creates a new Builder instance for LLMStreamingGenerator.
     *
     * @param <T> the type of the data being processed
     * @param <State> the type of the state extending AgentState
     * @return a new Builder instance
     */
    public static <T,State extends AgentState> Builder<T,State> builder() {
        return new Builder<>();
    }

    final StreamingResponseHandler<T> handler;

    /**
     * Constructs an LLMStreamingGenerator with the specified parameters.
     *
     * @param queue the blocking queue for async generator data
     * @param startingNode the starting node for streaming
     * @param startingState the initial state
     * @param mapResult a function to map the response to a Map (ie. Partial State )
     */
    private LLMStreamingGenerator( BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue,
                                   String startingNode,
                                   State startingState,
                                   Function<Response<T>, Map<String,Object>> mapResult)
    {
        super(new AsyncGeneratorQueue.Generator<>( Objects.requireNonNull(queue, "queue cannot be null" ) ));

        this.handler = new StreamingResponseHandler<T>() {

        @Override
        public void onNext(String token) {
            log.trace("onNext: {}", token);
            queue.add( AsyncGenerator.Data.of( new StreamingOutput<>( token, startingNode, startingState, null ) ) );
        }

        @Override
        public void onComplete(Response<T> response) {
            log.trace("onComplete: {}", response);
            queue.add(AsyncGenerator.Data.done( mapResult.apply(response) ));
        }

        @Override
        public void onError(Throwable error) {
            log.trace("onError", error);
            queue.add( AsyncGenerator.Data.error(error) );
        }
    };
    }

    /**
     * Returns the StreamingResponseHandler associated with this generator.
     *
     * @return the handler for streaming responses
     */
    public StreamingResponseHandler<T> handler() {
        return handler;
    }

    /**
     * Builder class for constructing instances of LLMStreamingGenerator.
     *
     * @param <T> the type of the data being processed
     * @param <State> the type of the state extending AgentState
     */
    public static class Builder<T,State extends AgentState> {
        private BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue;
        private Function<Response<T>,  Map<String,Object>> mapResult;
        private String startingNode;
        private State startingState;

        /**
         * Sets the queue for the builder.
         *
         * @param queue the blocking queue for async generator data
         * @return the builder instance
         */
        public Builder<T,State> queue( BlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>> queue ) {
            this.queue = queue;
            return this;
        }

        /**
         * Sets the mapping function for the builder.
         *
         * @param mapResult a function to map the response to a result
         * @return the builder instance
         */
        public Builder<T,State> mapResult( Function<Response<T>, Map<String,Object>> mapResult ) {
            this.mapResult = mapResult;
            return this;
        }

        /**
         * Sets the starting node for the builder.
         *
         * @param node the starting node
         * @return the builder instance
         */
        public Builder<T,State> startingNode( String node ) {
            this.startingNode = node;
            return this;
        }

        /**
         * Sets the starting state for the builder.
         *
         * @param state the initial state
         * @return the builder instance
         */
        public Builder<T,State> startingState( State state ) {
            this.startingState = state;
            return this;
        }

        /**
         * Builds and returns an instance of LLMStreamingGenerator.
         *
         * @return a new instance of LLMStreamingGenerator
         */
        public LLMStreamingGenerator<T, State> build() {
            if( queue == null )
                queue = new LinkedBlockingQueue<AsyncGenerator.Data<StreamingOutput<State>>>();
            return new LLMStreamingGenerator<>( queue, startingNode, startingState, mapResult );
        }
    }
}
