package org.bsc.langgraph4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


public class Issue118ITest {

    static org.slf4j.Logger log;

    public static class State extends MessagesState<ChatMessage> {
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    @BeforeAll
    public static void init() throws Exception {
        try (var file = new java.io.FileInputStream("./logging.properties")) {
            java.util.logging.LogManager.getLogManager().readConfiguration(file);
        }

        log = org.slf4j.LoggerFactory.getLogger("issue118");
    }

    @Test
    public void testinterruptGraphWhileStreamingNodeOutput() throws Exception {
        // setup streaming model
        var model = OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:11434")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("llama3.1:latest")
                .build();

        NodeAction<State> calculationNode = state -> {

            log.trace("calculationNode: {}", state.messages());

            var generator = StreamingChatGenerator.<State>builder()
                    .mapResult(response -> Map.of("messages", response.aiMessage()))
                    .startingNode("calculation")
                    .startingState(state)
                    .build();

            var request = ChatRequest.builder()
                    .messages(state.messages())
                    .build();

            model.chat(request, generator.handler());

            return Map.of("_streaming_messages", generator);
        };

        NodeAction<State> summaryNode = state -> {
            log.trace("summaryNode: {}", state.messages());

            var lastMessage = state.lastMessage().orElseThrow();

            return Map.of();
        };

        var stateSerializer = new LC4jStateSerializer<State>(State::new);

        // Define Graph
        var workflow = new StateGraph<State>(State.SCHEMA, stateSerializer)
                .addNode("calculation", node_async(calculationNode))
                .addNode("summary", node_async(summaryNode))
                .addEdge(START, "calculation")
                .addEdge("calculation", "summary" )
                .addEdge("summary", END);

        var app = workflow.compile();

        for( var out : app.stream( GraphInput.args(Map.of( "messages", UserMessage.from( "generate a UUID for me"))), RunnableConfig.empty() ) ) {
            if( out instanceof StreamingOutput<?> streaming ) {
                log.info( "StreamingOutput{node={}, chunk={} }", streaming.node(), streaming.chunk() );
            }
            else {
                log.info( "{} - {}", out.node(), out.state().lastMessage().orElseThrow() );
            }
        }
    }


}
