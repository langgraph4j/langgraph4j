package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractAgentExecutorTest {

    protected abstract  StateGraph<AgentExecutor.State> newGraph( AgentExecutor.Serializers serializer )  throws Exception ;

    protected StateGraph<AgentExecutor.State> newGraphWithStreaming( AgentExecutor.Serializers serializer, boolean emitStreamingOutputEnd )  throws Exception {
        throw new UnsupportedOperationException();
    }

    private List<NodeOutput<AgentExecutor.State>> executeAgent( String prompt,
                                                                AgentExecutor.Serializers serializer,
                                                                CompileConfig compileConfig,
                                                                RunnableConfig runnableConfig )  throws Exception
    {

        final var graph = newGraph(serializer).compile( compileConfig );

        final var iterator = graph.stream(
                GraphInput.args(Map.of( "messages", UserMessage.from(prompt))),
                runnableConfig );

        return iterator.stream().toList();
    }

    private List<NodeOutput<AgentExecutor.State>> executeAgentWithStreaming( String prompt,
                                                                             AgentExecutor.Serializers serializer,
                                                                             CompileConfig compileConfig,
                                                                             RunnableConfig runnableConfig ,
                                                                             boolean emitStreamingOutputEnd )  throws Exception
    {

        final var graph = newGraphWithStreaming(serializer, emitStreamingOutputEnd).compile( compileConfig );

        final var iterator = graph.stream(
                    GraphInput.args(Map.of( "messages", UserMessage.from(prompt))),
                    runnableConfig );

        return iterator.stream()
                .filter( output -> {
                    if( output instanceof StreamingOutput<AgentExecutor.State> s ) {
                        System.out.printf( "[%s] chunk=%s%n", s.isStreamingEnd() ? "END" : "NEXT", s.chunk());
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    enum newGraphEnum {
        GRAPH,
        GRAPH_WITH_STREAMING,
        GRAPH_WITH_STREAMING_AND_EMIT_OUTPUT_END
        ;
    }

    @ParameterizedTest
    @EnumSource(newGraphEnum.class)
    void executeAgentWithSingleToolInvocation( newGraphEnum type ) throws Exception {

        final var cConfig = CompileConfig.builder().build();
        final var rConfig = RunnableConfig.empty();

        final var prompt = "what is the result of test with messages: 'MY FIRST TEST'";

        final var serializer = AgentExecutor.Serializers.JSON;

        final var steps = switch (type) {
            case GRAPH ->
                    executeAgent( prompt, serializer, cConfig, rConfig);
            case GRAPH_WITH_STREAMING ->
                    executeAgentWithStreaming( prompt, serializer, cConfig, rConfig, false );
            case GRAPH_WITH_STREAMING_AND_EMIT_OUTPUT_END ->
                    executeAgentWithStreaming( prompt, serializer, cConfig, rConfig, true );
        };

        assertEquals( 6, steps.size() );
        final var state = lastOf(steps).map( NodeOutput::state ).orElse(null);
        assertNotNull(state);
        assertTrue(state.finalResponse().isPresent());
        System.out.println(state.finalResponse().get());

    }

    @ParameterizedTest
    @EnumSource(newGraphEnum.class)
    void executeAgentWithDoubleToolInvocation( newGraphEnum type ) throws Exception {
        final var cConfig = CompileConfig.builder().build();
        final var rConfig = RunnableConfig.empty();

        final var prompt = "what is the result of test with messages: 'MY FIRST TEST' and the result of test with message: 'MY SECOND TEST'";

        final var serializer = AgentExecutor.Serializers.JSON;

        final var steps = switch (type) {
            case GRAPH ->
                    executeAgent( prompt, serializer, cConfig, rConfig);
            case GRAPH_WITH_STREAMING ->
                    executeAgentWithStreaming( prompt, serializer, cConfig, rConfig, false );
            case GRAPH_WITH_STREAMING_AND_EMIT_OUTPUT_END ->
                    executeAgentWithStreaming( prompt, serializer, cConfig, rConfig, true );
        };

        assertEquals( 6, steps.size() );
        final var state = lastOf(steps).map( NodeOutput::state ).orElse(null);
        assertNotNull(state);
        assertTrue(state.finalResponse().isPresent());
        System.out.println(state.finalResponse().get());
    }

    @Test
    void executeAgentWithDoubleToolInvocationWithCheckpoint() throws Exception {

        final var saver = new MemorySaver();

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver( saver )
                .releaseThread(false)
                .build();

        final var config = RunnableConfig.builder().
                        threadId("thread_1")
                        .build();

        final var graph = newGraph(AgentExecutor.Serializers.JSON).compile( compileConfig );

        try {
            var iterator = graph.stream(
                    GraphInput.args(Map.of("messages",
                            UserMessage.from("what is the result of test with messages: 'MY FIRST TEST' and the result of test with message: 'MY SECOND TEST'"))),
                    config);

            var states = iterator.stream()
                    .peek(s -> System.out.println(s.node()))
                    .map(NodeOutput::state)
                    .toList();

            assertEquals(6, states.size()); // iterations
            var state = lastOf(states).orElse(null);
            assertNotNull(state);
            assertTrue(state.lastMessage().isPresent());
            System.out.printf("final response: %s\n", state.lastMessage().get());

            //var stateHistory = graph.lastStateOf( config ).orElseThrow();

            iterator = graph.stream(
                    GraphInput.args(Map.of("messages",
                            UserMessage.from(
                                    "what are the results of tests?"))),
                    config);

            states = iterator.stream()
                    .peek(s -> System.out.println(s.node()))
                    .map(NodeOutput::state)
                    .toList();

            assertEquals(4, states.size()); // iterations
            state = lastOf(states).orElse(null);
            assertNotNull(state);
            assertTrue(state.lastMessage().isPresent());
            System.out.printf("final response: %s\n", state.lastMessage().get());

        }
        finally {
            saver.release(config);
        }
    }

    @Test
    public void getGraphTest() throws Exception {

        final var app = new StateGraph<>(AgentState::new)
            .addEdge(START,"agent")
            .addNode( "agent", node_async( state -> Map.of() ))
            .addNode( "action", node_async( state -> Map.of() ))
            .addConditionalEdges(
                    "agent",
                    edge_async(state -> ""),
                    Map.of("continue", "action", "end", END)
            )
            .addEdge("action", "agent")
            .compile();

        var printConditionalEdge = false;

        var plantUml = app.getGraph( GraphRepresentation.Type.PLANTUML, "Agent Executor", printConditionalEdge );

        System.out.println( plantUml.content() );

        var mermaid = app.getGraph( GraphRepresentation.Type.MERMAID, "Agent Executor", printConditionalEdge );

        System.out.println( mermaid.content() );
    }
}
