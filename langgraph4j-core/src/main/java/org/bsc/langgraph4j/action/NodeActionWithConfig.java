package org.bsc.langgraph4j.action;


import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

@FunctionalInterface
public interface NodeActionWithConfig<S extends AgentState> {
    Map<String, Object> apply(S state, RunnableConfig config) throws Exception;

    default NodeResult applyWithResult( S state, RunnableConfig config) throws Exception {
        return NodeResult.withData( apply(state, config) );
    }
}
