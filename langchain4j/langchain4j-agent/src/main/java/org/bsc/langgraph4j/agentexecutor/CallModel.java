package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Optional.ofNullable;

public class CallModel<State extends MessagesState<ChatMessage>> implements AsyncNodeActionWithConfig<State> {

    static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CallModel.class);

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final SystemMessage systemMessage;
    final ChatRequestParameters parameters;

    /**
     * Constructs a CallAgent with the specified agent.
     *
     * @param builder the builder used to construct the Agent
     */
    public CallModel( AgentExecutorBuilder<State,?> builder ) {
        this.chatModel = builder.chatModel;
        this.streamingChatModel = builder.streamingChatModel;
        this.systemMessage = ofNullable( builder.systemMessage ).orElseGet( () -> SystemMessage.from("You are a helpful assistant") );

        var parametersBuilder = ChatRequestParameters.builder()
                .toolSpecifications( builder.toolMap().keySet().stream().toList() );

        if( builder.responseFormat != null ) {
            parametersBuilder.responseFormat(builder.responseFormat);
        }

        this.parameters =  parametersBuilder.build();
    }

    public boolean isStreaming() {
        return streamingChatModel != null;
    }

    /**
     * Maps the result of the response from an AI message to a structured format.
     *
     * @param response the response containing the AI message
     * @return a map containing the agent's outcome
     * @throws IllegalStateException if the finish reason of the response is unsupported
     */
    private Map<String,Object> mapResult(ChatResponse response )  {

        var content = response.aiMessage();

        if (response.finishReason() == FinishReason.TOOL_EXECUTION || content.hasToolExecutionRequests() ) {
            return Map.of("messages", content);
        }
        if( response.finishReason() == FinishReason.STOP || response.finishReason() == null  ) {
            String responseText = content.text();
            if (responseText == null) {
                responseText = "";
            }
            return Map.of(AgentExecutor.State.FINAL_RESPONSE, responseText);
        }

        throw new IllegalStateException("Unsupported finish reason: " + response.finishReason() );
    }

    private ChatRequest prepareRequest(List<ChatMessage> messages ) {

        var reqMessages = new ArrayList<ChatMessage>() {{
            add(systemMessage);
            addAll(messages);
        }};

        return ChatRequest.builder()
                .messages( reqMessages )
                .parameters(parameters)
                .build();
    }

    /**
     * Applies the action to the given state and returns the result.
     *
     * @param state the state to which the action is applied
     * @return a map containing the agent's outcome
     * @throws IllegalArgumentException if no input is provided in the state
     */
    public Map<String,Object> applySync(State state, RunnableConfig config)  {
        log.trace( "callAgent" );
        var messages = state.messages();

        if( messages.isEmpty() ) {
            throw new IllegalArgumentException("no input provided!");
        }

        if( isStreaming() && !config.isRunningInStudio() ) {

            var generator = StreamingChatGenerator.<State>builder()
                    .mapResult( this::mapResult )
                    .startingNode("agent")
                    .startingState( state )
                    .build();
            streamingChatModel.chat(prepareRequest(messages),  generator.handler());

            return Map.of( "_generator", generator);


        }
        else {
            var response = chatModel.chat(prepareRequest(messages));

            return mapResult(response);
        }

    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
        return CompletableFuture.completedFuture( applySync(state, config) );
    }
}
