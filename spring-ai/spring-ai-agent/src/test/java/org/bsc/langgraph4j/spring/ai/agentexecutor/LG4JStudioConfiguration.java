package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

@Configuration
public class LG4JStudioConfiguration extends LangGraphStudioConfig {

    final Map<String, LangGraphStudioServer.Instance> instanceMap;

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return instanceMap;
    }

    public LG4JStudioConfiguration( /*@Qualifier("ollama")*/ ChatModel chatModel ) throws GraphStateException {

        final var tools = new TestTools();

        final var workflow1 = AgentExecutorEx.builder()
                .chatModel(chatModel)
                .streaming(true)
                .approvalOn( "threadCount", (nodeId, state) ->
                        InterruptionMetadata.builder( nodeId, state ).build())
                .toolsFromObject(tools)
                .build();

        final var workflow2 = AgentExecutor.builder()
                .chatModel(chatModel)
                .streaming(true)
                .toolsFromObject(tools)
                .build();


        instanceMap = Map.of(
                "agent_executor_ex", LangGraphStudioServer.Instance.builder()
                        .title("LangGraph Studio (Agent Executor Extension)")
                        .addInputStringArg( "messages", true, v -> new UserMessage( Objects.toString(v) ) )
                        .graph( workflow1 )
                        .compileConfig( CompileConfig.builder()
                                .checkpointSaver( new MemorySaver() )
                                .build())
                        .build(),
                "agent_executor", LangGraphStudioServer.Instance.builder()
                        .title("LangGraph Studio (Agent Executor)")
                        .addInputStringArg( "messages", true, v -> new UserMessage( Objects.toString(v) ) )
                        .graph( workflow2 )
                        .compileConfig( CompileConfig.builder()
                                .checkpointSaver( new MemorySaver() )
                                .build())
                        .build()
        );


    }

}
