package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR1.2 — After successful tool execution, skill ids land in Graph State;
 * next CallModel request receives skill body via Policy; state messages stay clean.
 */
class SkillInjectorActivationTest {

    private static final String SKILL_MARKER = "DYNAMIC_SKILL_BODY::order-reply";

    static class LogisticsTools {
        @Tool("query order logistics")
        public String query_logistics(String orderId) {
            return "shipped:" + orderId;
        }
    }

    static class ScriptedChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            if (calls.getAndIncrement() == 0) {
                var req = ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("query_logistics")
                        .arguments("{\"arg0\":\"O-1\"}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(req))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("物流已发出"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    @Test
    void successfulToolActivatesSkillIdAndPolicyInjectsBodyOnNextModelCall() throws Exception {
        var chatModel = new ScriptedChatModel();
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "order-reply"))
                .skillBody(id -> SKILL_MARKER)
                .build();

        var app = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new LogisticsTools())
                .skillInjector(injector)
                .build()
                .compile();

        var result = app.invoke(GraphInput.args(Map.of("messages", UserMessage.from("查物流"))), RunnableConfig.empty()).orElseThrow();

        assertEquals(List.of("order-reply"), result.activeSkills());
        assertEquals(2, chatModel.requests.size());

        // First model call: no activation yet
        assertFalse(chatModel.requests.get(0).messages().stream().anyMatch(m ->
                m instanceof SystemMessage sm && sm.text().contains(SKILL_MARKER)));

        // Second model call: Policy injected skill body
        assertTrue(chatModel.requests.get(1).messages().stream().anyMatch(m ->
                m instanceof SystemMessage sm && sm.text().contains(SKILL_MARKER)));

        // Graph messages must not contain skill body
        assertFalse(result.messages().stream().anyMatch(m ->
                m instanceof SystemMessage sm && sm.text().contains(SKILL_MARKER)));

        System.out.println("PR1.2 OK: active_skills=" + result.activeSkills()
                + " ; 2nd request has skill body; state messages clean");
    }

    @Test
    void skillsFromClassPathProvideRealSkillBody() throws Exception {
        var chatModel = new ScriptedChatModel();
        // maps logistics tool → existing test resource skill "agent-commit"
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "agent-commit"))
                .skillsFromClassPath("skills")
                .build();

        var app = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new LogisticsTools())
                .skillInjector(injector)
                .build()
                .compile();

        var result = app.invoke(GraphInput.args(Map.of("messages", UserMessage.from("查物流"))), RunnableConfig.empty()).orElseThrow();

        assertEquals(List.of("agent-commit"), result.activeSkills());
        assertTrue(chatModel.requests.get(1).messages().stream().anyMatch(m ->
                m instanceof SystemMessage sm && sm.text().contains("Conventional commit")),
                "2nd request should contain body from classpath skills/agent-commit");
        assertFalse(result.messages().stream().anyMatch(m ->
                m instanceof SystemMessage sm && sm.text().contains("Conventional commit")));
    }
}
