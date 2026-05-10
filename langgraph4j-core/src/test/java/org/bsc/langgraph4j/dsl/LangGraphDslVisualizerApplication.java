package org.bsc.langgraph4j.dsl;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@SpringBootApplication
public class LangGraphDslVisualizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LangGraphDslVisualizerApplication.class, args);
    }

    @RestController
    static class DslVisualizerController {

        private final SampleGraphDslService sampleGraphDslService;

        DslVisualizerController(SampleGraphDslService sampleGraphDslService) {
            this.sampleGraphDslService = sampleGraphDslService;
        }

        @GetMapping(value = "/api/graph", produces = MediaType.APPLICATION_JSON_VALUE)
        String graph() throws GraphStateException {
            return sampleGraphDslService.sampleGraphJson();
        }
    }

    @Service
    static class SampleGraphDslService {

        String sampleGraphJson() throws GraphStateException {
            AsyncNodeAction<AgentState> action = state -> CompletableFuture.completedFuture(Map.of());

            var toolSubgraph = new StateGraph<>(AgentState::new)
                    .addNode("call_tool", action)
                    .addNode("format_result", action)
                    .addEdge(START, "call_tool")
                    .addEdge("call_tool", "format_result")
                    .addEdge("format_result", END)
                    .compile();

            return new StateGraph<>(AgentState::new)
                    .addNode("planner", action)
                    .addNode("tool_executor", toolSubgraph)
                    .addNode("responder", action)
                    .addEdge(START, "planner")
                    .addConditionalEdges(
                            "planner",
                            state -> CompletableFuture.completedFuture(""),
                            EdgeMappings.builder()
                                    .to("tool_executor", "tool")
                                    .to("responder", "answer")
                                    .build())
                    .addEdge("tool_executor", "responder")
                    .addEdge("responder", END)
                    .compile()
                    .toJSON();
        }
    }
}
