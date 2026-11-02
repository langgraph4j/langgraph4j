package org.bsc.langgraph4j.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillParserTest {

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

        assertEquals("""
        Compare before---after
        Keep the next line""", parser.getFrontMatter().values().get("description"));
        assertEquals("# Body", parser.getContent());
    }

    @Test
    void requiresAClosingDelimiterLine() {
        String markdown = """
                ---
                name: demo-skill
                description: before---after
                """;

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
        assertEquals("""
        First line
        Second line

        Fourth line""", parser.getFrontMatter().values().get("description"));
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

    /**
     * {@code FrontMatter} is a public record, so a caller may build one whose value is neither a
     * {@code String} nor a {@code List}. Asking for such a value as a list reports a problem, and the
     * report has to name the offending property: the message template is written with {@code %s} but
     * was never formatted, so the caller only ever saw a literal {@code %s}.
     */
    @Test
    void typeMismatchErrorNamesTheOffendingProperty() {

        final var frontMatter = new SkillParser.FrontMatter( Map.of( "allowed-tools", 42 ) );

        final var error = assertThrows( IllegalStateException.class,
                () -> frontMatter.getStringList( "allowed-tools" ) );

        assertTrue( error.getMessage().contains( "'allowed-tools'" ),
                "the error should name the property, but was: " + error.getMessage() );

        assertFalse(error.getMessage().contains("%s"), "the error should not leak an unformatted placeholder, but was: " + error.getMessage());
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
