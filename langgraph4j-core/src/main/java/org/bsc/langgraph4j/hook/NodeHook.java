package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeResult;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface NodeHook {

    @FunctionalInterface
    interface BeforeCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyBefore(String nodeId, State state, RunnableConfig config );

    }

    @FunctionalInterface
    interface AfterCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyAfter(String nodeId, State state, RunnableConfig config, Map<String, Object> lastResult ) ;

        default CompletableFuture<NodeResult> applyAfterWithResult(String nodeId, State state, RunnableConfig config, NodeResult lastResult ) {
            return applyAfter(nodeId, state, config, lastResult.data() ).thenApply(NodeResult::withData);
        }

    }

    @FunctionalInterface
    interface WrapCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action);

        default CompletableFuture<NodeResult> applyWrapWithResult(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {
            return applyWrap(nodeId, state, config, action).thenApply(NodeResult::withData);
        }

    }


}

