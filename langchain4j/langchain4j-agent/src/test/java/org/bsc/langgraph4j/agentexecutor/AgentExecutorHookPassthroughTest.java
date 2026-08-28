package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.agent.Agent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal check: AgentExecutor Builder hook passthrough reaches
 * call-model node wrap and execute-tools edge wrap (same wiring as Agent / Spring ReactAgent).
 */
class AgentExecutorHookPassthroughTest {

    static class EchoTools {
        @Tool("echo input")
        public String echo(String text) {
            return "echo:" + text;
        }
    }

    static class ScriptedChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            if (calls.getAndIncrement() == 0) {
                var req = ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("echo")
                        .arguments("{\"arg0\":\"hi\"}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(req))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    @Test
    void callModelAndExecuteToolsWrapHooksAreInvoked() throws Exception {
        List<String> timeline = new ArrayList<>();

        var app = AgentExecutor.builder()
                .chatModel(new ScriptedChatModel())
                .toolsFromObject(new EchoTools())
                .addCallModelHook((nodeId, state, config, action) -> {
                    timeline.add("NODE_WRAP:enter:" + nodeId);
                    return action.apply(state, config).thenApply(result -> {
                        timeline.add("NODE_WRAP:exit:" + nodeId);
                        return result;
                    });
                })
                .addExecuteToolsHook((sourceId, state, config, action) -> {
                    timeline.add("EDGE_WRAP:enter:" + sourceId);
                    return action.apply(state, config).thenApply(cmd -> {
                        timeline.add("EDGE_WRAP:exit:" + sourceId);
                        return cmd;
                    });
                })
                .build()
                .compile();

        var result = app.invoke(Map.of("messages", UserMessage.from("go"))).orElseThrow();

        assertTrue(result.finalResponse().isPresent());
        assertEquals("done", result.finalResponse().get());

        System.out.println("=== AgentExecutor hook passthrough timeline ===");
        for (int i = 0; i < timeline.size(); i++) {
            System.out.printf("%02d  %s%n", i, timeline.get(i));
        }
        System.out.println("===============================================");

        // ReAct: agent → action(tools) → agent → action(no tools → END)
        assertEquals(List.of(
                "NODE_WRAP:enter:" + Agent.AGENT_LABEL,
                "NODE_WRAP:exit:" + Agent.AGENT_LABEL,
                "EDGE_WRAP:enter:" + Agent.ACTION_LABEL,
                "EDGE_WRAP:exit:" + Agent.ACTION_LABEL,
                "NODE_WRAP:enter:" + Agent.AGENT_LABEL,
                "NODE_WRAP:exit:" + Agent.AGENT_LABEL,
                "EDGE_WRAP:enter:" + Agent.ACTION_LABEL,
                "EDGE_WRAP:exit:" + Agent.ACTION_LABEL
        ), timeline);
    }
}
