package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public interface SampleGraph {

    static LangGraphStudioServer.Instance issue216() throws GraphStateException {

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

        var stateGraph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                ;

        return LangGraphStudioServer.Instance.builder()
                .title("Issue 206")
                .addInputStringArg("messages", false)
                .graph(stateGraph)
                .build();

    }

    static LangGraphStudioServer.Instance baseAgent() throws GraphStateException {
        AsyncNodeAction<AgentState> action = state -> completedFuture(Map.of());

        final var graph =  new StateGraph<>(AgentState::new)
                .addNode("model", action)
                .addNode("tools", action)
                .addEdge(START, "model")
                .addConditionalEdges(
                        "model",
                        state -> completedFuture(END),
                        EdgeMappings.builder()
                                .to("tools")
                                .toEND()
                                .build())
                .addEdge("tools", "model");
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Base Agent)")
                .graph(graph)
                .addInputStringArg("input")
                .build();

    }

    static LangGraphStudioServer.Instance withInterruption() throws GraphStateException {
        class State extends MessagesState<String> {

            public State(Map<String, Object> initData) {
                super(initData);
            }
        }

        AsyncNodeActionWithConfig<State> action = (state,config) ->
                completedFuture(Map.of("messages",config.nodeId()));

        var graph = new StateGraph<>( State.SCHEMA, State::new)
                .addNode("A", action)
                .addNode("B", action)
                .addNode("C", action)
                .addConditionalEdges("B",
                        edge_async(state ->
                                state.lastMessage().orElse(END)),
                        EdgeMappings.builder()
                                .to("A")
                                .to("C")
                                .toEND()
                                .build())
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge("C", END);

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .interruptAfter("B")
                .interruptBeforeEdge(true)
                .build();

        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Interruption Demo)")
                .compileConfig(compileConfig)
                .graph(graph)
                .build();

    }

    static LangGraphStudioServer.Instance withSubgraph() throws GraphStateException {
        AsyncNodeAction<AgentState> action = state ->
                completedFuture(Map.of());

        var toolSubgraph = new StateGraph<>(AgentState::new)
                .addNode("call_tool", action)
                .addNode("format_result", action)
                .addEdge(START, "call_tool")
                .addEdge("call_tool", "format_result")
                .addEdge("format_result", END)
                .compile();

        final var graph = new StateGraph<>(AgentState::new)
                .addNode("planner", action)
                .addNode("tool_executor", toolSubgraph)
                .addNode("responder", action)
                .addEdge(START, "planner")
                .addConditionalEdges(
                        "planner",
                        state -> completedFuture("tool"),
                        EdgeMappings.builder()
                                .to("tool_executor", "tool")
                                .to("responder", "answer")
                                .build())
                .addEdge("tool_executor", "responder")
                .addEdge("responder", END);
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Subgraph Demo)")
                .graph(graph)
                .build();

    }

    static LangGraphStudioServer.Instance withNestedSubgraphs() throws GraphStateException {
        var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

        var subSubGraph = new StateGraph<>(AgentState::new)
                .addNode("foo1", mockedAction)
                .addNode("foo2", mockedAction)
                .addNode("foo3", mockedAction)
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile();

        var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", mockedAction)
                .addNode("subGraph2", subSubGraph)
                .addNode("bar2", mockedAction)
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subGraph2")
                .addEdge("subGraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile();

        final var graph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Nested Subgraph Demo)")
                .graph(graph)
                .build();

    }

    static LangGraphStudioServer.Instance withConditionalEdge() throws GraphStateException {
        final EdgeAction<AgentState> conditionalAge = new EdgeAction<>() {
            int steps = 0;

            @Override
            public String apply(AgentState state) {
                if (++steps == 2) {
                    steps = 0;
                    return "end";
                }
                return "next";
            }
        };

        final var graph = new StateGraph<>(AgentState::new)
                .addNode("agent", node_async((state) -> {
                    System.out.println("agent ");
                    System.out.println(state);
                    if (state.value("action_response").isPresent()) {
                        return Map.of("agent_summary", "This is just a DEMO summary");
                    }
                    return Map.of("agent_response", "This is an Agent DEMO response");
                }))
                .addNode("action", node_async(state -> {
                    System.out.print("action: ");
                    System.out.println(state);
                    return Map.of("action_response", "This is an Action DEMO response");
                }))
                .addEdge(START, "agent")
                .addEdge("action", "agent")
                .addConditionalEdges("agent",
                        edge_async(conditionalAge), Map.of("next", "action", "end", END))
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Sample)")
                .graph( graph )
                .build();
    }

    static LangGraphStudioServer.Instance issue241() throws GraphStateException {

        final Function<String, AsyncNodeAction<MessagesState<String>>> _makeNode = (String id) ->
                node_async(state ->
                        Map.of("messages", id));


       final var graph = new StateGraph<MessagesState<String>>(MessagesState::new)
                .addNode("claudeNode", _makeNode.apply("claudeNode"))
                .addEdge(START, "claudeNode")
                .addEdge("claudeNode", END)
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Issue241)")
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver( new MemorySaver() )
                        .interruptBefore("claudeNode")
                        .build())
                .graph(graph)
                .addInputStringArg( "input")
                .build();
    }

    static LangGraphStudioServer.Instance withStateSubgraph() throws GraphStateException {

        final Function<String, AsyncNodeAction<MessagesState<String>>> _makeNode = (String id) ->
                node_async(state ->
                        Map.of("messages", id));

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode.apply("B1"))
                .addNode("B2", _makeNode.apply("B2"))
                .addNode("C", _makeNode.apply("subgraph(C)"))
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges("B2",
                        edge_async(state -> "c"),
                        Map.of(END, END, "c", "C"))
                .addEdge("C", END);

        final var graph = new MessagesStateGraph<String>()
                .addNode("A", _makeNode.apply("A"))
                .addNode("B", workflowChild)
                .addNode("C", _makeNode.apply("C"))
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of("a", "A", "b", "B"))
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Merged Subgraph)")
                .graph( graph )
                .build();
    }

    static LangGraphStudioServer.Instance withCompiledSubgraph() throws GraphStateException {

        final Function<String, AsyncNodeAction<MessagesState<String>>> _makeNode = (String id) ->
                node_async(state ->
                        Map.of("messages", id));

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode.apply("B1"))
                .addNode("B2", _makeNode.apply("B2"))
                .addNode("C", _makeNode.apply("subgraph(C)"))
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges("B2",
                        edge_async(state -> "c"),
                        Map.of(END, END, "c", "C"))
                .addEdge("C", END)
                .compile();

        final var graph = new MessagesStateGraph<String>()
                .addNode("A", _makeNode.apply("A"))
                .addNode("B", workflowChild)
                .addNode("C", _makeNode.apply("C"))
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of("a", "A", "b", "B"))
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Compiled Subgraph)")
                .graph( graph )
                .build();

    }

    static LangGraphStudioServer.Instance withNestedSubgraph() throws GraphStateException {
        var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

        var subSubGraph = new StateGraph<>(AgentState::new)
                .addNode("foo1", mockedAction)
                .addNode("foo2", mockedAction)
                .addNode("foo3", mockedAction)
                .addEdge(StateGraph.START, "foo1")
                .addEdge("foo1", "foo2")
                .addEdge("foo2", "foo3")
                .addEdge("foo3", StateGraph.END)
                .compile();

        var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", mockedAction)
                .addNode("subGraph2", subSubGraph)
                .addNode("bar2", mockedAction)
                .addEdge(StateGraph.START, "bar1")
                .addEdge("bar1", "subGraph2")
                .addEdge("subGraph2", "bar2")
                .addEdge("bar2", StateGraph.END)
                .compile();

        final var graph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(StateGraph.START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", StateGraph.END)
                ;
        return LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio (Nested Subgraph)")
                .graph( graph )
                .build();
    }

}