package org.bsc.langgraph4j.jetty;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.servlet.DispatcherType;
import org.bsc.langgraph4j.SampleGraph;
import org.bsc.langgraph4j.TestTool;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.jetty.LangGraphStudioServer4Jetty;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.SampleGraph.issue216;
import static org.bsc.langgraph4j.SampleGraph.issue241;

public interface LG4JStudioApplication {

    static Map.Entry<String, LangGraphStudioServer.Instance> agentExecutor() throws Exception {
        var llm = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("qwen2.5:7b")
                .build();

        var app = AgentExecutorEx.builder()
                .chatModel(llm)
                .toolsFromObject( new TestTool() )
                .stateSerializer( AgentExecutorEx.Serializers.JSON.object() )
                .build();

        return Map.entry( "agent_executor", LangGraphStudioServer.Instance.builder()
                .title("AGENT EXECUTOR")
                .addInputStringArg("messages", true, v -> SystemMessage.from(Objects.toString(v)))
                .graph(app)
                .build());

    }

    static void main(String[] args) throws Exception {

        LangGraphStudioServer4Jetty.builder()
                .port(8081)
                .instance(agentExecutor())
                .instance(Map.entry("issue216", issue216()))
                .instance(Map.entry("issue241", issue241()))
                .instance(Map.entry( "nested_subgraph", SampleGraph.withNestedSubgraph() ))
                .instance(Map.entry( "sample", SampleGraph.withConditionalEdge() ))
                .instance(Map.entry( "state_subgraph", SampleGraph.withStateSubgraph() ))
                .instance(Map.entry( "compiled_subgraph", SampleGraph.withCompiledSubgraph() ))
                .filter( ctx -> ctx.addFilter(CorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST)))
                .build()
                .start()
                .join();

    }

}
