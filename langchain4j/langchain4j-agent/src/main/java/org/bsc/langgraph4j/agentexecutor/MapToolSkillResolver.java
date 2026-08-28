package org.bsc.langgraph4j.agentexecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit tool → skill id bindings for {@link ToolSkillResolver}.
 */
public final class MapToolSkillResolver implements ToolSkillResolver {

    private final Map<String, List<String>> bindings = new LinkedHashMap<>();

    public MapToolSkillResolver bind(String toolName, String skillId) {
        bindings.computeIfAbsent(toolName, k -> new ArrayList<>()).add(skillId);
        return this;
    }

    public MapToolSkillResolver bind(String toolName, List<String> skillIds) {
        bindings.computeIfAbsent(toolName, k -> new ArrayList<>()).addAll(skillIds);
        return this;
    }

    @Override
    public List<String> resolve(String toolName) {
        var ids = bindings.get(toolName);
        return ids == null ? List.of() : List.copyOf(ids);
    }
}
