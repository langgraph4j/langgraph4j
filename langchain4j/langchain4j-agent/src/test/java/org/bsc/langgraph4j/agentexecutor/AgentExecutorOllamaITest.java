package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.bsc.langgraph4j.StateGraph;


public class AgentExecutorOllamaITest extends AbstractAgentExecutorTest {


    @Override
    protected StateGraph<AgentExecutor.State> newGraph(AgentExecutor.Serializers serializer) throws Exception {

        final var chatModel = OllamaChatModel.builder()
                .modelName( "qwen3.5" )
                .baseUrl("http://localhost:11434")
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build();

        return AgentExecutor.builder()
                .stateSerializer(serializer.object())
                .chatModel(chatModel)
                .toolsFromObject(new TestTools())
                .build();

    }

    @Override
    protected StateGraph<AgentExecutor.State> newGraphWithStreaming( AgentExecutor.Serializers serializer, boolean emitStreamingOutputEnd ) throws Exception {
        final var chatModel = OllamaStreamingChatModel.builder()
                .modelName( "qwen3.5" )
                .baseUrl("http://localhost:11434")
                .logResponses(true)
                .temperature(0.0)
                .build();

        return AgentExecutor.builder()
                .stateSerializer(serializer.object())
                .chatModel(chatModel, emitStreamingOutputEnd)
                .toolsFromObject(new TestTools())
                .build();

    }
}
