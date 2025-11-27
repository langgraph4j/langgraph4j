package org.bsc.langgraph4j.spring.ai.generators;

import java.util.LinkedHashMap;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

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

            var result = new AtomicReference<ChatResponse>(null);

            var processedFlux = flux
                    .filter( response -> response.getResult() != null && response.getResult().getOutput() != null )
                    .doOnNext(currentResponse -> {
                        result.updateAndGet( lastResponse ->
                            lastResponse == null ?
                                    currentResponse :
                                    mergeResponses(lastResponse, currentResponse)
                        );
                    })
                    .map(next ->
                            new StreamingOutput<>( next.getResult().getOutput().getText(),
                                    startingNode,
                                    startingState )
                    );

            return FlowGenerator.fromPublisher(
                    FlowAdapters.toFlowPublisher( processedFlux ),
                    () -> mapResult.apply( result.get() ) );
        }

        /**
         * Merges two ChatResponse objects by combining their messages.
         * Fixes the bug where toolCalls were being lost in the original implementation.
         *
         * @return the merged ChatResponse
         */
        private ChatResponse mergeResponses(ChatResponse last, ChatResponse current) {
            var lastMessage = last.getResult().getOutput();
            var currentMessage = current.getResult().getOutput();

            var mergedMessage = AssistantMessage.builder()
                    .content(requireNonNull(mergeText(lastMessage.getText(), currentMessage.getText())))
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

    static <State extends AgentState> Builder<State> builder() {
        return new Builder<>();
    }

}
