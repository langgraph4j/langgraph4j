package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

/**
 * Represents an edge action that operates on an agent state and returns a result.
 *
 * @param <S> the type of the agent state
 */
@FunctionalInterface
public interface EdgeAction<S extends AgentState> {

    /**
     * Applies this action to the given agent state.
     *
     * @param state the agent state
     * @return a result of the action
     * @throws Exception if an error occurs during the action
     */
    String apply(S state) throws Exception;
}
