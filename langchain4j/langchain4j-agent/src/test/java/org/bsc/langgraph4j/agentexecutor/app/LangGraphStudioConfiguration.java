package org.bsc.langgraph4j.agentexecutor.app;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.agentexecutor.TestTool;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Configuration
public class LangGraphStudioConfiguration extends LangGraphStudioConfig {
    enum AiModel {

        OPENAI_GPT_4O_MINI( OpenAiChatModel.builder()
                .apiKey( System.getenv("OPENAI_API_KEY") )
                .modelName( "gpt-4o-mini" )
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() ),
        OLLAMA_QWEN3_14B( OllamaChatModel.builder()
                .modelName( "qwen3:14b" )
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() ),
        OLLAMA_QWEN2_5_7B( OllamaChatModel.builder()
                .modelName( "qwen2.5:7b" )
                .baseUrl("http://localhost:11434")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .temperature(0.0)
                .build() )
        ;

        public final ChatModel model;

        AiModel(  ChatModel model ) {
            this.model = model;
        }
    }

    final Map<String, LangGraphStudioServer.Instance> instanceMap;

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return instanceMap;
    }

    public LangGraphStudioConfiguration() throws GraphStateException {

        var workflow =  AgentExecutorEx.builder()
                .chatModel(AiModel.OLLAMA_QWEN2_5_7B.model)
                .toolsFromObject(new TestTool())
                .build();
        System.out.println( workflow.getGraph(GraphRepresentation.Type.PLANTUML, "ReACT Agent", false ).content() );

        this.instanceMap = agentWorkflow( workflow );
    }

    private Map<String, LangGraphStudioServer.Instance> agentWorkflow( StateGraph<? extends AgentState> workflow ) throws GraphStateException {

        return  Map.of( "sample", LangGraphStudioServer.Instance.builder()
                            .title("LangGraph Studio (LangChain4j)")
                            .addInputStringArg( "messages", true, v -> UserMessage.from( Objects.toString(v) ) )
                            .graph( workflow )
                            .compileConfig( CompileConfig.builder()
                                    .checkpointSaver( new MemorySaver() )
                                    .build())
                            .build());

    }

}
