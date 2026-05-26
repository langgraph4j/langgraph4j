package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Issue391ITest implements LG4JLoggable {

    public static class State extends MessagesState<ChatMessage> {

        public static final Map<String, Channel<?>> SCHEMA = MessagesState.SCHEMA;

        public State(Map<String, Object> initData) {
            super(initData);
        }

    }

    public static class JokeTeller implements AsyncNodeActionWithConfig<State> {

        final String systemPrompt = """
                You are my joke teller.
                """;

        private final StreamingChatModel streamingChatModel = AiStreamingModel.OLLAMA.chatModel("qwen3");

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            log.info("--------> JokeTeller <--------");


            List<ChatMessage> chatMessages = state.messages();

            var generator = StreamingChatGenerator.<State>builder()
                    .mapResult(response -> Map.of("messages", response.aiMessage()))
                    .startingNode("output_node")
                    .startingState(state)
                    .build();

            chatMessages.add(0, SystemMessage.from(systemPrompt));

            var request = ChatRequest.builder()
                    .parameters(ChatRequestParameters.builder()
                            .build())
                    .messages(chatMessages)
                    .build();

            streamingChatModel.chat(request, generator.handler());

            log.info("--------> JokeTeller Result <-------- ");

            return completedFuture(Map.of("_streaming_messages", generator));
        }
    }

    @Test
    public void testCheckpointPresence() throws Exception {

        final var saver = new MemorySaver();

        final var config = CompileConfig.builder()
                .checkpointSaver( saver )
                .build();

        final var stateSerializer = new LC4jJacksonStateSerializer<>( State::new );

        final var graph = new StateGraph<>( State.SCHEMA, stateSerializer )
                .addNode( "output_node", new JokeTeller() )
                .addEdge( START, "output_node" )
                .addEdge( "output_node", END )
                .compile(config)
                ;

        final var iterator = graph.stream(
                GraphInput.args( Map.of("messages",
                                    UserMessage.from("Tell me a joke"))),
                RunnableConfig.empty());

        final var graphResult = iterator.forEachAsync( output -> {
            log.info("Output: {}", output);
        })
        .thenApply( GraphResult::from )
        .whenComplete( ($1, throwable) -> {
            log.info("""
        Stream completed: {}
        ---------------------------
        
        """, $1);
        })
        .join();

        assertTrue( graphResult.isCheckpointSaverTag() );


        final var checkpoints = graphResult.asCheckpointSaverTag().checkpoints();

        assertEquals( 2, checkpoints.size() );

        checkpoints.forEach( checkpoint -> {
            log.info("Checkpoint: nodeId:{}, nextNodeId:{}", checkpoint.getNodeId(), checkpoint.getNextNodeId());
        });
    }
}
