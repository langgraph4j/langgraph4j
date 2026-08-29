package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.SampleGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.SampleGraph.issue216;
import static org.bsc.langgraph4j.SampleGraph.issue241;

@Configuration
public class LangGraphStudioSampleConfig extends LangGraphStudioConfig implements LG4JLoggable {

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        try {
            return Map.ofEntries(
                    Map.entry( "issue241", issue241() ),
                    Map.entry( "issue216", issue216() ),
                    Map.entry( "nested_subgraph", SampleGraph.withNestedSubgraph() ),
                    Map.entry( "sample", SampleGraph.withConditionalEdge() ),
                    Map.entry( "state_subgraph", SampleGraph.withStateSubgraph() ),
                    Map.entry( "compiled_subgraph", SampleGraph.withCompiledSubgraph() ) );
        } catch (GraphStateException e) {
            log.error(e.getMessage(), e);
            return Map.of();
        }
    }


}