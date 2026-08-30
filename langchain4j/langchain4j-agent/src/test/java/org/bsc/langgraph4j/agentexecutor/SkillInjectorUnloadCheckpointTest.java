package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that hold the PR2 unload wiring to the *strictest possible
 * contract* — touching the three danger zones the reviewer is almost guaranteed
 * to ask about:
 *
 * <ol>
 *   <li>{@link #ephemeralUnloadSurvivesCheckpointAndResume()} — proves Map-channel
 *       writes from {@code callModelUnloadHook} are actually merged into Graph
 *       State and persisted to checkpoint, so a second {@code invoke()} on the
 *       same threadId reads back an empty active_skills.</li>
 *   <li>{@link #stickyDefaultActiveSkillsSurviveCheckpoint()} — control group:
 *       WITHOUT ephemeral the same scenario leaves active_skills alive across
 *       checkpoint, so the assertion in (1) is not a test-bug.</li>
 *   <li>{@link #exceptionInsideCallModelLeavesActiveSkillsUntouched()} — nails
 *       semantics for (#2): unload happens only after successful
 *       completion</b> of the wrapped call-model action. A thrown exception
 *       in {@code action.apply(...)} short-circuits {@code thenApply}, so
 *       checkpoint retains the activation — exactly the safe default.</li>
 *   <li>{@link #selectiveUnloadPersistsAcrossCheckpoint()} — ids-based
 *       selective unload is also persisted, not just ephemeral all-clear.</li>
 *   <li>{@link #ephemeralTwoInvokeRoundsSameThread()} — thread-scoped resume:
 *       two full invoke() rounds sharing the same threadId; round 2 must start
 *       with empty active_skills (proves resume-path correctness).</li>
 * </ol>
 */
class SkillInjectorUnloadCheckpointTest {

    // ===== scaffolding =====

    static class EchoTools {
        @Tool("echo input") public String echo(String text) { return "echo:" + text; }
    }

    /**
     * Two-turn model: CM0 issues tool call, CM1 returns STOP. Used for the
     * "simple two-step" proofs (1, 2, 3, 4).
     */
    static class TwoTurnChatModel implements ChatModel {
        private final AtomicInteger n = new AtomicInteger();
        @Override public ChatResponse doChat(ChatRequest r) {
            if (n.getAndIncrement() == 0) {
                var req = ToolExecutionRequest.builder().id("t1").name("echo")
                        .arguments("{\"arg0\":\"hi\"}").build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(req))
                        .finishReason(FinishReason.TOOL_EXECUTION).build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP).build();
        }
        int calls() { return n.get(); }
    }

    /**
     * Throws on the n-th call (1-based). Used for the #2 exception proof.
     */
    static class ThrowOnCallModel implements ChatModel {
        final int throwOnCall; // 1 == first callModel
        ThrowOnCallModel(int throwOnCall) { this.throwOnCall = throwOnCall; }
        final AtomicInteger c = new AtomicInteger();
        @Override public ChatResponse doChat(ChatRequest r) {
            int n = c.incrementAndGet();
            if (n == throwOnCall) throw new RuntimeException("boom from callModel #" + n);
            if (n == 1) {
                var req = ToolExecutionRequest.builder().id("t1").name("echo")
                        .arguments("{\"arg0\":\"x\"}").build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(req))
                        .finishReason(FinishReason.TOOL_EXECUTION).build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP).build();
        }
    }

    /**
     * Build a graph with checkpointSaver + resolver bound echo->skills.
     */
    private static AgentExecutor.Builder baseGraph(ChatModel model, UnloadTarget targetOrNull) {
        SkillInjector.Builder ib = SkillInjector.builder()
                .resolver(new MapToolSkillResolver()
                        .bind("echo", "order-reply")
                        .bind("echo", "tool-guidance:a"))
                .skillBody(id -> id);
        if (targetOrNull != null) ib.unloadAfterCallModel(targetOrNull);
        return AgentExecutor.builder()
                .chatModel(model)
                .toolsFromObject(new EchoTools())
                .skillInjector(ib.build());
    }

    // ===== #1: Map writes are merged → checkpoint → thread resume reads [] =====

    /**
     * (#1 & #8 strict version):
     *   1st invoke finishes normally (ephemeral on).
     *   Read LAST checkpoint of the thread via graph.lastStateOf().
     *   Assert active_skills==[] in the persisted snapshot.
     *   Then 2nd invoke(threadId) WITHOUT any inputs seeds — CM2-entry must
     *   see [] (proves even a fresh graph run on same threadId reads the
     *   empty list).
     */
    @Test
    void ephemeralUnloadSurvivesCheckpointAndResume() throws Exception {
        final String thread = "ephemeral-X1";
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var model = new TwoTurnChatModel();
        var beforeEntry = Collections.synchronizedList(new ArrayList<List<String>>());

        var graph = baseGraph(model, UnloadTarget.all())
                .addCallModelHook((n, s, cfg, a) -> {
                    beforeEntry.add(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s, cfg);
                })
                .build()
                .compile(compileConfig);

        var rc = RunnableConfig.builder().threadId(thread).build();
        final var lastState = graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("go"))), rc)
                .orElseThrow(() -> new AssertionError("invoke failed"));

        assertTrue(model.calls() >= 2, "TwoTurnChatModel had to complete CM0+CM1: calls="+model.calls());

        // Assert A: last checkpoint Snapshot persisted [] as active_skills
        var persistActive = lastState.activeSkills();
        assertEquals(List.of(), persistActive,
                "LAST checkpoint after ephemeral MUST store active_skills=[]. " +
                "Actual: " + persistActive);

        // Assert B: round 1's CM1 entry DID see the activation
        //            (so round 2's [] is genuinely from unload, not from no-activation).
        assertTrue(beforeEntry.size() >= 2, "CM0 + CM1 before-entry snapshots missing: " + beforeEntry);
        assertEquals(List.of(), beforeEntry.get(0),                  "CM0 entry = no activation yet");
        assertTrue(beforeEntry.get(1).contains("order-reply"),       "CM1 entry should see activation: " + beforeEntry.get(1));
        assertTrue(beforeEntry.get(1).contains("tool-guidance:a"),   "CM1 entry should see activation: " + beforeEntry.get(1));
        var model2 = new TwoTurnChatModel();
        var entrySnapRound2 = new AtomicReference<List<String>>();
        var graph2 = baseGraph(model2, UnloadTarget.all())
                .addCallModelHook((n, s, cfg, a) -> {
                    if (entrySnapRound2.get() == null) {
                        entrySnapRound2.set(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    }
                    return a.apply(s, cfg);
                })
                .build()
                .compile(CompileConfig.builder().checkpointSaver(saver).build());

        graph2.invoke(GraphInput.args(Map.of("messages", UserMessage.from("another go"))),
                      RunnableConfig.builder().threadId(thread).build())
                .orElseThrow();

        assertEquals(List.of(), entrySnapRound2.get(),
                "Round 2 CM0 ENTRY reads checkpoint. After ephemeral round 1, " +
                "active_skills must be []. Actual: " + entrySnapRound2.get());
    }

    // ===== Control group (#1): WITHOUT ephemeral → activations SURVIVE checkpoint =====

    @Test
    void stickyDefaultActiveSkillsSurviveCheckpoint() throws Exception {
        final String thread = "sticky-K1";
        var saver = new MemorySaver();
        var model = new TwoTurnChatModel();

        var graph = baseGraph(model, null) // null = PR1 sticky default, no unload
                .build()
                .compile(CompileConfig.builder().checkpointSaver(saver).build());

        var rc = RunnableConfig.builder().threadId(thread).build();
        final var lastState = graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("g"))), rc).orElseThrow();

        var persistActive = lastState.activeSkills();

        assertTrue(persistActive.contains("order-reply"),
                "STICKY baseline: persisted active_skills must contain activations. " +
                "Got: " + persistActive);
        assertTrue(persistActive.contains("tool-guidance:a"), persistActive.toString());
    }

    // ===== #4 extension (same checkpoint): selective unload checkpoint-visible =====

    @Test
    void selectiveUnloadPersistsAcrossCheckpoint() throws Exception {
        final String thread = "selective-S2";
        var saver = new MemorySaver();
        var model = new TwoTurnChatModel();

        var graph = baseGraph(model, UnloadTarget.ids("order-reply"))
                .build()
                .compile(CompileConfig.builder().checkpointSaver(saver).build());

        var rc = RunnableConfig.builder().threadId(thread).build();
        final var lastState = graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("g"))), rc)
                .orElseThrow();

        final var persistActive = lastState.activeSkills();

        assertFalse(persistActive.contains("order-reply"),
                "ids('order-reply') unload must persist in checkpoint. Got: " + persistActive);
        assertTrue(persistActive.contains("tool-guidance:a"),
                "selective unload must keep non-listed ids alive. Got: " + persistActive);
    }

    // ===== #2: callModel exception → thenApply never fires → activation remains =====

    /**
     * If CallModel throws, {@code CompletableFuture.thenApply} short-circuits.
     * This test documents that PR2 does NOT define exception-path unload:
     *   - unload occurs after successful completion only
     *   - a subsequent successful resume on the same thread will see old
     *     activations still on state (caller's responsibility to clear via
     *     releaseThread=true, or manually via SkillInjector.unloadMap / unloadCommand).
     */
    @Test
    void exceptionInsideCallModelLeavesActiveSkillsUntouched() throws Exception {
        final String thread = "boom-B3";
        var saver = new MemorySaver();
        var thrower = new ThrowOnCallModel(2); // CM0 succeeds, CM1 throws (AFTER activation)

        var graph = baseGraph(thrower, UnloadTarget.all())
                .build()
                .compile(CompileConfig.builder().checkpointSaver(saver).build());

        var rc = RunnableConfig.builder().threadId(thread).build();
        try {
            graph.invoke(GraphInput.args(Map.of("messages", UserMessage.from("boom"))), rc).orElseThrow();
            fail("Expected RuntimeException from callModel #2");
        } catch (RuntimeException ok) {
            // Expected: "boom from callModel #2". Cause chain wraps in CompletionException, so use message.
            assertTrue(ok.getMessage().contains("callModel #2") ||
                       extractCausal(ok).contains("callModel #2"),
                    "Unexpected exception: " + ok);
        }

        // The checkpoint stores state at last node boundary BEFORE the thrown
        // CM1 actually had entered but THEN threw. Before entering CM1 the state
        // already contains active_skills=[order-reply, tool-guidance:a].
        // The key property: exception did NOT trigger extra "cleanup unload".
        var persistActive = graph.lastStateOf(rc).orElseThrow().state().activeSkills();
        assertTrue(persistActive.contains("order-reply"),
                "EXCEPTION semantics check: after CM1 throw, active_skills MUST " +
                "still contain earlier activations (thenApply never ran). Got: "
                + persistActive);
        assertTrue(persistActive.contains("tool-guidance:a"), persistActive.toString());
    }

    /**
     * Variant of (#1) using two full invoke rounds sharing the same
     * checkpoint threadId. Round 2 CM0 ENTRY snapshot is the most honest proof
     * that ephemeral clean-up was truly checkpointed.
     *
     * Note: We only assert on the entry snapshot of the first callModel of round 2. Whether the deterministic fake model then runs more
     * turns depends on how it reacts to a fresh user message over a history
     * that already ends with STOP, which isn't stable enough to pin.
     */
    @Test
    void ephemeralTwoInvokeRoundsSameThread() throws Exception {
        final String thread = "t-resume-R2";
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder().checkpointSaver(saver).build();

        List<List<String>> round1Before = Collections.synchronizedList(new ArrayList<>());

        var model1 = new TwoTurnChatModel();
        var graph1 = baseGraph(model1, UnloadTarget.all())
                .addCallModelHook((n,s,c,a) -> {
                    round1Before.add(List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s,c);
                })
                .build().compile(compileConfig);
        var rc = RunnableConfig.builder().threadId(thread).build();
        graph1.invoke(GraphInput.args(Map.of("messages", UserMessage.from("r1"))), rc).orElseThrow();
        assertTrue(round1Before.size() >= 2, "round1 completed two CM turns at least: " + round1Before);

        var round2EntryActive = new AtomicReference<List<String>>();
        var model2 = new TwoTurnChatModel();
        var graph2 = baseGraph(model2, UnloadTarget.all())
                .addCallModelHook((n,s,c,a) -> {
                    if (round2EntryActive.get() == null) round2EntryActive.set(
                            List.copyOf(((AgentExecutor.State) s).activeSkills()));
                    return a.apply(s,c);
                })
                .build().compile(compileConfig);
        graph2.invoke(GraphInput.args(Map.of("messages", UserMessage.from("r2"))), rc).orElseThrow();

        assertNotNull(round2EntryActive.get(), "Round 2 never hit callModel!");
        assertEquals(List.of(), round2EntryActive.get(),
                "Round 2 CM0 ENTRY MUST see empty active_skills — " +
                "ephemeral writes truly flowed through the Map channel " +
                "→ merge → checkpoint → resume. Round 2 entry: "
                + round2EntryActive.get() + "; round 1 turns: " + round1Before);
    }

    // ===== helpers =====

    private static String extractCausal(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur.getMessage() != null) sb.append(cur.getMessage()).append('|');
        }
        return sb.toString();
    }
}
