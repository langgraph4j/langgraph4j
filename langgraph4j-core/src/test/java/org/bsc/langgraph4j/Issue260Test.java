package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.Map;


import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test for issue <a href="https://github.com/langgraph4j/langgraph4j/issues/260">#260</a>
 */
public class Issue260Test {

    public static class GraphState extends AgentState {
        static final String MY_VALUE = "my_value";

        public GraphState(Map<String, Object> initData) {
            super(initData);
        }

        public static Map<String, Channel<?>> getChannels() {
            return Map.of(
                    MY_VALUE, Channels.base((oldVal, newVal) -> newVal)
            );
        }

        public <T> java.util.Optional<T> myValue() {
            return value(MY_VALUE);
        }
    }

    private static CompiledGraph<GraphState> getCompiledGraph() throws GraphStateException
    {

        AsyncNodeAction<GraphState> subgraphNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [SUBGRAPH] Before removal - my_value: " + state.myValue().orElse("NOT_PRESENT"));
            System.out.println("  [SUBGRAPH] Returning MARK_FOR_REMOVAL for my_value");
            return Map.of(GraphState.MY_VALUE, AgentState.MARK_FOR_REMOVAL);
        });

        // SUBGRAPH: Will try to remove my_value using MARK_FOR_REMOVAL
        var subGraph = new StateGraph<>(GraphState.getChannels(), GraphState::new)
            .addNode("remove_value", subgraphNode)
            .addEdge(StateGraph.START, "remove_value")
            .addEdge("remove_value", StateGraph.END);

        AsyncNodeAction<GraphState> setValueNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [PARENT] Setting my_value to 'parent_value'");
            return Map.of(GraphState.MY_VALUE, "parent_value");
        });

        AsyncNodeAction<GraphState> checkValueNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [PARENT] After subgraph - checking my_value...");
            if (state.myValue().isPresent())
            {
                System.out.println("  [PARENT] ❌ BUG: my_value still exists with value: " + state.myValue().get());
            }
            else
            {
                System.out.println("  [PARENT] ✅ SUCCESS: my_value was removed");
            }
            return Map.of();
        });

        // PARENT GRAPH
        var parentGraph = new StateGraph<>(GraphState.getChannels(), GraphState::new)
            .addNode("set_value", setValueNode)
            .addNode("subgraph", subGraph.compile())
            .addNode("check_value", checkValueNode)
            .addEdge(StateGraph.START, "set_value")
            .addEdge("set_value", "subgraph")
            .addEdge("subgraph", "check_value")
            .addEdge("check_value", StateGraph.END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .build();

        return parentGraph.compile(compileConfig);
    }

    @Test
    public void removalPropagatesFromSubgraphTest() throws Exception
    {
        var graph = getCompiledGraph();
        var runConfig = RunnableConfig.builder()
                .threadId("removal-test")
                .streamMode(CompiledGraph.StreamMode.VALUES)
                .build();

        // Start with my_value set
        var input = new GraphArgs(Map.of(GraphState.MY_VALUE, "initial_value"));

        var output = graph.invokeFinal(input, runConfig).orElseThrow();

        var state = output.state();

        assertTrue( state.myValue().isEmpty(), "Expected: my_value should be NOT_PRESENT after subgraph");
    }

    private static CompiledGraph<GraphState> getFixedCompiledGraph() throws GraphStateException
    {

        AsyncNodeAction<GraphState> subgraphNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [SUBGRAPH] Before removal - my_value: " + state.myValue().orElse("NOT_PRESENT"));
            System.out.println("  [SUBGRAPH] Returning MARK_FOR_REMOVAL for my_value");
            //return Map.of(GraphState.MY_VALUE, AgentState.MARK_FOR_REMOVAL);
            return Map.of();
        });

        // SUBGRAPH: Will try to remove my_value using MARK_FOR_REMOVAL
        var subGraph = new StateGraph<>(GraphState.getChannels(), GraphState::new)
                .addNode("remove_value", subgraphNode)
                .addEdge(StateGraph.START, "remove_value")
                .addEdge("remove_value", StateGraph.END);

        AsyncNodeAction<GraphState> setValueNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [PARENT] Setting my_value to 'parent_value'");
            return Map.of(GraphState.MY_VALUE, "parent_value");
        });

        AsyncNodeAction<GraphState> checkValueNode = AsyncNodeAction.node_async((state) -> {
            System.out.println("  [PARENT] After subgraph - checking my_value...");
            if (state.myValue().isPresent())
            {
                System.out.println("  [PARENT] ❌ BUG: my_value still exists with value: " + state.myValue().get());
            }
            else
            {
                System.out.println("  [PARENT] ✅ SUCCESS: my_value was removed");
            }
            return Map.of();
        });

        AsyncCommandAction<GraphState> exitFromSubGraphEdge = AsyncCommandAction.command_async(( state, config ) ->
            new Command( "check_value", Map.of(GraphState.MY_VALUE, AgentState.MARK_FOR_REMOVAL)));

        // PARENT GRAPH
        var parentGraph = new StateGraph<>(GraphState.getChannels(), GraphState::new)
                .addNode("set_value", setValueNode)
                .addNode("subgraph", subGraph.compile())
                .addNode("check_value", checkValueNode)
                .addEdge(StateGraph.START, "set_value")
                .addEdge("set_value", "subgraph")
                .addConditionalEdges("subgraph",
                        exitFromSubGraphEdge,
                        EdgeMappings.builder()
                                .to("check_value")
                                .build())
                .addEdge("check_value", StateGraph.END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .build();

        return parentGraph.compile(compileConfig);
    }

    @Test
    public void fixErrorTest() throws Exception
    {
        var graph = getFixedCompiledGraph();
        var runConfig = RunnableConfig.builder()
                .threadId("removal-test")
                .streamMode(CompiledGraph.StreamMode.VALUES)
                .build();

        // Start with my_value set
        var input = new GraphArgs(Map.of(GraphState.MY_VALUE, "initial_value"));

        var output = graph.invokeFinal(input, runConfig).orElseThrow();

        var state = output.state();

        assertTrue( state.myValue().isEmpty(), "Expected: my_value should be NOT_PRESENT after subgraph");
    }

}
