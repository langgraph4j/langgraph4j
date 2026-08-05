package org.bsc.langgraph4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live LLM coverage for Basic Reflection (related to
 * <a href="https://github.com/langgraph4j/langgraph4j/issues/8">#8</a>).
 * Skipped unless {@code OPENAI_API_KEY} is set. Excluded from default Surefire via {@code *ITest}.
 */
public class BasicReflectionITest {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BasicReflectionITest.class);

    static final int MAX_ROUNDS = 2;

    static class ReflectionState extends MessagesState<String> {
        static final Map<String, Channel<?>> SCHEMA = MessagesState.SCHEMA;

        ReflectionState(Map<String, Object> initData) {
            super(initData);
        }

        long draftCount() {
            return messages().stream().filter(m -> m.startsWith("DRAFT:")).count();
        }

        String lastMessageOrEmpty() {
            return lastMessage().orElse("");
        }
    }

    interface WriterService {
        @SystemMessage("You are an essay writer. Produce a concise essay draft. "
                + "If critique feedback is provided, revise the previous draft accordingly. "
                + "Return only the essay text.")
        String write(@UserMessage String prompt);
    }

    interface CriticService {
        @SystemMessage("You are a writing critic. Give brief, actionable critique "
                + "(structure, clarity, examples, conclusion). Return only the critique.")
        String critique(@UserMessage String draft);
    }

    static class LlmGenerateNode implements NodeAction<ReflectionState> {
        private final WriterService writer;

        LlmGenerateNode(ChatModel model) {
            this.writer = AiServices.create(WriterService.class, model);
        }

        @Override
        public Map<String, Object> apply(ReflectionState state) {
            var prompt = "Topic / conversation so far:\n"
                    + state.messages().stream().collect(Collectors.joining("\n"))
                    + "\n\nWrite or revise the essay now.";
            return Map.of("messages", "DRAFT: " + writer.write(prompt));
        }
    }

    static class LlmReflectNode implements NodeAction<ReflectionState> {
        private final CriticService critic;

        LlmReflectNode(ChatModel model) {
            this.critic = AiServices.create(CriticService.class, model);
        }

        @Override
        public Map<String, Object> apply(ReflectionState state) {
            var draft = state.lastMessageOrEmpty();
            var text = draft.startsWith("DRAFT:") ? draft.substring("DRAFT:".length()).trim() : draft;
            return Map.of("messages", "CRITIQUE: " + critic.critique(text));
        }
    }

    @Test
    void llmReflectionRunsFixedRounds() throws Exception {
        var openAiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(openAiKey != null && !openAiKey.isBlank(),
                "OPENAI_API_KEY is required for BasicReflectionITest");

        var chatModel = OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName("gpt-4o-mini")
                .timeout(Duration.ofMinutes(2))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.2)
                .maxTokens(1500)
                .build();

        EdgeAction<ReflectionState> shouldContinue = state ->
                state.draftCount() >= MAX_ROUNDS ? "end" : "continue";

        var app = new StateGraph<>(ReflectionState.SCHEMA, ReflectionState::new)
                .addNode("generate", node_async(new LlmGenerateNode(chatModel)))
                .addNode("reflect", node_async(new LlmReflectNode(chatModel)))
                .addEdge(START, "generate")
                .addEdge("generate", "reflect")
                .addConditionalEdges("reflect", edge_async(shouldContinue), Map.of(
                        "continue", "generate",
                        "end", END
                ))
                .compile();

        var result = app.invoke( GraphInput.args(Map.of(
                "messages", "Write a short essay about the benefits of journaling.")),
                RunnableConfig.empty());

        assertTrue(result.isPresent());
        var state = result.get();
        assertEquals(MAX_ROUNDS, state.draftCount());
        assertTrue(state.messages().stream().anyMatch(m -> m.startsWith("CRITIQUE:")));
        log.info("final messages:\n{}", state.messages());
    }
}
