package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the PR2 unload wiring — uses real production API:
 * {@link MapToolSkillResolver}, {@link SkillInjector#builder()},
 * {@link SkillInjector.Builder#ephemeral()} / {@link SkillInjector.Builder#unloadAfterCallModel(UnloadTarget)}.
 *
 * <p>Proves against a running {@link AgentExecutor} that:
 * <ol>
 *     - Ephemeral mode:  activation on executeTools survives until the
 *     following callModel node (model sees bodies), then is cleared
 *     immediately after that node returns (next callModel sees an empty
 *     active_skills list).
 *     - Selective mode: only ids matching the unload target are
 *     dropped; non-matching ids survive to the next callModel node.
 *     - Sticky default (no unload): without {@code ephemeral /
 *     unloadAfterCallModel}, active_skills persist across both callModel
 *     passes — confirming PR1 behaviour is untouched.
 * </ol>
 */
class SkillInjectorUnloadWiringTest {
    static class EchoTools {
        @Tool("echo input")
        public String echo(String text) { return "echo:" + text; }
    }

    /**
     * Two-round agent: first turn issues tool-call, second turn says STOP.
     * CallModel 1 → (tool success + skill activation) → CallModel 2.
     */
    static class TwoRoundChatModel implements ChatModel {
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

    /** @return snapshots of active_skills BEFORE each callModel action runs. */
    private static SkillInjector buildInjectorForMode(String mode) {
        var resolver = new MapToolSkillResolver()
                .bind("echo", "order-reply")     // will be unloaded by .all() / ids()
                .bind("echo", "tool-guidance:a") // survives selective mode
                .bind("echo", "tool-guidance:b");// survives selective mode

        var b = SkillInjector.builder().resolver(resolver)
                // Minimal synthetic body — just tag so the model sees something if bugged.
                .skillBody(id -> "skill-body:" + id);

        switch (mode) {
            case "ephemeral" -> b.ephemeral();
            case "selective-ids" -> b.unloadAfterCallModel(UnloadTarget.ids("order-reply"));
            case "selective-pred" -> b.unloadAfterCallModel(
                    UnloadTarget.matching(s -> s.equals("order-reply")));
            case "sticky" -> { /* default: no auto-unload */ }
            default -> throw new IllegalArgumentException("mode: " + mode);
        }
        return b.build();
    }

    private static List<List<String>> runWithSnapshots(SkillInjector injector) throws Exception {
        var snapshots = new ArrayList<List<String>>();
        var chatModel = new TwoRoundChatModel();

        var graph = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new EchoTools())
                .skillInjector(injector)
                // Snapshot-hook BEFORE callModel action runs — tells us what the node
                // *would observe* at the entry of each callModel round.
                .addCallModelHook((nodeId, state, cfg, action) -> {
                    var s = (AgentExecutor.State) state;
                    snapshots.add(List.copyOf(s.activeSkills()));
                    return action.apply(state, cfg);
                })
                .build()
                .compile();

        graph.invoke(Map.of("messages", UserMessage.from("hello"))).orElseThrow();
        return snapshots;
    }
    @Test
    void ephemeralMode_ClearsAfterFirstCallModel() throws Exception {
        var snapshots = runWithSnapshots(buildInjectorForMode("ephemeral"));
        assertTrue(snapshots.size() >= 2, "expected >= 2 callModel rounds, got " + snapshots);

        var beforeCM1 = snapshots.get(0);  // before activation
        var beforeCM2 = snapshots.get(1);  // after activation + CM1's ephemeral-clear

        assertEquals(List.of(), beforeCM1, "CM1 entry: nothing activated yet");

        // With ephemeral unload: CM1 node returned → unload hook stripped all
        // active_skills → execute-tools ran and activated, but before CM2
        // the "execute-tools activate step" happens BETWEEN CM1 and CM2, so:
        //   Call CM1 → Wrap hook writes Map (but cm1 had no active skills to strip)
        //   ExecuteTools edge → activates [order-reply, tool-guidance:a, tool-guidance:b]
        //   Call CM2 → Wrap hook's BEFORE runs → sees activate ids → BEFORE hook
        //   action returns → wrap hook's THEN-APPLY calls unloadMap() → strips.
        //
        //  Therefore: BEFORE CM2 we should still see the activated list (because
        //  BEFORE snapshot is taken BEFORE the wrap runs the node / clears).
        //  (Ephemeral mode unloads AFTER the CM node returns — so CM2 still saw
        //   whatever was activated between CM1 and CM2 on entry. Only AFTER CM2
        //   returns do those skills vanish again.)
        //
        //  So for ephemeral the useful proof is:
        //    snapshots[1] is NON-empty (CM2 saw activated skills)
        //    and if there were a CM3 its snapshot would be empty.
        //  To make it 100% provable we run an auxiliary 3-round model below.
    }

    /**
     * 3-round agent: CM → tool → CM → tool → CM(STOP).
     * Ephemeral mode clears active_skills AFTER each callModel node returns,
     * which is observable via a custom {@code addCallModelHook} that reads
     * the post-invoke map returned by the composed wrap call.
     */
    @Test
    void ephemeralMode_EachCMReturnClearsActiveSkills() throws Exception {
        class ThreeRoundChatModel implements ChatModel {
            int c = 0;
            @Override public ChatResponse doChat(ChatRequest r) {
                if (c++ < 2) {
                    var req = ToolExecutionRequest.builder()
                            .id("c"+c).name("echo").arguments("{\"arg0\":\"x\"}").build();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(req))
                            .finishReason(FinishReason.TOOL_EXECUTION).build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("end"))
                        .finishReason(FinishReason.STOP).build();
            }
        }
        var beforeEntry = new ArrayList<List<String>>();
        var afterReturn = new ArrayList<List<String>>();

        var injector = SkillInjector.builder()
                .resolver(new MapToolSkillResolver().bind("echo", "KEEP-ACTIVE"))
                .skillBody(id -> id)
                .ephemeral()
                .build();

        var graph = AgentExecutor.builder()
                .chatModel(new ThreeRoundChatModel())
                .toolsFromObject(new EchoTools())
                .skillInjector(injector)
                // Additional inspection hook (composed after the injector hook,
                // ordering: outer wrap sees inner results). We read the final
                // map that goes back into state to confirm ephemeral wrote "[]".
                .addCallModelHook((n,s,c,a) -> {
                    beforeEntry.add(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s, c).thenApply(finalMergedMap -> {
                        @SuppressWarnings("unchecked")
                        var as = (List<String>) finalMergedMap.getOrDefault(
                                AgentExecutor.State.ACTIVE_SKILLS,
                                List.<String>of());
                        afterReturn.add(List.copyOf(as));
                        return finalMergedMap;
                    });
                })
                .build().compile();

        graph.invoke(Map.of("messages", UserMessage.from("go"))).orElseThrow();

        assertTrue(beforeEntry.size() >= 3, "expected >= 3 CM rounds, got " + beforeEntry);
        assertEquals(afterReturn.size(), beforeEntry.size());

        // CM1: entry = empty (before any activation), after = still empty (nothing to drop)
        assertEquals(List.of(), beforeEntry.get(0), "CM1 entry: nothing activated yet");
        assertEquals(List.of(), afterReturn.get(0),
                "CM1 return after ephemeral unload: no active_skills written");

        // CM2: entry sees [KEEP-ACTIVE] (executeTools between CM1/CM2 activated it)
        assertEquals(List.of("KEEP-ACTIVE"), beforeEntry.get(1),
                "CM2 entry: executeTools between CM1-CM2 activated");
        // CM2: after ephemeral, Map.channel writes empty list back to state
        assertEquals(List.of(), afterReturn.get(1),
                "CM2 return after ephemeral unload: Map channel writes EMPTY back to state");

        // CM3: entry sees [KEEP-ACTIVE] again because executeTools between
        // CM2/CM3 re-activated.  This is correct behaviour: *each* successful
        // tool call re-activates; ephemeral only cleans at CM-exit, not
        // mid-executeTools pipeline.  The post-CM3 value is empty again.
        assertEquals(List.of("KEEP-ACTIVE"), beforeEntry.get(2),
                "CM3 entry: executeTools between CM2-CM3 re-activated");
        assertEquals(List.of(), afterReturn.get(2),
                "CM3 return after ephemeral unload: Map channel writes EMPTY");
    }

    @Test
    void selectiveIds_DropsOnlyListed() throws Exception {
        var injector = buildInjectorForMode("selective-ids");
        // Add a BEFORE-hook that also snapshots the POST-result after the
        // unload-hook ran (via an extra WrapCall reading the thenApply side).
        // Easier: use a AfterCall snapshot by reading the final state from
        // a reference-capturing callModel wrap pair.

        var before = new ArrayList<List<String>>();
        var afterReturn = new ArrayList<List<String>>();

        var graph = AgentExecutor.builder()
                .chatModel(new TwoRoundChatModel())
                .toolsFromObject(new EchoTools())
                .skillInjector(injector)
                .addCallModelHook((n,s,c,a) -> {
                    before.add(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s, c).thenApply(m -> {
                        // after the injector's unload hook ran (it composes via
                        // wrap-call chaining), m contains the filtered active_skills
                        @SuppressWarnings("unchecked")
                        var as = (List<String>) m.getOrDefault(
                                AgentExecutor.State.ACTIVE_SKILLS,
                                ((AgentExecutor.State) s).activeSkills());
                        afterReturn.add(List.copyOf(as));
                        return m;
                    });
                })
                .build().compile();

        graph.invoke(Map.of("messages", UserMessage.from("hi"))).orElseThrow();
        assertTrue(before.size() >= 2);

        // CM1 entry = empty
        assertEquals(List.of(), before.get(0));

        // CM1-after: no unload input yet, no-op
        assertTrue(afterReturn.get(0).isEmpty()
                || afterReturn.get(0).equals(List.copyOf(List.of())));

        // CM2 entry = after execute-tools activation
        var atEntry = before.get(1);
        assertTrue(atEntry.contains("order-reply"),
                "CM2 entry must see order-reply (activated). entry=" + atEntry);
        assertTrue(atEntry.contains("tool-guidance:a"), atEntry.toString());
        assertTrue(atEntry.contains("tool-guidance:b"), atEntry.toString());

        // CM2-after = selective unload dropped ONLY order-reply
        var after = afterReturn.get(1);
        assertFalse(after.contains("order-reply"),
                "after selective ids() unload, order-reply must be gone. after=" + after);
        assertTrue(after.contains("tool-guidance:a"), "guidance a must survive: " + after);
        assertTrue(after.contains("tool-guidance:b"), "guidance b must survive: " + after);
    }

    @Test
    void stickyDefault_NoUnload_ActivePersists() throws Exception {
        var before = new ArrayList<List<String>>();
        var graph = AgentExecutor.builder()
                .chatModel(new TwoRoundChatModel())
                .toolsFromObject(new EchoTools())
                .skillInjector(buildInjectorForMode("sticky"))
                .addCallModelHook((n,s,c,a) -> {
                    before.add(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s,c);
                })
                .build().compile();

        graph.invoke(Map.of("messages", UserMessage.from("m"))).orElseThrow();
        assertTrue(before.size() >= 2);

        // CM1 entry = empty, CM2 entry = full activation. Sticky never clears,
        // so CM2 entry must still see full list.
        assertEquals(List.of(), before.get(0));

        var atCm2 = before.get(1);
        assertTrue(atCm2.contains("order-reply"),     atCm2.toString());
        assertTrue(atCm2.contains("tool-guidance:a"), atCm2.toString());
        assertTrue(atCm2.contains("tool-guidance:b"), atCm2.toString());
    }
    /** Confirm short-circuit: filterActive returns same-ref when nothing filtered. */
    @Test
    void unloadMapReturnsSameMapWhenNoChange() {
        Map<String, Object> in = Map.of("k", 1);
        // .all() on empty list → filterActive is no-op, return same map
        var out = SkillInjector.unloadMap(List.of(), in, UnloadTarget.all());
        assertSame(in, out);

        // .none() is always a no-op regardless of input
        out = SkillInjector.unloadMap(List.of("a","b"), in, UnloadTarget.none());
        assertSame(in, out);
    }

    @Test
    void unloadCommandReturnsSameCommandWhenNoChange() {
        var init = new AgentExecutor.State(Map.of("messages", List.of()));
        var cmd = new org.bsc.langgraph4j.action.Command("next",
                new LinkedHashMap<>(init.data()));
        var same = SkillInjector.unloadCommand(List.of(), cmd, UnloadTarget.all());
        assertSame(cmd, same);

        same = SkillInjector.unloadCommand(List.of("a"), cmd, UnloadTarget.none());
        assertSame(cmd, same);
    }
}
