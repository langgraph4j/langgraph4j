package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

/**
 * Defines a node that can also send command signals (goto, interrupt, etc.)
 * in addition to updating state.
 *
 * <p>A CommandAction is used when a node needs to direct the graph execution
 * to a specific next node, interrupt the execution, or otherwise control
 * the traversal of the graph beyond simple state updates.
 *
 * @param <S> the state type, which must extend {@link AgentState}
 * @see AsyncCommandAction
 * @see Command
 */
@FunctionalInterface
public interface CommandAction<S extends AgentState> {
    Command apply(S state, RunnableConfig config) throws Exception;
}
