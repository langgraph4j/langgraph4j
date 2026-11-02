package org.bsc.langgraph4j.studio.jetty.agui;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.bsc.langgraph4j.agui.AGUISampleAgent;
import org.bsc.langgraph4j.TestTool;
import org.bsc.langgraph4j.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.agui.sdk.AGUIAgent;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioAgent;

import java.util.Objects;


public interface AGUILangGraphStudioApplication {

    static AGUIAgent agentExecutor( String id ) throws Exception {
        final var llm = OllamaChatModel.builder()
                .baseUrl( "http://localhost:11434" )
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("qwen2.5:7b")
                .build();

        final var agent = AgentExecutorEx.builder()
                .chatModel(llm)
                .toolsFromObject( new TestTool() )
                .stateSerializer( AgentExecutorEx.Serializers.JSON.object() )
                .build();

        return AGUILangGraphStudioAgent.builder()
                .id(id)
                .title("AGENT EXECUTOR (OLLAMA)")
                .stateGraph(agent)
                .addInputStringArg("messages", true, v -> SystemMessage.from(Objects.toString(v)))
                .build();

    }

    static void main(String[] args) throws Exception {

        AGUILangGraphStudioServer4Jetty.builder()
                .port(8081)
                .agent(agentExecutor("agent_executor"))
                .agent(AGUISampleAgent.baseAgent("base-agent"))
                .agent(AGUISampleAgent.agentWithInterruption("agent-with-interruption"))
                .build()
                .start()
                .join();

    }

}
