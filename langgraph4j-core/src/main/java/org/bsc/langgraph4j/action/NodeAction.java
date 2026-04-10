package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

@Deprecated
@FunctionalInterface
public interface NodeAction <State extends AgentState> {
    Map<String, Object> apply(State state) throws Exception;

    default NodeResult applyWithResult( State state, RunnableConfig config) throws Exception {
        return NodeResult.withData( apply(state) );
    }


}

