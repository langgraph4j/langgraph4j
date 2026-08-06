package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.SampleGraph;
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
    public StateGraph<? extends AgentState> graph() throws GraphStateException {
        return SampleGraph.withSubgraph();
    }

    @Bean
    @Override
    protected LG4JEmbedViewerService viewerService() throws Exception {
            return LG4JEmbedViewerService.builder()
                    .id("lg4j-embed-viewer-sample")
                    .title("LangGraph4J Embed Viewer Sample")
                    .diagram(graph())
                    .build();
    }
}
