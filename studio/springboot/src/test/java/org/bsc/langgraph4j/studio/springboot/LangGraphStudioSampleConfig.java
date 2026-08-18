package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.SampleGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class LangGraphStudioSampleConfig extends LangGraphStudioConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LangGraphStudioSampleConfig.class);

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        try {
            return Map.ofEntries(
                    nestedSubgraph(),
                    issue241(),
                    sampleFlow(),
                    withStateSubgraphSample(),
                    withCompiledSubgraphSample() );
        } catch (GraphStateException e) {
            log.error(e.getMessage(), e);
            return Map.of();
        }
    }

    private Map.Entry<String, LangGraphStudioServer.Instance> issue241() throws GraphStateException {

        return  Map.entry( "issue241", LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Issue241)")
                 .compileConfig(CompileConfig.builder()
                         .releaseThread(true)
                         .checkpointSaver( new MemorySaver() )
                         .interruptBefore("claudeNode")
                         .build())
                .graph(SampleGraph.issue241().graph())
                .addInputStringArg( "input")
                .build());

    }

    private Map.Entry<String, LangGraphStudioServer.Instance> sampleFlow() throws GraphStateException {

        return  Map.entry( "sample", LangGraphStudioServer.Instance.builder()
                                        .title("LangGraph Studio (Sample)")
                                        .graph( SampleGraph.withConditionalEdge().graph() )
                                        .build());

    }

    private Map.Entry<String, LangGraphStudioServer.Instance> withStateSubgraphSample() throws GraphStateException {


        return   Map.entry( "state_subgraph", LangGraphStudioServer.Instance.builder()
                                        .title("LangGraph Studio (Merged Subgraph)")
                                        .graph( SampleGraph.withStateSubgraph().graph() )
                                        .build());

    }

    private Map.Entry<String, LangGraphStudioServer.Instance> withCompiledSubgraphSample() throws GraphStateException {
        return  Map.entry( "compiled_subgraph", LangGraphStudioServer.Instance.builder()
                                        .title("LangGraph Studio (Compiled Subgraph)")
                                        .graph( SampleGraph.withCompiledSubgraph().graph() )
                                        .build());
    }

    public Map.Entry<String, LangGraphStudioServer.Instance> nestedSubgraph() throws GraphStateException {

        return  Map.entry( "nested_subgraph", LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Nested Subgraph)")
                .graph( SampleGraph.withNestedSubgraph().graph() )
                .build());

    }


}
