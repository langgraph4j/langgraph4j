package org.bsc.langgraph4j.agentexecutor;

import java.util.List;

/**
 * Maps a business tool name to zero or more skill ids to activate.
 */
@FunctionalInterface
public interface ToolSkillResolver {

    /**
     * @param toolName tool that executed successfully
     * @return skill ids to activate (never {@code null}; empty = none)
     */
    List<String> resolve(String toolName);
}
