package org.bsc.langgraph4j.studio.springboot;

import jakarta.annotation.PostConstruct;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LG4JEmbedViewerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LG4jEmbedViewerApplication {

    final StateGraph<AgentState> graph;
    final LG4JEmbedViewerService viewerService;
    public LG4jEmbedViewerApplication(StateGraph<AgentState> graph, LG4JEmbedViewerService viewerService) {
        this.graph = graph;
        this.viewerService = viewerService;
    }

    @PostConstruct
    public void run() throws GraphStateException {

        final var agent = graph.compile();

        agent.streamSnapshots( GraphInput.noArgs(), RunnableConfig.empty())
                .forEachAsync( output -> {

                    viewerService.processor.dispatchAsync( AsyncGenerator.Data.of(output));
                    if(output.isEND() ) {
                        viewerService.processor.dispatchAsync( AsyncGenerator.Data.done(output) );
                    }
                });
    }

    public static void main(String[] args) {

        SpringApplication.run(LG4jEmbedViewerApplication.class, args);
    }

}
