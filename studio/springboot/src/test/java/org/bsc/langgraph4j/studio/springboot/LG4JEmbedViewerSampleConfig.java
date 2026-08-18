package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.studio.LG4JEmbedViewerService;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LG4JEmbedViewerSampleConfig extends LG4JEmbedViewerConfig {

    @Bean
    public LangGraphStudioServer.Instance graphInstance() throws GraphStateException {
//        return SampleGraph.withSubgraph();
        return SampleGraph.withInterruption();
    }

    @Bean
    @Override
    protected LG4JEmbedViewerService viewerService() throws Exception {
            return LG4JEmbedViewerService.builder()
                    .id("lg4j-embed-viewer-sample")
                    .title(graphInstance().title())
                    .diagram(graphInstance().graph())
                    .build();
    }
}
