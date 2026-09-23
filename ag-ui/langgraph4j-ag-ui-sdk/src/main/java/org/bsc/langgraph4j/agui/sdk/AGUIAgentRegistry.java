package org.bsc.langgraph4j.agui.sdk;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * A registry of {@link AGUIAgent} instances, keyed by their {@link AGUIAgent#id() id}.
 * <p>
 * Allows looking up a registered agent by its identifier, typically used to dispatch
 * an incoming AG-UI run request to the appropriate agent implementation.
 *
 * @param map the map of agent id to agent instance backing this registry
 */
public record AGUIAgentRegistry(Map<String, AGUIAgent> map) {

    /**
     * Looks up a registered agent by its identifier.
     *
     * @param agentId the identifier of the agent to look up
     * @return an {@link Optional} containing the matching agent, or empty if none is registered
     *         with the given id
     */
    public Optional<AGUIAgent> agent(String agentId) {
        return ofNullable(map.get(agentId));
    }

    /**
     * Creates a registry from the given agents, indexed by their {@link AGUIAgent#id() id}.
     *
     * @param agents the agents to register
     */
    public AGUIAgentRegistry(AGUIAgent... agents) {
        this(Arrays.stream(agents).collect(java.util.stream.Collectors.toMap(AGUIAgent::id, agent -> agent)));
    }

}
