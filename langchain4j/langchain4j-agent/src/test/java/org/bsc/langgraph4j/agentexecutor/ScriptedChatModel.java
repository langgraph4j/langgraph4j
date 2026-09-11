package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ScriptedChatModel implements ChatModel {

    public static class Builder {
        private final List<ChatResponse> responses = new LinkedList<>();

        public Builder addResponse(ChatResponse response) {
            responses.add(response);
            return this;
        }

        public Builder addResponses(ChatResponse... responses) {
            this.responses.addAll(Arrays.asList(responses));
            return this;
        }

        public Builder addToolCallResponse(String id, String name, String arguments) {
            final var response = ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                    .id(id)
                                    .name(name)
                                    .arguments(arguments)
                                    .build()))
                            .build())
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
            this.responses.add(response);
            return this;
        }

        public Builder addTextResponse(String text, FinishReason finishReason) {
            final var response = ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .finishReason(finishReason)
                    .build();
            this.responses.add(response);
            return this;
        }

        public ScriptedChatModel build() {
            return new ScriptedChatModel(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    final ArrayDeque<ChatResponse> responses = new ArrayDeque<>();
    final AtomicInteger calls = new AtomicInteger();

    private ScriptedChatModel(Builder builder) {
        this.responses.addAll(builder.responses);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        calls.incrementAndGet();
        final var next = responses.poll();
        if (next == null) {
            throw new IllegalStateException("no scripted response left, calls=" + calls.get());
        }
        return next;
    }
}
