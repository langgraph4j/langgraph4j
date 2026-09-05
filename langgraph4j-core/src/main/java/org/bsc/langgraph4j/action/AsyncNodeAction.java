package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

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
    @SuppressWarnings("unchecked")
    static <S extends AgentState> AsyncNodeAction<S> node_async(NodeAction<S> syncAction) {

        final AsyncNodeAction<S> asyncAction = t -> {
            try {
                return completedFuture(syncAction.apply(t));
            } catch (Exception e) {
                return failedFuture(e);
            }
        };

        if( syncAction instanceof InterruptibleAction<?> ) {
            final var proxyInstance = Proxy.newProxyInstance( syncAction.getClass().getClassLoader(),
                    new Class[] { AsyncNodeAction.class, InterruptibleAction.class},
                    (proxy, method, methodArgs) -> {
                        if( method.getName().equals("interrupt") ) {
                            return method.invoke(syncAction, methodArgs);
                        }
                        return method.invoke(asyncAction, methodArgs);
                    }
            );
            return (AsyncNodeAction<S>) proxyInstance;
        }

        return asyncAction;
    }
}
