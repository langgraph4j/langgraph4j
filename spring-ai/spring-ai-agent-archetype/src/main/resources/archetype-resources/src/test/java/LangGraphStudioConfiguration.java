#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import ${groupId}.CompileConfig;
import ${groupId}.GraphRepresentation;
import ${groupId}.GraphStateException;
import ${groupId}.StateGraph;
import ${groupId}.checkpoint.MemorySaver;
import ${groupId}.state.AgentState;
import ${groupId}.studio.LangGraphStudioServer;
import ${groupId}.studio.springboot.LangGraphStudioConfig;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

@Configuration
public class LangGraphStudioConfiguration extends LangGraphStudioConfig {

    final StateGraph<AgentExecutorEx.State> workflow;

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {

        return  Map.of( "sample", LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Spring AI)")
                .addInputStringArg( "messages", true, v -> new UserMessage( Objects.toString(v) ) )
                .graph( workflow )
                .compileConfig( CompileConfig.builder()
                        .checkpointSaver( new MemorySaver() )
                        .releaseThread(true)
                        .build())
                .build());

    }

    public LangGraphStudioConfiguration( /*@Qualifier("ollama")*/ ChatModel chatModel ) throws GraphStateException {

        this.workflow = AgentExecutorEx.builder()
                .chatModel(chatModel, true)
                .toolsFromObject(new TestTools())
                .build();

        var mermaid = workflow.getGraph( GraphRepresentation.Type.MERMAID, "ReAct Agent", false );
        System.out.println( mermaid.content() );

    }

}
