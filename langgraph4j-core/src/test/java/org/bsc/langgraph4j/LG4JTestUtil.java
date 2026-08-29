package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;

public interface LG4JTestUtil {


    class State extends MessagesState<String> {
        final static String RESUME = "LG4J_RESUME";

        public State(Map<String, Object> initData) {
            super(initData);
        }

        boolean isResume() {
            return this.<Boolean>value(RESUME).orElse(false);
        }
    }

    class JsonStateSerializer extends JacksonStateSerializer<State> {

        public JsonStateSerializer(AgentStateFactory<State> stateFactory) {
            super(stateFactory);
        }
    }

    enum StateSerializerEnum {
        BINARY(new ObjectStreamStateSerializer<>(State::new)),
        JSON(new JsonStateSerializer(State::new));

        public final StateSerializer<State> stateSerializer;

        StateSerializerEnum(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
        }
    }


    class CustomNodeAction implements AsyncNodeActionWithConfig<State> {

        public static class Interruptable extends CustomNodeAction implements InterruptableAction<State> {
            private final boolean interrupt;

            private Interruptable(Builder builder) {
                super(builder);
                interrupt = builder.interruptable;
            }

            @Override
            public Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state, RunnableConfig config) {
                if( interrupt && !state.isResume() ) {
                    assertEquals( nodeId, this.message);
                    return Optional.of(InterruptionMetadata.builder(nodeId,state).build());
                }
                return Optional.empty();
            }

        }

        public static class Builder {
            boolean interruptable;
            boolean interruptWithException;
            String message;
            boolean streaming;
            

            public Builder message(String message ) {
                this.message = message;
                return this;
            }

            public Builder interruptable() {
                interruptable = true;
                return this;
            }

            public Builder interruptWithException() {
                interruptWithException = true;
                return this;
            }

            public Builder streaming() {
                streaming = true;
                return this;
            }


            public CustomNodeAction build() {
                return (interruptable) ?
                        new CustomNodeAction.Interruptable(this) :
                        new CustomNodeAction(this);
            }

        }

        public static Builder builder() {
            return new Builder();
        }

        public static CustomNodeAction of(String id) {
            return CustomNodeAction.builder().message(id).build();
        }

        final String message;
        final boolean streaming;
        final boolean interruptWithException;
        private CustomNodeAction(CustomNodeAction.Builder builder) {
            this.message = requireNonNull(builder.message, "message cannot be null!");
            this.streaming = builder.streaming;
            this.interruptWithException = builder.interruptWithException;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            if( interruptWithException && !state.isResume() ) {
                return failedFuture(new GraphInterruptException(config, ofNullable(message).orElse("Interrupting with exception!")));
            }
            if (streaming) {
                final var generator = AsyncGeneratorFlow.create( dispatcher -> {
                    dispatcher.dispatchAsync(AsyncGenerator.Data.of(new StreamingOutput<>( "Test1", message, state, null ) ) );
                    dispatcher.dispatchAsync(AsyncGenerator.Data.of(new StreamingOutput<>( "Test2", message, state, null ) ) );
                    dispatcher.dispatchAsync(AsyncGenerator.Data.done(Map.of("messages", "Test1Test2") ));
                });

                return completedFuture(Map.of("_streaming_messages", generator));
            }
            return completedFuture(Map.of(State.MESSAGES_STATE, message));
        }


    }

}
