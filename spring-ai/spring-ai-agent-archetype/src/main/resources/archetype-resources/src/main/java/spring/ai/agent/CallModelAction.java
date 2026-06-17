#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.agent.ConversationContextPolicy;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.generators.StreamingChatGenerator;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.Generation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

public class CallModelAction<State extends MessagesState<Message>> implements AsyncNodeActionWithConfig<State> {

    private final ChatService chatService;
    private final boolean streaming;
    private final boolean emitStreamingOutputEnd;
    private final ConversationContextPolicy<Message> conversationContextPolicy;

    public <B extends BaseReactAgentBuilder<?,?>> CallModelAction( B builder ) {

        this.chatService = ofNullable(builder.chatService).orElseGet( () -> new DefaultChatService(builder) );
        this.conversationContextPolicy = builder.conversationContextPolicy;
        this.streaming = builder.streaming;
        this.emitStreamingOutputEnd = builder.emitStreamingOutputEnd;

    }

    /**
     * Calls a model with the given workflow state.
     *
     * @param state The current state containing input and intermediate steps.
     * @return A map containing the outcome of the agent call, either an action or a finish.
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {

        final var messages = ofNullable(conversationContextPolicy)
                .map( policy -> policy.filter(state, config) )
                .orElseGet(state::messages);


        if (messages.isEmpty()) {
            return failedFuture( new IllegalArgumentException("no input provided!") );
        }

        if (streaming && !config.isRunningInStudio() ) {
            var flux = chatService.streamingExecute(messages);

            var generator = StreamingChatGenerator.builder()
                    .emitStreamingOutputEnd(emitStreamingOutputEnd)
                    .startingNode("agent")
                    .startingState(state)
                    .mapResult(response -> Map.of("messages", response.getResult().getOutput()))
                    .build(flux);

            return completedFuture(Map.of("messages", generator));
        } else {
            final var output = ofNullable(chatService.execute(messages).getResult())
                    .map(Generation::getOutput);

            return output
                    .map( o -> completedFuture(Map.<String,Object>of("messages", o)) )
                    .orElseGet( () -> failedFuture( new IllegalStateException("no output returned from chat service!")) )
                    ;
        }

    }

}
