package org.bsc.langgraph4j;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI-friendly coverage for the Corrective RAG how-to related to issue #8.
 *
 * <p>All model, retrieval, and search behavior is deterministic and requires no
 * API keys or network access.</p>
 */
public class CorrectiveRagStubTest {

    private static final CorrectiveRagWorkflow.RagDocument RELEVANT =
            new CorrectiveRagWorkflow.RagDocument(
                    "Agent memory includes short-term working memory and long-term memory.",
                    "local://agent-memory"
            );

    private static final CorrectiveRagWorkflow.RagDocument IRRELEVANT =
            new CorrectiveRagWorkflow.RagDocument(
                    "Prompt injection is an adversarial attack against language models.",
                    "local://prompt-injection"
            );

    private static final CorrectiveRagWorkflow.RagDocument WEB_RESULT =
            new CorrectiveRagWorkflow.RagDocument(
                    "Episodic memory stores experiences and semantic memory stores facts.",
                    "https://example.test/memory"
            );

    @Test
    void allRelevantDocumentsGenerateWithoutWebSearch() throws Exception {
        var webCalls = new AtomicInteger();
        var graph = CorrectiveRagWorkflow.build(
                question -> List.of(RELEVANT),
                (question, document) -> true,
                question -> question + " rewritten",
                question -> {
                    webCalls.incrementAndGet();
                    return List.of(WEB_RESULT);
                },
                CorrectiveRagStubTest::answerFromContext
        );

        var result = graph.invoke(Map.of(
                CorrectiveRagWorkflow.RagState.QUESTION, "How does agent memory work?"
        ));

        assertTrue(result.isPresent());
        var state = result.get();
        assertFalse(state.webSearch());
        assertEquals(0, webCalls.get());
        assertEquals("How does agent memory work?", state.question());
        assertTrue(state.generation().orElse("").contains("short-term working memory"));
    }

    @Test
    void mixedDocumentsAreFilteredAndSupplementedWithWebSearch() throws Exception {
        var searchedQuestion = new AtomicReference<String>();
        var generatedDocuments = new AtomicReference<List<CorrectiveRagWorkflow.RagDocument>>();
        var graph = CorrectiveRagWorkflow.build(
                question -> List.of(RELEVANT, IRRELEVANT),
                (question, document) -> document.equals(RELEVANT),
                question -> "agent memory types",
                question -> {
                    searchedQuestion.set(question);
                    return List.of(WEB_RESULT);
                },
                (question, documents) -> {
                    generatedDocuments.set(documents);
                    return answerFromContext(question, documents);
                }
        );

        var result = graph.invoke(Map.of(
                CorrectiveRagWorkflow.RagState.QUESTION, "Tell me about memory"
        ));

        assertTrue(result.isPresent());
        var state = result.get();
        assertTrue(state.webSearch());
        assertEquals("agent memory types", searchedQuestion.get());
        assertEquals(List.of(RELEVANT, WEB_RESULT), generatedDocuments.get());
        var answer = state.generation().orElse("");
        assertTrue(answer.contains("short-term working memory"));
        assertTrue(answer.contains("Episodic memory"));
        assertFalse(answer.contains("Prompt injection"));
    }

    @Test
    void allIrrelevantDocumentsAreReplacedByWebContext() throws Exception {
        var graph = CorrectiveRagWorkflow.build(
                question -> List.of(IRRELEVANT),
                (question, document) -> false,
                question -> "agent memory overview",
                question -> List.of(WEB_RESULT),
                CorrectiveRagStubTest::answerFromContext
        );

        var result = graph.invoke(Map.of(
                CorrectiveRagWorkflow.RagState.QUESTION, "Explain agent memory"
        ));

        assertTrue(result.isPresent());
        var state = result.get();
        assertEquals(List.of(WEB_RESULT), state.documents());
        var answer = state.generation().orElse("");
        assertTrue(answer.contains("Episodic memory"));
        assertFalse(answer.contains("Prompt injection"));
    }

    @Test
    void emptyRetrievalStillUsesWebSearchAndTerminates() throws Exception {
        var webCalls = new AtomicInteger();
        var graph = CorrectiveRagWorkflow.build(
                question -> List.of(),
                (question, document) -> false,
                question -> "agent memory overview",
                question -> {
                    webCalls.incrementAndGet();
                    return List.of(WEB_RESULT);
                },
                CorrectiveRagStubTest::answerFromContext
        );

        var result = graph.invoke(Map.of(
                CorrectiveRagWorkflow.RagState.QUESTION, "Explain agent memory"
        ));

        assertTrue(result.isPresent());
        assertEquals(1, webCalls.get());
        assertEquals(List.of(WEB_RESULT), result.get().documents());
        assertTrue(result.get().generation().orElse("").contains("Episodic memory"));
    }

    private static String answerFromContext(
            String question,
            List<CorrectiveRagWorkflow.RagDocument> documents) {
        var context = documents.stream()
                .map(CorrectiveRagWorkflow.RagDocument::content)
                .collect(Collectors.joining("\n"));
        return "Question: " + question + "\n" + context;
    }
}
