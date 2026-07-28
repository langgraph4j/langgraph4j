package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LG4JEmbedViewerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class LG4JEmbedViewerSampleConfig extends LG4JEmbedViewerConfig {

    @Bean
    StateGraph<AgentState> sampleGraph() throws GraphStateException {
        final EdgeAction<AgentState> conditionalAge  = new EdgeAction<>() {
            int steps= 0;
            @Override
            public String apply(AgentState state) {
                if( ++steps == 2 ) {
                    steps = 0;
                    return "end";
                }
                return "next";
            }
        };

        return new StateGraph<>(AgentState::new)
                .addNode("agent", node_async((state ) -> {
                    System.out.println("agent ");
                    System.out.println(state);
                    if( state.value( "action_response").isPresent() ) {
                        return Map.of("agent_summary", "This is just a DEMO summary");
                    }
                    return Map.of("agent_response", "This is an Agent DEMO response");
                }) )
                .addNode("action", node_async( state  -> {
                    System.out.print( "action: ");
                    System.out.println( state );
                    return Map.of("action_response", "This is an Action DEMO response");
                }))
                .addEdge(START, "agent")
                .addEdge("action", "agent" )
                .addConditionalEdges("agent",
                        edge_async(conditionalAge), Map.of( "next", "action", "end", END ) )
                ;
    }

    @Bean
    @Override
    protected LG4JEmbedViewerService viewerService() {

        try {
            final var graph = sampleGraph();

            return LG4JEmbedViewerService.builder()
                    .title("LangGraph4J Embed Viewer Sample")
                    .diagram(graph)
                    .build();
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }
}
