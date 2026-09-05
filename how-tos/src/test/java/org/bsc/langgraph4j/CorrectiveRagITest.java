package org.bsc.langgraph4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live OpenAI-compatible model and Tavily coverage for the Corrective RAG how-to.
 *
 * <p>Excluded from normal Surefire runs by the {@code *ITest} rule. Run it
 * explicitly with {@code OPENAI_API_KEY} and {@code TAVILY_API_KEY}. Set
 * {@code OPENAI_BASE_URL}, {@code OPENAI_CHAT_MODEL}, and
 * {@code OPENAI_EMBEDDING_MODEL} when using another OpenAI-compatible service.</p>
 */
public class CorrectiveRagITest {

    private record ModelConfig(
            String apiKey,
            String baseUrl,
            String chatModel,
            String embeddingModel
    ) {
    }

    static class GradeDocuments {
        @Description("Relevance score: 'yes' if relevant, or 'no' if not relevant")
        public String binaryScore;
    }

    interface DocumentGrader {
        @SystemMessage("Grade whether the retrieved document is relevant to the question. "
                + "Return yes only when it contains information that can help answer the question; otherwise return no.")
        GradeDocuments grade(@dev.langchain4j.service.UserMessage String prompt);
    }

    interface QuestionRewriter {
        @SystemMessage("Rewrite the user question into a concise query optimized for web search. "
                + "Return only the rewritten query.")
        String rewrite(@dev.langchain4j.service.UserMessage String question);
    }

    interface AnswerGenerator {
        @SystemMessage("Answer the question using only the supplied context. "
                + "If the context is insufficient, say so. Keep the answer concise and cite source URLs when present.")
        String answer(@dev.langchain4j.service.UserMessage String prompt);
    }

    @Test
    void liveWorkflowCorrectsIrrelevantRetrievalWithWebSearch() throws Exception {
        var modelConfig = loadModelConfig();
        var tavilyKey = System.getenv("TAVILY_API_KEY");
        Assumptions.assumeTrue(!modelConfig.apiKey().isBlank(),
                "OPENAI_API_KEY is required for CorrectiveRagITest");
        Assumptions.assumeTrue(tavilyKey != null && !tavilyKey.isBlank(),
                "TAVILY_API_KEY is required for CorrectiveRagITest");

        var documents = loadBlogDocuments();
        // The configured OpenAI-compatible service must expose both chat and
        // embedding endpoints because retrieval and generation share one client setup.
        var embeddingModel = createEmbeddingModel(modelConfig);
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(250, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(documents);

        var contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .build();
        var chatModel = createChatModel(modelConfig);
        var documentGrader = AiServices.create(DocumentGrader.class, chatModel);
        var questionRewriter = AiServices.create(QuestionRewriter.class, chatModel);
        var answerGenerator = AiServices.create(AnswerGenerator.class, chatModel);
        var webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyKey)
                .build();
        var webResultCount = new AtomicInteger();

        var graph = CorrectiveRagWorkflow.build(
                question -> contentRetriever.retrieve(new Query(question)).stream()
                        .map(content -> toRagDocument(content.textSegment()))
                        .toList(),
                (question, document) -> {
                    var prompt = "Question: " + question + "\n\nDocument:\n" + document.content();
                    var grade = documentGrader.grade(prompt);
                    return grade != null && "yes".equalsIgnoreCase(grade.binaryScore);
                },
                questionRewriter::rewrite,
                question -> {
                    var webDocuments = webSearchEngine.search(WebSearchRequest.from(question, 3))
                            .results().stream()
                            .map(result -> new CorrectiveRagWorkflow.RagDocument(
                                    webResultText(result.content(), result.snippet(), result.title()),
                                    result.url().toString()
                            ))
                            .toList();
                    // Track the actual Tavily response instead of inferring it from HTTP source URLs.
                    webResultCount.set(webDocuments.size());
                    return webDocuments;
                },
                (question, contextDocuments) -> answerGenerator.answer(
                        "Question: " + question + "\n\nContext:\n" + formatContext(contextDocuments)
                )
        );

        // This question is intentionally outside the local agent-related corpus.
        var result = graph.invoke(GraphInput.args(Map.of(
                CorrectiveRagWorkflow.RagState.QUESTION,
                "What is the weather in Rome today?")),
                RunnableConfig.empty()
        );

        assertTrue(result.isPresent());
        var state = result.get();
        assertTrue(state.webSearch());
        assertFalse(state.documents().isEmpty());
        assertTrue(webResultCount.get() > 0);
        assertTrue(state.generation().filter(answer -> !answer.isBlank()).isPresent());
    }

    private static ModelConfig loadModelConfig() {
        var apiKey = System.getenv("OPENAI_API_KEY");
        return new ModelConfig(
                apiKey == null ? "" : apiKey,
                System.getenv("OPENAI_BASE_URL"),
                envOrDefault("OPENAI_CHAT_MODEL", "gpt-4o-mini"),
                envOrDefault("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")
        );
    }

    private static String envOrDefault(String name, String defaultValue) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static EmbeddingModel createEmbeddingModel(ModelConfig config) {
        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.embeddingModel())
                // Qwen text-embedding-v4 accepts at most 10 inputs per request.
                .maxSegmentsPerBatch(10);
        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            builder.baseUrl(config.baseUrl());
        }
        return builder.build();
    }

    private static ChatModel createChatModel(ModelConfig config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.chatModel())
                .temperature(0.0)
                .timeout(Duration.ofMinutes(2))
                .maxRetries(2);
        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            builder.baseUrl(config.baseUrl());
        }
        return builder.build();
    }

    private static List<Document> loadBlogDocuments() {
        return Stream.of(
                        "https://lilianweng.github.io/posts/2023-06-23-agent/",
                        "https://lilianweng.github.io/posts/2023-03-15-prompt-engineering/",
                        "https://lilianweng.github.io/posts/2023-10-25-adv-attack-llm/"
                )
                .map(url -> UrlDocumentLoader.load(url, new TextDocumentParser()))
                .toList();
    }

    private static CorrectiveRagWorkflow.RagDocument toRagDocument(TextSegment segment) {
        var source = segment.metadata().getString(Document.URL);
        return new CorrectiveRagWorkflow.RagDocument(
                segment.text(),
                source == null || source.isBlank() ? "local-vector-store" : source
        );
    }

    private static String webResultText(String content, String snippet, String title) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (snippet != null && !snippet.isBlank()) {
            return snippet;
        }
        return title == null ? "" : title;
    }

    private static String formatContext(List<CorrectiveRagWorkflow.RagDocument> documents) {
        return documents.stream()
                .map(document -> "Source: " + document.source() + "\n" + document.content())
                .collect(Collectors.joining("\n\n"));
    }
}
