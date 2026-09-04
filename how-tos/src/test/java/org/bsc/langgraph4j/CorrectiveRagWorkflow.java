package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Shared graph used by the deterministic and live Corrective RAG tests.
 *
 * <p>The external services are injected so the graph topology is tested without
 * credentials while the live integration test can use the same workflow.</p>
 */
final class CorrectiveRagWorkflow {

    private CorrectiveRagWorkflow() {
    }

    /** A small serializable representation shared by vector and web results. */
    record RagDocument(String content, String source) implements Serializable {
    }

    static final class RagState extends AgentState {

        static final String QUESTION = "question";
        static final String DOCUMENTS = "documents";
        static final String WEB_SEARCH = "web_search";
        static final String GENERATION = "generation";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                QUESTION, Channels.base(() -> ""),
                DOCUMENTS, Channels.base(ArrayList::new),
                WEB_SEARCH, Channels.base(() -> false),
                GENERATION, Channels.base(() -> "")
        );

        RagState(Map<String, Object> initData) {
            super(initData);
        }

        String question() {
            return this.<String>value(QUESTION).orElse("");
        }

        @SuppressWarnings("unchecked")
        List<RagDocument> documents() {
            return this.<List<RagDocument>>value(DOCUMENTS).orElse(List.of());
        }

        boolean webSearch() {
            return this.<Boolean>value(WEB_SEARCH).orElse(false);
        }

        Optional<String> generation() {
            return value(GENERATION);
        }
    }

    @FunctionalInterface
    interface Retriever {
        List<RagDocument> retrieve(String question);
    }

    @FunctionalInterface
    interface RelevanceGrader {
        boolean isRelevant(String question, RagDocument document);
    }

    @FunctionalInterface
    interface QueryRewriter {
        String rewrite(String question);
    }

    @FunctionalInterface
    interface WebSearcher {
        List<RagDocument> search(String question);
    }

    @FunctionalInterface
    interface AnswerGenerator {
        String generate(String question, List<RagDocument> documents);
    }

    static CompiledGraph<RagState> build(
            Retriever retriever,
            RelevanceGrader grader,
            QueryRewriter rewriter,
            WebSearcher webSearcher,
            AnswerGenerator answerGenerator) throws GraphStateException {

        NodeAction<RagState> retrieve = state -> {
            var retrieved = Optional.ofNullable(retriever.retrieve(state.question()))
                    .orElse(List.of());
            return Map.of(
                    RagState.DOCUMENTS, new ArrayList<>(retrieved),
                    RagState.WEB_SEARCH, false
            );
        };

        NodeAction<RagState> gradeDocuments = state -> {
            var filtered = new ArrayList<RagDocument>();
            // An empty retrieval needs correction just as much as an irrelevant one.
            var needsWebSearch = state.documents().isEmpty();
            for (var document : state.documents()) {
                if (grader.isRelevant(state.question(), document)) {
                    filtered.add(document);
                } else {
                    needsWebSearch = true;
                }
            }
            return Map.of(
                    RagState.DOCUMENTS, filtered,
                    RagState.WEB_SEARCH, needsWebSearch
            );
        };

        EdgeAction<RagState> decideToGenerate = state ->
                state.webSearch() ? "transform_query" : "generate";

        NodeAction<RagState> transformQuery = state -> {
            var rewritten = rewriter.rewrite(state.question());
            // Preserve a usable query if a model unexpectedly returns a blank rewrite.
            var nextQuestion = rewritten == null || rewritten.isBlank()
                    ? state.question()
                    : rewritten;
            return Map.of(RagState.QUESTION, nextQuestion);
        };

        NodeAction<RagState> webSearch = state -> {
            var merged = new ArrayList<>(state.documents());
            var webDocuments = Optional.ofNullable(webSearcher.search(state.question()))
                    .orElse(List.of());
            merged.addAll(webDocuments);
            return Map.of(RagState.DOCUMENTS, merged);
        };

        NodeAction<RagState> generate = state -> Map.of(
                RagState.GENERATION,
                answerGenerator.generate(state.question(), List.copyOf(state.documents()))
        );

        return new StateGraph<>(RagState.SCHEMA, RagState::new)
                .addNode("retrieve", node_async(retrieve))
                .addNode("grade_documents", node_async(gradeDocuments))
                .addNode("transform_query", node_async(transformQuery))
                .addNode("web_search", node_async(webSearch))
                .addNode("generate", node_async(generate))
                .addEdge(START, "retrieve")
                .addEdge("retrieve", "grade_documents")
                .addConditionalEdges("grade_documents", edge_async(decideToGenerate), Map.of(
                        "transform_query", "transform_query",
                        "generate", "generate"
                ))
                .addEdge("transform_query", "web_search")
                .addEdge("web_search", "generate")
                .addEdge("generate", END)
                .compile();
    }
}
