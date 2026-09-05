# Corrective RAG (CRAG) with LangGraph4j

This how-to ports the LangGraph [Corrective RAG tutorial](https://github.com/langchain-ai/langgraph/blob/0035ab9825cd/docs/docs/tutorials/rag/langgraph_crag.ipynb) to Java with LangGraph4j and LangChain4j.

[Corrective Retrieval Augmented Generation](https://arxiv.org/abs/2401.15884) evaluates retrieved knowledge before generation and takes corrective action when retrieval quality is poor. The paper also describes confidence-based actions and a knowledge-refinement step. Like the original LangGraph tutorial, this introductory implementation intentionally uses a smaller workflow:

* grade each retrieved document for relevance;
* keep relevant local documents;
* if retrieval is empty or any document is irrelevant, rewrite the query and supplement the retained context with web search;
* generate an answer from the corrected context.

The resulting graph is:

```text
START -> retrieve -> grade_documents -> generate -> END
                                  |
                                  +-> transform_query -> web_search -> generate -> END
```

LangChain4j provides document loading, OpenAI-compatible chat and embeddings, retrieval, structured model output, and Tavily integration. LangGraph4j provides the state and graph orchestration.

## Setup

Run the notebook with the [rapaio Jupyter Java kernel](https://github.com/padreati/rapaio-jupyter-kernel). Install LangGraph4j in your local Maven repository first, then configure an OpenAI-compatible service and Tavily.

The selected model service must expose both chat-completions and embedding endpoints. OpenAI works with the default model names. For Qwen, use Alibaba Cloud Model Studio's [OpenAI-compatible endpoint](https://help.aliyun.com/zh/model-studio/model-calling-in-sub-workspace):

| Variable | OpenAI default | Qwen example |
| --- | --- | --- |
| `OPENAI_API_KEY` | Your OpenAI API key | Your Model Studio API key |
| `OPENAI_BASE_URL` | Leave unset | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | `qwen-plus` |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | `text-embedding-v4` |
| `TAVILY_API_KEY` | Your Tavily API key | Your Tavily API key |

If Model Studio displays a workspace-specific endpoint for your region, use that value for `OPENAI_BASE_URL`.

```java
var userHomeDir = System.getProperty("user.home");
var localRepoUrl = "file://" + userHomeDir + "/.m2/repository/";
var langgraph4jVersion = "1.8.26";
var langchain4jVersion = "1.19.0";
var langchain4jBetaVersion = "1.19.0-beta29";
```

```java
%dependency /add-repo local \{localRepoUrl} release|never snapshot|always
%dependency /add org.slf4j:slf4j-jdk14:2.0.9
%dependency /add org.bsc.langgraph4j:langgraph4j-langchain4j:\{langgraph4jVersion}
%dependency /add dev.langchain4j:langchain4j:\{langchain4jVersion}
%dependency /add dev.langchain4j:langchain4j-open-ai:\{langchain4jVersion}
%dependency /add dev.langchain4j:langchain4j-web-search-engine-tavily:\{langchain4jBetaVersion}
%dependency /resolve
```

```java
var openAiKey = System.getenv("OPENAI_API_KEY");
var openAiBaseUrl = System.getenv("OPENAI_BASE_URL");
var chatModelName = System.getenv().getOrDefault("OPENAI_CHAT_MODEL", "gpt-4o-mini");
var embeddingModelName = System.getenv().getOrDefault("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small");
var tavilyKey = System.getenv("TAVILY_API_KEY");
if (openAiKey == null || openAiKey.isBlank()) {
    throw new IllegalStateException("Set OPENAI_API_KEY before running this notebook");
}
if (tavilyKey == null || tavilyKey.isBlank()) {
    throw new IllegalStateException("Set TAVILY_API_KEY before running this notebook");
}
```

## Build the local index

Load the same three blog posts used by the original tutorial and index them in an in-memory embedding store.

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

var urls = List.of(
        "https://lilianweng.github.io/posts/2023-06-23-agent/",
        "https://lilianweng.github.io/posts/2023-03-15-prompt-engineering/",
        "https://lilianweng.github.io/posts/2023-10-25-adv-attack-llm/"
);

var sourceDocuments = urls.stream()
        .map(url -> UrlDocumentLoader.load(url, new TextDocumentParser()))
        .toList();

var embeddingModelBuilder = OpenAiEmbeddingModel.builder()
        .apiKey(openAiKey)
        .modelName(embeddingModelName)
        // Qwen text-embedding-v4 accepts at most 10 inputs per request.
        .maxSegmentsPerBatch(10);
// A custom base URL lets the same tutorial run against Qwen or another compatible service.
if (openAiBaseUrl != null && !openAiBaseUrl.isBlank()) {
    embeddingModelBuilder.baseUrl(openAiBaseUrl);
}
var embeddingModel = embeddingModelBuilder.build();

var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
EmbeddingStoreIngestor.builder()
        .documentSplitter(DocumentSplitters.recursive(250, 0))
        .embeddingModel(embeddingModel)
        .embeddingStore(embeddingStore)
        .build()
        .ingest(sourceDocuments);

var retriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(3)
        .build();
```

## Define the graph state

Both local vector results and web results are converted to a small serializable record. A base channel is used for `documents` because the grading node replaces the retrieved list with its filtered list; the web-search node then explicitly merges new results into that list.

```java
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

record RagDocument(String content, String source) implements Serializable {}

class CorrectiveRagState extends AgentState {
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

    CorrectiveRagState(Map<String, Object> initData) {
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
```

## Configure grading, rewriting, and generation

The relevance grader uses LangChain4j structured output. Any value other than `yes` is treated conservatively as irrelevant.

```java
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

import java.time.Duration;

class GradeDocuments {
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

var chatModelBuilder = OpenAiChatModel.builder()
        .apiKey(openAiKey)
        .modelName(chatModelName)
        .temperature(0.0)
        .timeout(Duration.ofMinutes(2))
        .maxRetries(2);
if (openAiBaseUrl != null && !openAiBaseUrl.isBlank()) {
    chatModelBuilder.baseUrl(openAiBaseUrl);
}
var chatModel = chatModelBuilder.build();

var documentGrader = AiServices.create(DocumentGrader.class, chatModel);
var questionRewriter = AiServices.create(QuestionRewriter.class, chatModel);
var answerGenerator = AiServices.create(AnswerGenerator.class, chatModel);
var webSearchEngine = TavilyWebSearchEngine.builder()
        .apiKey(tavilyKey)
        .build();
```

## Implement the nodes

Retrieve local context first. Preserve each source URL so the final generation prompt can identify where every piece of context came from.

```java
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchRequest;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.stream.Collectors;

String localSource(TextSegment segment) {
    var source = segment.metadata().getString(Document.URL);
    return source == null || source.isBlank() ? "local-vector-store" : source;
}

NodeAction<CorrectiveRagState> retrieve = state -> {
    var documents = retriever.retrieve(new Query(state.question())).stream()
            .map(content -> new RagDocument(
                    content.textSegment().text(),
                    localSource(content.textSegment())
            ))
            .toList();
    return Map.of(
            CorrectiveRagState.DOCUMENTS, new ArrayList<>(documents),
            CorrectiveRagState.WEB_SEARCH, false
    );
};
```

Grade every document independently. If retrieval returns nothing, or if at least one document is rejected, the graph takes the corrective branch. Relevant local documents remain available to be combined with web results.

```java
NodeAction<CorrectiveRagState> gradeDocuments = state -> {
    var filtered = new ArrayList<RagDocument>();
    // Empty retrieval is a failed retrieval and therefore needs correction.
    var needsWebSearch = state.documents().isEmpty();
    for (var document : state.documents()) {
        var prompt = "Question: " + state.question()
                + "\n\nDocument:\n" + document.content();
        var grade = documentGrader.grade(prompt);
        if (grade != null && "yes".equalsIgnoreCase(grade.binaryScore)) {
            filtered.add(document);
        } else {
            needsWebSearch = true;
        }
    }
    return Map.of(
            CorrectiveRagState.DOCUMENTS, filtered,
            CorrectiveRagState.WEB_SEARCH, needsWebSearch
    );
};

EdgeAction<CorrectiveRagState> decideToGenerate = state ->
        state.webSearch() ? "transform_query" : "generate";
```

Rewrite the query only on the corrective path, then add up to three Tavily results.

```java
NodeAction<CorrectiveRagState> transformQuery = state -> {
    var rewritten = questionRewriter.rewrite(state.question());
    // Keep the original query if the model unexpectedly returns an empty rewrite.
    var nextQuestion = rewritten == null || rewritten.isBlank()
            ? state.question()
            : rewritten;
    return Map.of(CorrectiveRagState.QUESTION, nextQuestion);
};

String webResultText(String content, String snippet, String title) {
    if (content != null && !content.isBlank()) return content;
    if (snippet != null && !snippet.isBlank()) return snippet;
    return title == null ? "" : title;
}

NodeAction<CorrectiveRagState> webSearch = state -> {
    var merged = new ArrayList<>(state.documents());
    webSearchEngine.search(WebSearchRequest.from(state.question(), 3))
            .results().stream()
            .map(result -> new RagDocument(
                    webResultText(result.content(), result.snippet(), result.title()),
                    result.url().toString()
            ))
            .forEach(merged::add);
    return Map.of(CorrectiveRagState.DOCUMENTS, merged);
};
```

Generate from only the filtered and supplemented context.

```java
String formatContext(List<RagDocument> documents) {
    return documents.stream()
            .map(document -> "Source: " + document.source() + "\n" + document.content())
            .collect(Collectors.joining("\n\n"));
}

NodeAction<CorrectiveRagState> generate = state -> {
    var prompt = "Question: " + state.question()
            + "\n\nContext:\n" + formatContext(state.documents());
    return Map.of(CorrectiveRagState.GENERATION, answerGenerator.answer(prompt));
};
```

## Assemble and run the graph

The graph has no correction loop: it performs at most one query rewrite and one web search before generation.

```java
import org.bsc.langgraph4j.StateGraph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

var graph = new StateGraph<>(CorrectiveRagState.SCHEMA, CorrectiveRagState::new)
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
```

```java
var result = graph.invoke(Map.of(
        CorrectiveRagState.QUESTION,
        "What are the main components of an LLM-powered autonomous agent system?"
)).orElseThrow();

System.out.println("Final query: " + result.question());
System.out.println("Web search used: " + result.webSearch());
System.out.println(result.generation().orElse("No answer generated"));
```

For deterministic CI coverage without API keys, see `CorrectiveRagStubTest`. `CorrectiveRagITest` exercises the same topology with the configured OpenAI-compatible service and Tavily and is excluded from default Surefire runs.
