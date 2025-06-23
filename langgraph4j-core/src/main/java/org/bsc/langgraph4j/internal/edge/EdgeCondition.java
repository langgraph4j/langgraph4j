package org.bsc.langgraph4j.internal.edge;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

import static java.lang.String.format;

/**
 * Represents a condition associated with an edge in a graph.
 *
 * @param <S> the type of the state associated with the edge
 * @param action The action to be performed asynchronously when the edge condition is met.
 * @param mappings A map of string key-value pairs representing additional mappings for the edge condition.
 */
public record EdgeCondition<S extends AgentState>(AsyncCommandAction<S> action, Map<String, String> mappings ) {

    @Override
    public String toString() {
        return format( "EdgeCondition[ %s, mapping=%s ]",
                action!=null ? "action" : "null",
                mappings);
    }

}
