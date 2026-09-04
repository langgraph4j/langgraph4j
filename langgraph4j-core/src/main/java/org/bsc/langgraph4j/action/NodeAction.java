package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * Defines a node in the graph that reads and writes state.
 *
 * <p>A NodeAction is the primary building block of a LangGraph4j graph.
 * Each node receives the current agent state, performs computation, and
 * returns a map of updates to be merged into the state by their corresponding
 * channels.
 *
 * @param <T> the state type, which must extend {@link AgentState}
 * @throws Exception if the node's computation fails
 * @see CommandAction
 */
@FunctionalInterface
public interface NodeAction <T extends AgentState> {
    Map<String, Object> apply(T state) throws Exception;
}