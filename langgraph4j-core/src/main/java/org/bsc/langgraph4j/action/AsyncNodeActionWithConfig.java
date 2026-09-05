package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Represents an asynchronous action that can be executed with a configuration.
 *
 * @param <S> the type of the agent state
 */
public interface AsyncNodeActionWithConfig<S extends AgentState> extends BiFunction<S, RunnableConfig,CompletableFuture<Map<String, Object>>> {

    AsyncNodeActionWithConfig<?> NOOP = ( state, config ) ->
            completedFuture(Map.of());

    @SuppressWarnings("unchecked")
    static <S extends AgentState> AsyncNodeActionWithConfig<S> noop() {
        return (AsyncNodeActionWithConfig<S>) NOOP;
    }

    /**
     * Applies this action to the given agent state.
     *
     * @param state the agent state
     * @return a CompletableFuture representing the result of the action
     */
    CompletableFuture<Map<String, Object>> apply(S state, RunnableConfig config);


    /**
     * Converts a synchronous {@link NodeActionWithConfig} to an asynchronous one.
     *
     * @param <S>   the type of agent state
     * @param syncAction the synchronous action to be converted
     * @return an {@link AsyncNodeActionWithConfig} representation of the given synchronous action
     */
    @SuppressWarnings("unchecked")
    static <S extends AgentState> AsyncNodeActionWithConfig<S> node_async(NodeActionWithConfig<S> syncAction) {

        final AsyncNodeActionWithConfig<S> asyncAction = (t, config ) -> {
            try {
                return completedFuture(syncAction.apply(t, config));
            } catch (Throwable e) {
                return failedFuture(e);
            }
        };

        if( syncAction instanceof InterruptibleAction<?> ) {
            final var proxyInstance = Proxy.newProxyInstance( syncAction.getClass().getClassLoader(),
                    new Class[] { AsyncNodeActionWithConfig.class, InterruptibleAction.class},
                    (proxy, method, methodArgs) -> {
                        if( method.getName().equals("interrupt") ) {
                            return method.invoke(syncAction, methodArgs);
                        }
                        return method.invoke(asyncAction, methodArgs);
                    }
            );
            return (AsyncNodeActionWithConfig<S>) proxyInstance;
        }

        return asyncAction;
    }

    /**
     * Adapts a simple AsyncNodeAction to an AsyncNodeActionWithConfig.
     *
     * @param action the simple AsyncNodeAction to be adapted
     * @param <S> the type of the agent state
     * @return an AsyncNodeActionWithConfig that wraps the given AsyncNodeAction
     */
    @SuppressWarnings("unchecked")
    static <S extends AgentState> AsyncNodeActionWithConfig<S> of(AsyncNodeAction<S> action) {
        final AsyncNodeActionWithConfig<S> actionWithConfig = (t, config ) ->
                action.apply(t);

        if( action instanceof InterruptibleAction<?> ) {
            final var proxyInstance = Proxy.newProxyInstance( action.getClass().getClassLoader(),
                    new Class[] { AsyncNodeActionWithConfig.class, InterruptibleAction.class},
                    (proxy, method, methodArgs) -> {
                        if( method.getName().equals("interrupt") ) {
                            return method.invoke(action, methodArgs);
                        }
                        return method.invoke(actionWithConfig, methodArgs);
                    }
            );
            return (AsyncNodeActionWithConfig<S>) proxyInstance;
        }

        return actionWithConfig;
    }

}