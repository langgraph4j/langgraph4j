package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;

/**
 * refer to issue <a href="https://github.com/langgraph4j/langgraph4j/issues/309">#309<a></a>
 */
public class Issue309Test {


    static class State extends AgentState {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        List<String> values() {
            return this.<List<String>>value("VALUE" ).orElseThrow();
        }
        String nextNode() {
            return this.<String>value("NEXT_NODE" ).orElse(StateGraph.END);
        }
    }

    public record LoggingNodeHook<State extends AgentState>() implements NodeHook.WrapCall<State> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

            System.out.printf("%s Executing%n", nodeId);
            return action.apply(state, config)
//                    .whenComplete( ( result, exception ) -> {
//                        System.out.printf("%s Executed%n", nodeId);
//                    })
                    ;
        }
    }

    private Map<String,Object> extracted(AsyncGenerator.Cancellable<NodeOutput<State>> iterator) {
        for( var next : iterator ) {

            if( next instanceof StreamingOutput<?> streamingOutput){
                System.out.println("Chunk : " + streamingOutput.chunk());
            }
            else {
                AgentState state = next.state();
                String nodeName = next.node();
                System.out.printf( "%s Executed%nResult State : %s%n", nodeName,state );
            }
        }

        return GraphResult.from(iterator).asStateData();
    }

    private StateGraph<State> buildStateGraph() throws GraphStateException {

        Map<String,Channel<?>> schema = Map.of( "VALUE", Channels.appender(ArrayList::new) );

        var stateSerializer = new ObjectStreamStateSerializer<>(State::new);

        return new StateGraph<>(schema, stateSerializer)
            .addWrapCallNodeHook( new LoggingNodeHook<>() )
            .addNode("NODE_A",  (state,config) ->
                completedFuture(Map.of("VALUE", "1")))
            .addNode("NODE_B", ( state, config ) ->
                completedFuture(Map.of("VALUE", "2")))
            .addNode("FEEDBACK_NODE", ( state, config ) ->
                    completedFuture(Map.of("VALUE", "3")))
            .addEdge(StateGraph.START, "NODE_A")
            .addEdge("NODE_A", "NODE_B")
            .addConditionalEdges(
                    "NODE_B",
                    state ->
                            completedFuture( state.nextNode() ),
                    new EdgeMappings.Builder()
                            .to("FEEDBACK_NODE")
                            .toEND()
                            .build()
            )
            .addEdge("FEEDBACK_NODE",StateGraph.END);
    }

    private CompiledGraph<State> compiledGraph( boolean displayGraph ) throws GraphStateException {
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var stateGraph = buildStateGraph();
        var compile = stateGraph.compile(compileConfig);

        if( displayGraph ) {
            var mermaidGraph = compile.getGraph(GraphRepresentation.Type.MERMAID);
            System.out.printf("=== Workflow Graph (MERMAID) === %n%s%n%n", mermaidGraph);
        }
        return compile;

    }

    @Test
    public void resumeAsNode() throws Exception {

        CompiledGraph<State> compiledGraph = compiledGraph( false );

        var runnableConfig = RunnableConfig.builder()
                .threadId("1")
                .build();

        System.out.println("First Time execute Done , Execute order NodeA - NodeB - END");

        var stream = compiledGraph.stream(GraphInput.noArgs(), runnableConfig);

        var result = extracted(stream);
        assertFalse( result.isEmpty() );
        assertTrue( result.containsKey("VALUE") );
        assertInstanceOf( List.class, result.get("VALUE"));
        @SuppressWarnings("unchecked")
        var values = (List<String>)result.get("VALUE");
        assertIterableEquals( List.of("1", "2"), values );

        // Resume from NODE_B
        System.out.println("Resume the same session , Set Resume from NodeB , and Set NEXT_NODE , want to route to FEEDBACK_NODE");

        var updatedConfig = compiledGraph.updateState(runnableConfig, Map.of("NEXT_NODE","FEEDBACK_NODE"), "NODE_B");

        var resumeStream = compiledGraph.stream(GraphInput.resume(), updatedConfig);
        result = extracted(resumeStream);
        assertFalse( result.isEmpty() );
        assertTrue( result.containsKey("VALUE") );
        assertInstanceOf( List.class, result.get("VALUE"));
        @SuppressWarnings("unchecked")
        var resumeValues = (List<String>)result.get("VALUE");
        assertIterableEquals( List.of("1", "2", "3"), resumeValues );

        System.out.println("Resume execute Done , Resume from Node B  Execute order NodeA - NodeB - END");


    }


}
