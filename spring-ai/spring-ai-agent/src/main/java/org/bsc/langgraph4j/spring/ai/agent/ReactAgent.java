package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public interface ReactAgent extends LG4JLoggable {

    interface ChatService {

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

    /**
     * Class responsible for building a state graph.
     */
    class Builder<State extends MessagesState<Message>> extends ReactAgentBuilder<Builder<State>, State> {
        protected Agent.Builder<Message,State> agentBuilder = Agent.builder();

        public Builder<State> addCallModelHook(NodeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addCallModelHook(wrapCall);
            return result();
        }

        public Builder<State> addExecuteToolsHook(EdgeHook.WrapCall<State> wrapCall ) {
            agentBuilder.addExecuteToolsHook(wrapCall);
            return result();
        }

        /**
         * Builds and returns a StateGraph with the specified configuration.
         * Initializes the stateSerializer if it's null. Then, constructs a new StateGraph object using the provided schema
         * and serializer, adds an initial edge from the START node to "agent", and then proceeds to add nodes for "agent" and
         * "action". It also sets up conditional edges from the "agent" node based on whether or not to continue.
         *
         * @return A configured StateGraph object.
         * @throws GraphStateException If there is an issue with building the graph state.
         */
        public StateGraph<State> build(Function<ReactAgentBuilder<?,?>, ChatService> chatServiceFactory ) throws GraphStateException {

            final var callModelAction = new CallModelAction<State>( chatServiceFactory, this );

            final var executeToolsAction = new ExecuteToolsAction<State>( tools() );

            return agentBuilder
                    .stateSerializer( stateSerializer )
                    .schema( ofNullable(schema).orElse( MessagesState.SCHEMA) )
                    .callModelAction( callModelAction )
                    .executeToolsAction( executeToolsAction )
                    .build();

        }
    }

    /**
     * Returns a new instance of {@link Builder}.
     *
     * @return a new {@link Builder} object
     */
    static <State extends MessagesState<Message>> Builder<State> builder() {
        return new Builder<>();
    }

}
