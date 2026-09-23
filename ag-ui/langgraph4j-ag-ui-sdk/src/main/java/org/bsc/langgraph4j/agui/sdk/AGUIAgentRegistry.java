package org.bsc.langgraph4j.agui.sdk;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public record AGUIAgentRegistry(Map<String, AGUIAgent> map) {

    public Optional<AGUIAgent> agent(String agentId) {
        return ofNullable(map.get(agentId));
    }

    public AGUIAgentRegistry(AGUIAgent... agents) {
        this(Arrays.stream(agents).collect(java.util.stream.Collectors.toMap(AGUIAgent::id, agent -> agent)));
    }

}
