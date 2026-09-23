package org.bsc.langgraph4j.agui.sdk;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;


/**
 * LangGraph4j {@link NodeHook} implementation dedicated to the AG-UI protocol.
 * <p>
 * It provides a ready-made {@link NodeHook.WrapCall} that wraps a node's action execution
 * so that the corresponding AG-UI {@code StepStartedEvent} and {@code StepFinishedEvent}
 * are automatically dispatched (via the {@link RunnableConfig#customDispatcher() custom
 * dispatcher}) right before the node starts and right after it successfully completes,
 * without requiring any manual event handling in the node's own logic.
 */
public interface AGUIHook extends LG4JLoggable {

    /**
     * Returns a {@link NodeHook.WrapCall} that automatically emits the AG-UI
     * {@code StepStartedEvent} before invoking the wrapped node action, and the AG-UI
     * {@code StepFinishedEvent} once the action completes successfully.
     * <p>
     * If the wrapped action completes exceptionally, the {@code StepFinishedEvent} is not
     * dispatched, leaving error handling/events to the caller (e.g. {@link AGUIAgentBase}).
     *
     * @param <State> the type of the agent state used by the graph
     * @return a wrap-call hook emitting step start/finish AG-UI events around node execution
     */
    static <State extends AgentState> NodeHook.WrapCall<State> stepEvents() {
        return (String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) -> {

            config.customDispatcher().dispatchAsync(AGUINodeOutput.builder()
                    .stepStartedEvent(nodeId)
                    .build( nodeId, state));

            log.trace("start node '{}'", config.nodeId());
            return action.apply(state, config)
                    .whenComplete( ( result, exception ) -> {

                        if( exception == null ) {
                            config.customDispatcher().dispatchAsync(AGUINodeOutput.builder()
                                    .stepFinishedEvent(nodeId)
                                    .build( nodeId, state));
                            log.trace("end node action '{}'", config.nodeId());
                        }
                    });
        };
    }
}
