package org.bsc.langgraph4j.agui.sdk;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;


public interface AGUIHook extends LG4JLoggable {

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
