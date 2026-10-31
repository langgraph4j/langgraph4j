package org.bsc.langgraph4j.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code FrontMatter} is a public record, so a caller may build one whose value is neither a
 * {@code String} nor a {@code List}. Asking for such a value as a list reports a problem, and the
 * report has to name the offending property: the message template is written with {@code %s} but
 * was never formatted, so the caller only ever saw a literal {@code %s}.
 */
class SkillParserFrontMatterTypeErrorTest {

    @Test
    void typeMismatchErrorNamesTheOffendingProperty() {

        final var frontMatter = new SkillParser.FrontMatter( Map.of( "allowed-tools", 42 ) );

        final var error = assertThrows( IllegalStateException.class,
                () -> frontMatter.getStringList( "allowed-tools" ) );

        assertTrue( error.getMessage().contains( "'allowed-tools'" ),
                "the error should name the property, but was: " + error.getMessage() );

        assertTrue( !error.getMessage().contains( "%s" ),
                "the error should not leak an unformatted placeholder, but was: " + error.getMessage() );
    }
}
