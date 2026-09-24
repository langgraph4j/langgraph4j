package org.bsc.langgraph4j.otel;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * Wraps LangGraph4j node and edge execution with OpenTelemetry spans.
 * <p>
 * This hook creates spans for node and edge evaluation, attaches attributes from
 * {@link RunnableConfig}, and records start/end events that include serialized state.
 * </p>
 *
 * @param <State> workflow state type
 */
public class OTELWrapCallTraceHook<State extends AgentState> implements NodeHook.WrapCall<State>, EdgeHook.WrapCall<State>, OTELObservable {

    final StateSerializer<?> stateSerializer;
    final TracerHolder tracer;

    /**
     * Creates a new tracing hook.
     *
     * @param stateSerializer serializer used to convert state data into attributes
     */
    public OTELWrapCallTraceHook(StateSerializer<?> stateSerializer) {
        this.stateSerializer = requireNonNull(stateSerializer, "stateSerializer cannot be null");
        tracer = new TracerHolder(otel(), getClass().getName() );
    }

    /**
     * Wraps a node call with a span named {@code evaluateNode}.
     *
     * @param nodeId node identifier
     * @param state current state
     * @param config runnable configuration
     * @param action node action
     * @return future result of the wrapped action
     */
    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String nodeId,
                                                            State state,
                                                            RunnableConfig config,
                                                            AsyncNodeActionWithConfig<State> action) {
        var span = tracer.spanBuilder("evaluateNode")
                .setAttribute("nodeId", nodeId)
                .setAllAttributes( OTELObservable.attrsOf( config ) )
                .startSpan();

        return tracer.applySpan( span, $ -> {
            log.info("\nnode start: '{}' with state: {}",
                    nodeId,
                    state);

            try ( var scope = span.makeCurrent() ) {

                span.addEvent( "start", OTELObservable.attrsOf( state.data(), stateSerializer) );

                return action.apply( state, config )
                    .whenComplete( (result, ex ) -> {
                        if( ex != null ) {
                            return;
                        }

                        span.addEvent( "end", OTELObservable.attrsOf( result, stateSerializer) );

                        log.info("\nnode end: '{}' with result: {}",
                                nodeId,
                                result);
                    });
                }
            });

    }

    /**
     * Wraps an edge call with a span named {@code evaluateEdge}.
     *
     * @param sourceId source node identifier
     * @param state current state
     * @param config runnable configuration
     * @param action edge action
     * @return future command result
     */
    @Override
    public CompletableFuture<Command> applyWrap(String sourceId,
                                                State state,
                                                RunnableConfig config,
                                                AsyncCommandAction<State> action) {

        var span = tracer.spanBuilder("evaluateEdge")
                .setAttribute("sourceId", sourceId)
                .setAllAttributes( OTELObservable.attrsOf( config ) )
                .startSpan();

        return tracer.applySpan( span, $ -> {

            log.info("\nedge start from: '{}' with state: {}", sourceId, state);

            try ( var scope = span.makeCurrent() ) {

                span.addEvent("start", OTELObservable.attrsOf(state.data(), stateSerializer));

                return action.apply(state, config).whenComplete((result, ex) -> {

                    if (ex != null) {
                        return;
                    }

                    span.addEvent("end", OTELObservable.attrsOf(result, stateSerializer));

                    log.info("\nedge end: {}", result);

                });
            }
        });
    }
}
