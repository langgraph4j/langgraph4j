package org.bsc.langgraph4j.spring.ai.generators;

import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StreamingChatGenerator}.
 *
 * <p>Covers the streaming response merge logic, including edge cases where
 * LLM responses contain tool calls but no text content (which causes null text).
 *
 * @see <a href="https://github.com/langgraph4j/langgraph4j/issues/XXX">Related issue: NPE when both last and current text are null</a>
 */
class StreamingChatGeneratorTest {

    private ChatResponse createChatResponseWithText(String text) {
        var message = AssistantMessage.builder().content(text).build();
        var generation = new Generation(message);
        return ChatResponse.builder().generations(List.of(generation)).build();
    }

    private ChatResponse createChatResponseWithToolCalls(String toolCallId, String toolName, String args) {
        var toolCall = new AssistantMessage.ToolCall(toolCallId, "function", toolName, args);
        var message = AssistantMessage.builder()
                .content(null)  // tool-call-only responses have null text
                .toolCalls(List.of(toolCall))
                .build();
        var generation = new Generation(message);
        return ChatResponse.builder().generations(List.of(generation)).build();
    }

    /**
     * Should NOT throw NullPointerException when both last and current text are null.
     *
     * <p>This is the core bug scenario: when an LLM response contains only tool calls
     * and no text content, both {@code lastMessage.getText()} and {@code currentMessage.getText()}
     * return null, causing {@code mergeText(null, null)} to return null.
     * The original code wrapped this with {@code requireNonNull()}, which threw NPE.
     *
     * <p>After fix: null content is allowed since {@link AssistantMessage.Builder#content(String)}
     * accepts null values for tool-call-only responses.
     */
    @Test
    void should_not_throw_npe_when_both_last_and_current_text_are_null() throws Exception {
        var flux = Flux.just(
                createChatResponseWithToolCalls("call_1", "search", "{\"q\": \"test\"}"),
                createChatResponseWithToolCalls("call_2", "execute", "{\"sql\": \"SELECT 1\"}")
        );

        var generator = StreamingChatGenerator.<MessagesState<Message>>builder()
                .mapResult(res -> Map.of("messages", res))
                .startingNode("node")
                .build(flux);

        // Iterate through all outputs — must NOT throw NullPointerException
        assertDoesNotThrow(() -> {
            for (var output : generator) {
                assertNotNull(output);
            }
        });
    }

    /**
     * Should handle tool-call-only response followed by text content.
     */
    @Test
    void should_handle_tool_call_then_text_responses() throws Exception {
        var flux = Flux.just(
                createChatResponseWithToolCalls("call_1", "search", "{\"q\": \"test\"}"),
                createChatResponseWithText("Here are the results.")
        );

        var generator = StreamingChatGenerator.<MessagesState<Message>>builder()
                .mapResult(res -> Map.of("messages", res))
                .startingNode("node")
                .build(flux);

        assertDoesNotThrow(() -> {
            for (var output : generator) {
                assertNotNull(output);
            }
        });
    }

}
