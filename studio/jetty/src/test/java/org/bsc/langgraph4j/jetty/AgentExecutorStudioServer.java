package org.bsc.langgraph4j.jetty;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.servlet.DispatcherType;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.TestTool;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.jetty.LangGraphStudioServer4Jetty;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

public interface AgentExecutorStudioServer {

    static Map.Entry<String, LangGraphStudioServer.Instance> agentExecutor() throws Exception {
        var llm = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("qwen2.5:7b")
                .build();

        var app = AgentExecutor.builder()
                .chatModel(llm)
                .toolsFromObject( new TestTool() )
                .stateSerializer( AgentExecutor.Serializers.JSON.object() )
                .build();

        return Map.entry( "agent_executor", LangGraphStudioServer.Instance.builder()
                .title("AGENT EXECUTOR")
                .addInputStringArg("messages", true, v -> SystemMessage.from(Objects.toString(v)))
                .graph(app)
                .build());

    }

    static Map.Entry<String, LangGraphStudioServer.Instance> issue216() throws Exception {

        var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

        var subSubGraph = new StateGraph<>(AgentState::new)
                .addNode("foo1", mockedAction)
                .addNode("foo2", mockedAction)
                .addNode("foo3", mockedAction)
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile()
                ;

        var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", mockedAction)
                .addNode("subGraph2", subSubGraph)
                .addNode("bar2", mockedAction)
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subGraph2")
                .addEdge("subGraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile()
                ;

        var stateGraph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                ;

        return Map.entry( "issue216", LangGraphStudioServer.Instance.builder()
                .title("Issue 206")
                .addInputStringArg("messages", false)
                .graph(stateGraph)
                .build());

    }

    static void main(String[] args) throws Exception {

        LangGraphStudioServer4Jetty.builder()
                .port(8080)
                .instance(agentExecutor())
                .instance(issue216())
                .filter( ctx -> ctx.addFilter(CorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST)))
                .build()
                .start()
                .join();

    }

}
