package org.bsc.langgraph4j.spring.ai.generators;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.FlowGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.reactivestreams.FlowAdapters;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

public interface StreamingChatGenerator {

    class Builder<State extends AgentState> {
        private Function<ChatResponse, Map<String,Object>> mapResult;
        private String startingNode;
        private State startingState;

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
            requireNonNull( flux, "flux cannot be null" );
            requireNonNull( mapResult, "mapResult cannot be null" );

            var result = new AtomicReference<ChatResponse>(null) ;

            Consumer<ChatResponse> mergeMessage = (response ) -> {
                result.updateAndGet( lastResponse -> {

                    if( lastResponse == null ) {
                        return response;
                    }

                    final var currentMessage = response.getResult().getOutput();

                    if (lastResponse.hasToolCalls()) {
                      return lastResponse;
                    }

                    if( currentMessage.hasToolCalls() ) {
                        return response;
                    }

                    final var lastMessageText = requireNonNull(lastResponse.getResult().getOutput().getText(),
                            "lastResponse text cannot be null" );

                    final var currentMessageText = currentMessage.getText();

                    var newMessage =  new AssistantMessage(
                            currentMessageText != null ?
                                    lastMessageText.concat( currentMessageText ) :
                                    lastMessageText,
                            currentMessage.getMetadata(),
                            currentMessage.getToolCalls(),
                            currentMessage.getMedia()
                    );

                    var newGeneration = new Generation(newMessage, response.getResult().getMetadata());
                    return new ChatResponse( List.of(newGeneration), response.getMetadata());

                });
            };

            var processedFlux = flux
                    .filter( response -> response.getResult() != null && response.getResult().getOutput() != null )
                    .doOnNext(mergeMessage)
                    .map(next ->
                            new StreamingOutput<>( next.getResult().getOutput().getText(),
                                    startingNode,
                                    startingState )
                    );

            return FlowGenerator.fromPublisher(
                    FlowAdapters.toFlowPublisher( processedFlux ),
                    () -> mapResult.apply( result.get() ) );
        }
    }

    static <State extends AgentState> Builder<State> builder() {
        return new Builder<>();
    }

}
