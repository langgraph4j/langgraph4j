package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

/**
 * Defines a contract for actions that can interrupt the execution of a graph.
 *
 * @param <State> The type of the agent state, which must extend {@link AgentState}.
 * @deprecated use {@link InterruptibleAction} instead
 */
@Deprecated(forRemoval = true)
public interface InterruptableAction<State extends AgentState> extends InterruptibleAction<State> {

}