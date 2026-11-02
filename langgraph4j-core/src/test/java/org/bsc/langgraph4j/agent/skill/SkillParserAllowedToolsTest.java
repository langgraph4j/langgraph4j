package org.bsc.langgraph4j.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code allowed-tools} is a hand-written front matter value, and the repository's own
 * {@code drawio-mcp/SKILL.md} sample writes it as a comma separated list:
 * <pre>
 * allowed-tools: Bash, Write
 * </pre>
 * The parser only split on a single space, so the comma stayed glued to the preceding
 * tool name and {@code SkilledReactSubAgent} then dropped that tool from the agent.
 */
class SkillParserAllowedToolsTest {

    private static List<String> allowedToolsOf(String allowedToolsValue) {
        final var markdown = """
                ---
                name: drawio
                description: Generate draw.io diagrams as .drawio files
                allowed-tools: %s
                ---
                # Body
                """.formatted(allowedToolsValue);

        return SkillParser.of(markdown)
                .getFrontMatter()
                .getStringList("allowed-tools")
                .orElseThrow();
    }

    @Test
    void splitsCommaSeparatedTools() {
        // the exact form used by langgraph4j-core/src/test/resources/skills/drawio-mcp/SKILL.md
        assertEquals(List.of("Bash", "Write"), allowedToolsOf("Bash, Write"));
    }

    @Test
    void splitsCommaSeparatedToolsWithoutSpace() {
        assertEquals(List.of("Bash", "Write"), allowedToolsOf("Bash,Write"));
    }

    @Test
    void doesNotProduceEmptyEntriesForRepeatedSeparators() {
        assertEquals(List.of("Bash", "Write"), allowedToolsOf("Bash,  Write"));
        assertEquals(List.of("Bash", "Write"), allowedToolsOf("Bash  Write"));
    }

    @Test
    void keepsSpaceSeparatedToolsWorking() {
        assertEquals(List.of("a", "b", "c"), allowedToolsOf("a b c"));
    }
}
