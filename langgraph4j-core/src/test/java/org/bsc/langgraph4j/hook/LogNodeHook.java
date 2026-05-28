package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;

public interface LogNodeHook extends LG4JLoggable {

    static <State extends AgentState> NodeHook.BeforeCall<State> applyBeforeHook() {

        return (nodeId, state, config) -> {
            log.info("""
                     node before call start:
                     hook on '{}'
                     path: '{}'
                     """,
                    nodeId,
                    config.nodePath());

            return completedFuture(Map.<String,Object>of())
                    .whenComplete( ( result, exception ) ->
                            log.info("""
                                     node before call end:
                                     hook on '{}'
                                     path: '{}'
                                    """,
                                    nodeId,
                                    config.nodePath()));

        };
    }

    static <State extends AgentState> NodeHook.AfterCall<State> applyAfterHook() {
        return (nodeId, state, config, lastResult) -> {
            log.info("""
                     node after call start:
                     hook on '{}'
                     path: '{}'
                    """,
                    nodeId,
                    config.nodePath());


            return completedFuture(lastResult)
                    .whenComplete((result, exception) ->
                            log.info("""
                                     node after call end:
                                     hook on '{}'
                                     path: '{}'
                                    """,
                                    nodeId,
                                    config.nodePath()));
        };
    }

}
