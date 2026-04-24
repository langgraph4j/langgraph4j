package org.bsc.langgraph4j.otel;

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OTELObservationLangraph4jITest implements OTELObservable {

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }

    }

    @BeforeAll
    public static void initialize() throws Exception{

        var props = new java.util.Properties();
        try (var input = OTELObservationLangraph4jITest.class.getResourceAsStream("/otel-config.properties")) {

            assertNotNull(input, "/otel-config.properties not found in classpath");

            props.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        System.getProperties().putAll(props);

        var autoConfig = AutoConfiguredOpenTelemetrySdk.initialize();

        var otel = autoConfig.getOpenTelemetrySdk();

        OpenTelemetryAppender.install(otel);

    }

    @Test
    public void simpleWorkflow() throws GraphStateException {

        AsyncNodeActionWithConfig<State> simpleAction = ( state, config ) ->
            CompletableFuture.completedFuture(
                    Map.of( "messages", "%s-%d".formatted(config.nodeId(), System.currentTimeMillis()) ) );

        AsyncCommandAction<State> simpleEdgeAction = ( state, config ) ->
            CompletableFuture.completedFuture(
                ( state.messages().size() > 4) ?
                        new Command(END) :
                        new Command( "node_1" ));

        var stateSerializer = new ObjectStreamStateSerializer<>(State::new);

        var otelSetParentHook = OTELWrapCallTraceSetParentHook.<State>builder()
                .scope( "SimpleWorkflow" )
                .groupName( "stream" )
                .build();

        var otelHook = new OTELWrapCallTraceHook<State>(stateSerializer);

        var workflow = new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addWrapCallNodeHook( otelHook )
                .addWrapCallEdgeHook( otelHook )
                .addWrapCallNodeHook( otelSetParentHook )
                .addWrapCallEdgeHook( otelSetParentHook )
                .addNode("node_1", simpleAction )
                .addNode("node_2", simpleAction )
                .addNode("node_3", simpleAction )
                .addNode("node_4", simpleAction )
                .addEdge(START, "node_1")
                .addEdge("node_1", "node_2")
                .addEdge("node_2", "node_3")
                .addEdge("node_3", "node_4")
                .addConditionalEdges( "node_4", simpleEdgeAction,
                        EdgeMappings.builder()
                                .to("node_1")
                                .toEND()
                                .build())
                .compile();

        var result = workflow.invoke( GraphInput.noArgs(), RunnableConfig.empty());

        assertTrue( result.isPresent() );
    }

}
