package org.bsc.langgraph4j.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void preservesDashesInsideFrontMatterValues(String newline) {
        String markdown = String.join(newline,
                "---",
                "name: demo-skill",
                "description: compare before---after",
                "allowed-tools: Read",
                "---",
                "# Body");

        final var parser = SkillParser.of(markdown);

        assertEquals("compare before---after", parser.getFrontMatter().values().get("description"));
        assertEquals(List.of("Read"), parser.getFrontMatter().getStringList("allowed-tools").orElseThrow());
        assertEquals("# Body", parser.getContent());
    }

    @Test
    void preservesDashesInsideMultilineFrontMatter() {
        String markdown = """
                ---
                name: demo-skill
                description: |
                  Compare before---after
                  Keep the next line
                ---
                # Body
                """;

        final var parser = SkillParser.of(markdown);

        assertEquals("Compare before---after\nKeep the next line", parser.getFrontMatter().values().get("description"));
        assertEquals("# Body", parser.getContent());
    }

    @Test
    void requiresAClosingDelimiterLine() {
        String markdown = "---\nname: demo-skill\ndescription: before---after";

        final var parser = SkillParser.of(markdown);

        assertTrue(parser.getFrontMatter().values().isEmpty());
        assertEquals(markdown, parser.getContent());
    }

    @Test
    void parsesYamlMultilineDescriptionBlock() {
        String markdown = """
                ---
                name: demo-skill
                description: |
                  First line
                  Second line

                  Fourth line
                ---
                # Body
                line1
                line2
                """;

        final var parser = SkillParser.of(markdown);

        assertEquals("demo-skill", parser.getFrontMatter().values().get("name"));
        assertEquals("First line\nSecond line\n\nFourth line", parser.getFrontMatter().values().get("description"));
        assertEquals("""
                # Body
                line1
                line2""", parser.getContent());
    }

    @Test
    void preservesSingleLineFrontMatterValues() {
        String markdown = """
                ---
                name: demo-skill
                description: "Single line"
                ---
                body
                line1
                line2
                """;

        final var parser = SkillParser.of(markdown);

        assertEquals("Single line", parser.getFrontMatter().values().get("description"));
        assertEquals("""
                    body
                    line1
                    line2""", parser.getContent());
    }

    @Test
    void parsesYamlAllowedToolsBlock() {
        String markdown = """
                ---
                name: demo-skill
                description: this is a allowed-tools demo
                allowed-tools: a b c
                ---
                # Body
                line1
                line2
                """;

        final var parser = SkillParser.of(markdown);

        assertEquals("demo-skill", parser.getFrontMatter().values().get("name"));
        assertEquals("this is a allowed-tools demo", parser.getFrontMatter().values().get("description"));
        assertEquals("a b c", parser.getFrontMatter().values().get("allowed-tools"));
        final var allowedTools = parser.getFrontMatter().getStringList("allowed-tools");
        assertTrue(allowedTools.isPresent());
        assertEquals(List.of( "a", "b", "c"), allowedTools.get());
        assertEquals("""
                # Body
                line1
                line2""", parser.getContent());

    }

    @Test
    void parsesYamlArraySyntax() {
        String markdown = """
                ---
                name: demo-skill
                description: |
                    this is an array demo
                allowed-tools:
                  - Bash:grep
                  - Bash:curl
                  - Read
                ---
                # Body
                line1
                line2
                """;

        final var parser = SkillParser.of(markdown);

        assertEquals("demo-skill", parser.getFrontMatter().values().get("name"));
        assertEquals("this is an array demo", parser.getFrontMatter().values().get("description"));
        final var expectedAllowedTools = List.of("Bash:grep", "Bash:curl", "Read");
        assertEquals(expectedAllowedTools, parser.getFrontMatter().values().get("allowed-tools"));
        final var allowedTools = parser.getFrontMatter().getStringList("allowed-tools");
        assertTrue(allowedTools.isPresent());
        assertEquals(expectedAllowedTools, allowedTools.get());
        assertEquals("""
                # Body
                line1
                line2""", parser.getContent());
    }

}
