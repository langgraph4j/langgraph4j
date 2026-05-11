package org.bsc.langgraph4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class SearchTool {

    @Tool("Use to surf the web, fetch current information, check the weather, and retrieve other information.")
    String execQuery(@P("The query to use in your search.") String query) {

        // This is a placeholder for the actual implementation
        return "Cold, with a low of 13 degrees";
    }
}

public class StreamingTestITest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StreamingTestITest.class);

    @BeforeAll
    public static void loadEnv() {
        DotEnvConfig.load();
    }

    @Test
    public void streamingTest() throws Exception {
        var openApiKey = DotEnvConfig.valueOf("OPENAI_API_KEY")
                .orElseThrow(() -> new IllegalArgumentException("no APIKEY provided!"));

        var stateSerializer = new ObjectStreamStateSerializer<MessagesState<ChatMessage>>(MessagesState::new);
        stateSerializer.mapper()
                // Setup custom serializer for Langchain4j ToolExecutionRequest
                .register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer())
                // Setup custom serializer for Langchain4j AiMessage
                .register(ChatMessage.class, new ChatMesssageSerializer());

        // setup streaming model
        var model = OpenAiStreamingChatModel.builder()
                .apiKey(openApiKey)
                .modelName("gpt-4o-mini")
                .logResponses(true)
                .temperature(0.0)
                .maxTokens(2000)
                .build();

        // setup tools
        var tools = LC4jToolService.builder()
                .toolsFromObject(new SearchTool())
                .build();

        // Call Model
        NodeAction<MessagesState<ChatMessage>> callModel = state -> {
            log.info("CallModel:\n{}", state.messages());

            var generator = StreamingChatGenerator.builder()
                    .mapResult(response -> {
                        log.info("MapResult: {}", response);
                        return Map.of("messages", response.aiMessage());
                    })
                    .startingNode("agent")
                    .startingState(state)
                    .build();

            var params = ChatRequestParameters.builder()
                    .toolSpecifications( tools.toolSpecifications() )
                    .build();
            var request = ChatRequest.builder()
                    .parameters( params )
                    .messages( state.messages() )
                    .build();
            model.chat(request, generator.handler() );

            return Map.of("messages", generator);
        };

        // Route Message
        EdgeAction<MessagesState<ChatMessage>> routeMessage = state -> {
            log.info("routeMessage:\n{}", state.messages());

            var lastMessage = state.lastMessage()
                    .orElseThrow(() -> (new IllegalStateException("last message not found!")));

            if (lastMessage instanceof AiMessage message) {
                // If tools should be called
                if (message.hasToolExecutionRequests()) return "next";
            }

            // If no tools are called, we can finish (respond to the user)
            return "exit";
        };

        // Invoke Tool
        AsyncNodeAction<MessagesState<ChatMessage>> invokeTool = state -> {
            log.info("invokeTool:\n{}", state.messages());

            var lastMessage = state.lastMessage()
                    .orElseThrow(() -> (new IllegalStateException("last message not found!")));


            if (lastMessage instanceof AiMessage lastAiMessage) {

                return tools.execute(
                        lastAiMessage.toolExecutionRequests(),
                        InvocationContext.builder()
                                .invocationParameters( InvocationParameters.from(Map.of()) )
                                .build(), "messages")
                        .thenApply(Command::update);

            }

            return CompletableFuture.failedFuture( new IllegalStateException("invalid last message") );
        };

        // Define Graph
        var workflow = new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addNode("agent", node_async(callModel))
                .addNode("tools", invokeTool)
                .addEdge(START, "agent")
                .addConditionalEdges("agent",
                        edge_async(routeMessage),
                        Map.of("next", "tools", "exit", END))
                .addEdge("tools", "agent");

        var iterator = workflow.compile()
                .stream( GraphInput.args(Map.of( "messages", UserMessage.from( "what is the whether today?"))), RunnableConfig.empty() );

        for( var out : iterator ) {
            log.info( "{}", out );
        }

    }

}
