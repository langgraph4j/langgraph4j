package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents an asynchronous node action that operates on an agent state and returns state update.
 *
 * @param <S> the type of the agent state
 */
@FunctionalInterface
public interface AsyncNodeAction<S extends AgentState> extends Function<S, CompletableFuture<Map<String, Object>>> {

    /**
     * Applies this action to the given agent state.
     *
     * @param state the agent state
     * @return a CompletableFuture representing the result of the action
     */
    CompletableFuture<Map<String, Object>> apply(S state);

    /**
     * Creates an asynchronous node action from a synchronous node action.
     *
     * @param syncAction the synchronous node action
     * @param <S> the type of the agent state
     * @return an asynchronous node action
     */
    static <S extends AgentState> AsyncNodeAction<S> node_async(NodeAction<S> syncAction) {
        return t -> {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            try {
                result.complete(syncAction.apply(t));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return result;
        };
    }
}
