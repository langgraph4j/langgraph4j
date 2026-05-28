package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public record NestedNodeHook<State extends AgentState>( String level ) implements LG4JLoggable {

    public static String AFTER_CALL_ATTRIBUTE = "node-after-call";
    public static String HOOKS_ATTRIBUTE = "node-hooks";

    public static <State extends AgentState> NestedNodeHook<State> of( String level ) {
        return new NestedNodeHook<>(level);
    }

    public NodeHook.WrapCall<State> applyWrapHook() {
        return (nodeId, state, config, action) -> {
            log.info("""
            node wrap call start:
            hook on '{}'
            level '{}'
            """, nodeId, level);

            return action.apply(state, config)
                    .thenApply(result ->
                            mergeMap( result, Map.of(HOOKS_ATTRIBUTE, Map.of( nodeId, List.of(level))), ( left, right ) -> right ))
                    .whenComplete( ( result, exception ) -> {
                        log.info("""
                        node wrap call end:
                        hook on '{}'
                        level '{}'
                        """, nodeId, level);
                    });
        };
    }

    public NodeHook.BeforeCall<State> applyBeforeHook( Map<String,Channel<?>> schema ) {

        return (nodeId, state, config) -> {
            log.info("""
            node before call start:
            hook on '{}'
            level '{}'
            path: '{}'
            """, nodeId, level, config.nodePath());

            final var newState = AgentState.updateState( state.data(),  Map.of(HOOKS_ATTRIBUTE, Map.of( nodeId, List.of(level))), schema);
            return completedFuture(newState)
                    .whenComplete( ( result, exception ) ->
                            log.info("""
                            node before call end:
                            hook on '{}'
                            level '{}'
                            path: '{}'
                            """, nodeId, level, config.nodePath()));
        };
    }

    public NodeHook.AfterCall<State> applyAfterHook( Map<String,Channel<?>> schema ) {
        return (nodeId, state, config, lastResult) -> {
            log.info("""
            node after call start:
            hook on '{}'
            level '{}'
            path: '{}'
            """, nodeId, level, config.nodePath());


            final var newResult = AgentState.updateState( lastResult,  Map.of(AFTER_CALL_ATTRIBUTE, 1), schema);

            return completedFuture(newResult)
                    .whenComplete((result, exception) ->
                            log.info("""
                                node after call end:
                                hook on '{}'
                                level '{}'
                                path: '{}'
                                """, nodeId, level, config.nodePath()));
        };
    }
}
