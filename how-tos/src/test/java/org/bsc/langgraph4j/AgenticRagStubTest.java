package org.bsc.langgraph4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI-friendly coverage for the Agentic RAG how-to.
 *
 * Uses real LangChain4j messages, tool requests, tool responses, and LC4jToolService.
 * The retriever body is deterministic so the test does not need OpenAI or network access.
 */
public class AgenticRagStubTest {

    static class BlogTools {

        @Tool("Search and return information about Lilian Weng blog posts.")
        String retrieveBlogPosts(@P("search query") String query) {
            return "Lilian Weng describes two types of reward hacking: "
                    + "environment or goal misspecification, and reward tampering.";
        }
    }

    @Test
    void langChain4jToolPathRetrievesGradesAndAnswers() throws Exception {
        var toolService = LC4jToolService.builder()
                .toolsFromObject(new BlogTools())
                .build();

        NodeAction<MessagesState<ChatMessage>> retrieve = state -> {
            var last = state.lastMessage().orElseThrow();
            if (last instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                return toolService.execute(
                                aiMessage.toolExecutionRequests(),
                                InvocationContext.builder()
                                        .invocationParameters(InvocationParameters.from(state.data()))
                                        .build(),
                                "messages")
                        .thenApply(Command::update)
                        .join();
            }
            return Map.of();
        };

        EdgeAction<MessagesState<ChatMessage>> gradeDocuments = state -> {
            var context = state.lastMessage()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .map(ToolExecutionResultMessage::text)
                    .orElse("");
            return context.contains("reward tampering") ? "generate_answer" : "rewrite_question";
        };

        NodeAction<MessagesState<ChatMessage>> rewriteQuestion = state -> Map.of(
                "messages",
                UserMessage.from("What are the types of reward hacking described by Lilian Weng?")
        );

        NodeAction<MessagesState<ChatMessage>> generateAnswer = state -> {
            var context = state.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .map(ToolExecutionResultMessage::text)
                    .collect(Collectors.joining("\n\n"));
            return Map.of("messages", AiMessage.from("Based on the retrieved context: " + context));
        };

        var stateSerializer = new LC4jStateSerializer<MessagesState<ChatMessage>>(MessagesState::new);

        var graph = new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addNode("retrieve", node_async(retrieve))
                .addNode("rewrite_question", node_async(rewriteQuestion))
                .addNode("generate_answer", node_async(generateAnswer))
                .addEdge(START, "retrieve")
                .addConditionalEdges("retrieve", edge_async(gradeDocuments), Map.of(
                        "generate_answer", "generate_answer",
                        "rewrite_question", "rewrite_question"
                ))
                .addEdge("rewrite_question", "retrieve")
                .addEdge("generate_answer", END)
                .compile();

        var toolRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("retrieveBlogPosts")
                .arguments("{\"arg0\":\"types of reward hacking\"}")
                .build();
        var toolCall = new AiMessage.Builder()
                .text("")
                .toolExecutionRequests(List.of(toolRequest))
                .build();

        var result = graph.invoke(GraphInput.args(Map.of("messages", List.of(
                UserMessage.from("What does Lilian Weng say about types of reward hacking?"),
                toolCall))), RunnableConfig.empty());

        assertTrue(result.isPresent());
        var messages = result.get().messages();
        assertTrue(messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .anyMatch(message -> message.text().contains("reward tampering")));
        var last = messages.get(messages.size() - 1);
        assertInstanceOf(AiMessage.class, last);
        assertTrue(((AiMessage) last).text().contains("reward tampering"));
    }
}
