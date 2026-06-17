#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

import org.bsc.langgraph4j.agent.ConversationContextPolicy;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.Resource;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public abstract class BaseReactAgentBuilder<B extends BaseReactAgentBuilder<B,State>, State extends MessagesState<Message>> {

    protected ChatService chatService;
    protected StateSerializer<State> stateSerializer;
    protected ChatModel chatModel;
    protected String systemMessage;
    protected boolean streaming = false;
    protected final Set<ToolCallback> tools = new HashSet<>();
    protected Map<String, Channel<?>> schema = MessagesState.SCHEMA;
    protected boolean emitStreamingOutputEnd;
    protected ConversationContextPolicy<Message> conversationContextPolicy;

    public BaseReactAgentBuilder() {
    }

    public BaseReactAgentBuilder(BaseReactAgentBuilder<?,State> builder) {
        this.chatService = builder.chatService;
        this.stateSerializer = builder.stateSerializer;
        this.chatModel = builder.chatModel;
        this.systemMessage = builder.systemMessage;
        this.streaming = builder.streaming;
        if( !builder.tools.isEmpty() ) {
            this.tools.addAll(builder.tools);
        }
        this.schema = builder.schema;
        this.emitStreamingOutputEnd = builder.emitStreamingOutputEnd;
        this.conversationContextPolicy = builder.conversationContextPolicy;

    }
    public Optional<String> systemMessage() {
        return ofNullable(systemMessage);
    }

    public List<ToolCallback> tools() {
        return tools.stream().toList();
    }

    @SuppressWarnings("unchecked")
    protected B result() {
        return (B)this;
    }

    public B chatService( ChatService chatService ) {
        this.chatService = requireNonNull(chatService, "chatService cannot be null!");
        return result();
    }

    public B conversationContextPolicy( ConversationContextPolicy<Message> conversationContextPolicy ) {
        this.conversationContextPolicy = conversationContextPolicy;
        return result();
    }

    public B schema(Map<String, Channel<?>> schema) {
        this.schema = schema;
        return result();
    }

    /**
     * Sets the state serializer for the graph builder.
     *
     * @param stateSerializer the state serializer to set
     * @return the current instance of GraphBuilder for method chaining
     */
    public B stateSerializer(StateSerializer<State> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return result();
    }

    /**
     * Sets the chat model and streaming-related options.
     *
     * @param chatModel the chat model to use
     * @param streaming enables/disables streaming mode
     * @param emitStreamingOutputEnd enables/disables emitting the streaming end output event
     * @return the current builder instance
     * @deprecated Use {@link ${symbol_pound}chatModel(ChatModel)} and configure streaming options with
     * {@link ${symbol_pound}streaming(boolean)} and {@link ${symbol_pound}emitStreamingEnd(boolean)}.
     */
    @Deprecated
    public B chatModel(ChatModel chatModel, boolean streaming, boolean emitStreamingOutputEnd ) {
        this.chatModel = chatModel;
        this.streaming = streaming;
        this.emitStreamingOutputEnd = emitStreamingOutputEnd;
        return result();
    }

    /**
     * Sets the chat model and streaming-related options.
     *
     * @param chatModel the chat model to use
     * @param streaming enables/disables streaming mode
     * @return the current builder instance
     * @deprecated Use {@link ${symbol_pound}chatModel(ChatModel)} and configure streaming options with
     * {@link ${symbol_pound}streaming(boolean)}.
     */
    @Deprecated
    public B chatModel(ChatModel chatModel, boolean streaming ) {
        this.chatModel = chatModel;
        this.streaming = streaming;
        return result();
    }

    public B chatModel( ChatModel chatModel ) {
        this.chatModel = chatModel;
        return result();
    }

    public B streaming( boolean streaming ) {
        this.streaming = streaming;
        return result();
    }

    public B emitStreamingEnd(boolean emitStreamingOutputEnd ) {
        this.emitStreamingOutputEnd = emitStreamingOutputEnd;
        return result();
    }

    public B defaultSystem(String systemMessage) {
        this.systemMessage = systemMessage;
        return result();
    }

    public B tool(ToolCallback tool) {
        this.tools.add(requireNonNull(tool, "tool cannot be null!"));
        return result();
    }

    public B tools(List<ToolCallback> tools) {
        this.tools.addAll(requireNonNull(tools, "tools cannot be null!"));
        return result();
    }

    public B tools(ToolCallbackProvider toolCallbackProvider) {
        requireNonNull(toolCallbackProvider, "toolCallbackProvider cannot be null!");
        var toolCallbacks = toolCallbackProvider.getToolCallbacks();
        if (toolCallbacks.length == 0) {
            throw new IllegalArgumentException("toolCallbackProvider.getToolCallbacks() cannot be empty!");
        }
        this.tools.addAll(List.of(toolCallbacks));
        return result();
    }

    public B toolsFromObject(Object objectWithTools) {
        var tools = ToolCallbacks.from(requireNonNull(objectWithTools, "objectWithTools cannot be null"));
        this.tools.addAll(List.of(tools));
        return result();
    }

}
