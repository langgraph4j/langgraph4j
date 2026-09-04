package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Optional;

/**
 * Defines a contract for actions that can interrupt the execution of a graph.
 * This is a functional interface whose functional method is {@link #interrupt(String, AgentState)}.
 *
 * @param <State> The type of the agent state, which must extend {@link AgentState}.
 */
public interface InterruptibleAction<State extends AgentState> {

    /**
     * Determines whether the graph execution should be interrupted at the current node.
     *
     * @param nodeId The identifier of the current node being processed.
     * @param state  The current state of the agent.
     * @return An {@link Optional} containing {@link InterruptionMetadata} if the execution
     *         should be interrupted. Returns an empty {@link Optional} to continue execution.
*    * @deprecated use {@link #interrupt(String,State,RunnableConfig)} instead
     */
    @Deprecated(forRemoval = true)
    default Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state ) {
        return Optional.empty();
    }

    /**
     * Determines whether the graph execution should be interrupted at the current node.
     * override this method if you need information by current RunnableConfig
     *
     * @param nodeId The identifier of the current node being processed.
     * @param state  The current state of the agent.
     * @param config The runnable configuration.
     * @return An {@link Optional} containing {@link InterruptionMetadata} if the execution
     *         should be interrupted. Returns an empty {@link Optional} to continue execution.
     */
    default Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state, RunnableConfig config ) {
        return interrupt(nodeId, state);
    }

}