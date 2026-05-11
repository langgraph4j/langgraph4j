package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallModelConversationContextPolicyTest {

    private static class TestState extends MessagesState<ChatMessage> {
        TestState(Map<String, Object> initData) {
            super(initData);
        }
    }

    private static class TestBuilder extends AgentExecutorBuilder<TestState, TestBuilder> {}

    private static class CapturingChatModel implements ChatModel {

        ChatRequest lastRequest;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            this.lastRequest = chatRequest;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    @Test
    void shouldUseAllGraphMessagesWhenPolicyIsNotConfigured() {
        var chatModel = new CapturingChatModel();
        var builder = new TestBuilder()
                .chatModel(chatModel)
                .systemMessage(SystemMessage.from("system"));

        var callModel = new CallModel<>(builder);
        var graphMessages = List.of(
                UserMessage.from("m1"),
                UserMessage.from("m2"),
                UserMessage.from("m3")
        );
        var state = new TestState(Map.of("messages", graphMessages));

        callModel.applySync(state, RunnableConfig.empty());

        assertEquals(4, chatModel.lastRequest.messages().size());
        assertEquals(SystemMessage.class, chatModel.lastRequest.messages().get(0).getClass());
        assertEquals(graphMessages, chatModel.lastRequest.messages().subList(1, 4));
    }

    @Test
    void shouldApplyConversationContextPolicyWhenConfigured() {
        var chatModel = new CapturingChatModel();
        var strategy = new MessageWindowConversationContextPolicy(2);
        var builder = new TestBuilder()
                .chatModel(chatModel)
                .systemMessage(SystemMessage.from("system"))
                .conversationContextPolicy(strategy);

        var callModel = new CallModel<>(builder);
        var graphMessages = List.of(
                UserMessage.from("m1"),
                UserMessage.from("m2"),
                UserMessage.from("m3")
        );
        var state = new TestState(Map.of("messages", graphMessages));

        callModel.applySync(state, RunnableConfig.empty());

        assertEquals(3, chatModel.lastRequest.messages().size());
        assertEquals(SystemMessage.class, chatModel.lastRequest.messages().get(0).getClass());
        assertEquals("m2", ((UserMessage) chatModel.lastRequest.messages().get(1)).singleText());
        assertEquals("m3", ((UserMessage) chatModel.lastRequest.messages().get(2)).singleText());

        // strategy should not mutate graph state
        assertEquals(3, state.messages().size());
        assertTrue(state.messages().stream().allMatch(UserMessage.class::isInstance));
    }
}
