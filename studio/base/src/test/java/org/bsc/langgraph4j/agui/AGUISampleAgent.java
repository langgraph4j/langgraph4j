package org.bsc.langgraph4j.agui;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agui.sdk.AGUIHook;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioAgent;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

public interface AGUISampleAgent {

    static AGUILangGraphStudioAgent baseAgent(String id) throws GraphStateException {
        AsyncNodeAction<AgentState> action = state -> completedFuture(Map.of());

        final var graph =  new StateGraph<>(AgentState::new)
                .addWrapCallNodeHook(AGUIHook.stepEvents())
                .addNode("model", action)
                .addNode("tools", action)
                .addEdge(START, "model")
                .addConditionalEdges(
                        "model",
                        (state,config) -> {
                            if( state.<Boolean>value("tool_invoked").orElse(false) )
                                return completedFuture( new Command(END));
                            return completedFuture(new Command("tools", Map.of("tool_invoked", true)));
                        },
                        EdgeMappings.builder()
                                .to("tools")
                                .toEND()
                                .build())
                .addEdge("tools", "model");
        return AGUILangGraphStudioAgent.builder()
                .id(id)
                .title("LangGraph Studio (Base Agent)")
                .stateGraph(graph)
                .addInputStringArg("input")
                .build();

    }

}
