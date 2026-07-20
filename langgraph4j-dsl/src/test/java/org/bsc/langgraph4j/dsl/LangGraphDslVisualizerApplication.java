package org.bsc.langgraph4j.dsl;

import org.bsc.langgraph4j.GraphDefinition;
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

    private static final GraphDefinition.Reducer<AgentState,String> jsonDslGenerator = new JsonDslGenerator<>();

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
            return  sampleGraphDslService.nestedSubgraphs();
        }
    }

    @Service
    static class SampleGraphDslService {

        String agentExecutor() throws GraphStateException {
            AsyncNodeAction<AgentState> action = state -> CompletableFuture.completedFuture(Map.of());

            return new StateGraph<>(AgentState::new)
                    .addNode("model", action)
                    .addNode("tools", action)
                    .addEdge(START, "model")
                    .addConditionalEdges(
                            "model",
                            state -> CompletableFuture.completedFuture(""),
                            EdgeMappings.builder()
                                    .to("tools")
                                    .toEND()
                                    .build())
                    .addEdge("tools", "model")
                    .compile()
                    .reduce( jsonDslGenerator );
        }

        String graphWithSubgraph() throws GraphStateException {
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
                    .reduce( jsonDslGenerator );
        }

        String nestedSubgraphs() throws GraphStateException {
            var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

            var subSubGraph = new StateGraph<>(AgentState::new)
                    .addNode("foo1", mockedAction)
                    .addNode("foo2", mockedAction)
                    .addNode("foo3", mockedAction)
                    .addEdge(StateGraph.START, "foo1")
                    .addEdge("foo1", "foo2")
                    .addEdge("foo2", "foo3")
                    .addEdge("foo3", StateGraph.END)
                    .compile()
                    ;

            var subGraph = new StateGraph<>(AgentState::new)
                    .addNode("bar1", mockedAction)
                    .addNode("subGraph2", subSubGraph)
                    .addNode("bar2", mockedAction)
                    .addEdge(StateGraph.START, "bar1")
                    .addEdge("bar1", "subGraph2")
                    .addEdge("subGraph2", "bar2")
                    .addEdge("bar2", StateGraph.END)
                    .compile()
                    ;

            return new StateGraph<>(AgentState::new)
                    .addNode("main1", mockedAction)
                    .addNode("subgraph1", subGraph)
                    .addNode("main2", mockedAction)
                    .addEdge(StateGraph.START, "main1")
                    .addEdge("main1", "subgraph1")
                    .addEdge("subgraph1", "main2")
                    .addEdge("main2", StateGraph.END)
                    .compile()
                    .reduce( jsonDslGenerator )
                    ;

        }
    }
}
