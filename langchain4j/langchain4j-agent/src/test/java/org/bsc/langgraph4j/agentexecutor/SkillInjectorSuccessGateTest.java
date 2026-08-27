package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hard rules: only successful tool results activate skills.
 */
class SkillInjectorSuccessGateTest {

    @Test
    void errorToolResultDoesNotActivate() {
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "order-reply"))
                .skillBody(id -> "BODY")
                .build();

        var state = new AgentExecutor.State(Map.of());
        var command = new Command(Agent.AGENT_LABEL, Map.of(
                "messages", List.of(
                        ToolExecutionResultMessage.builder()
                                .id("1")
                                .toolName("query_logistics")
                                .text("boom")
                                .isError(true)
                                .build()
                )
        ));

        var next = injector.activate(state, command);
        assertTrue(next.update().get(AgentExecutor.State.ACTIVE_SKILLS) == null
                        || ((List<?>) next.update().getOrDefault(AgentExecutor.State.ACTIVE_SKILLS, List.of())).isEmpty(),
                "isError=true must not write active_skills");
        assertEquals(command.gotoNode(), next.gotoNode());
    }

    @Test
    void missingMessagesUpdateDoesNotActivate() {
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "order-reply"))
                .build();

        var state = new AgentExecutor.State(Map.of());
        var command = new Command(Agent.END_LABEL, Map.of());

        var next = injector.activate(state, command);
        assertTrue(next.update().isEmpty() || next.update().get(AgentExecutor.State.ACTIVE_SKILLS) == null);
    }

    @Test
    void successfulToolResultActivates() {
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "order-reply"))
                .build();

        var state = new AgentExecutor.State(Map.of());
        var command = new Command(Agent.AGENT_LABEL, Map.of(
                "messages", List.of(
                        ToolExecutionResultMessage.builder()
                                .id("1")
                                .toolName("query_logistics")
                                .text("ok")
                                .isError(false)
                                .build()
                )
        ));

        var next = injector.activate(state, command);
        assertEquals(List.of("order-reply"), next.update().get(AgentExecutor.State.ACTIVE_SKILLS));
    }

    @Test
    void mixedBatchActivatesOnlySuccessfulTools() {
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver()
                        .bind("ok_tool", "skill-ok")
                        .bind("fail_tool", "skill-fail"))
                .build();

        var state = new AgentExecutor.State(Map.of());
        var command = new Command(Agent.AGENT_LABEL, Map.of(
                "messages", List.of(
                        ToolExecutionResultMessage.builder()
                                .id("a").toolName("ok_tool").text("ok").isError(false).build(),
                        ToolExecutionResultMessage.builder()
                                .id("b").toolName("fail_tool").text("no").isError(true).build()
                )
        ));

        var next = injector.activate(state, command);
        assertEquals(List.of("skill-ok"), next.update().get(AgentExecutor.State.ACTIVE_SKILLS));
    }

    @Test
    void reactivatingSameSkillDoesNotDuplicate() {
        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("query_logistics", "order-reply"))
                .build();

        var state = new AgentExecutor.State(Map.of(
                AgentExecutor.State.ACTIVE_SKILLS, List.of("order-reply")));
        var command = new Command(Agent.AGENT_LABEL, Map.of(
                "messages", List.of(
                        ToolExecutionResultMessage.builder()
                                .id("2")
                                .toolName("query_logistics")
                                .text("ok-again")
                                .isError(false)
                                .build()
                )
        ));

        var next = injector.activate(state, command);
        // set merge: no-op write when membership unchanged
        assertTrue(next.update().get(AgentExecutor.State.ACTIVE_SKILLS) == null);
        assertEquals(List.of("order-reply"), state.activeSkills());
    }
}
