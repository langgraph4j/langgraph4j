package org.bsc.langgraph4j.agent.skill;

import java.util.*;
import java.util.stream.Collectors;

public class SkillParser {

    public record FrontMatter( Map<String, Object> values ) {

        public Optional<String> getString(String key ) {
            final var value = values.get(key);

            if( value == null ) {
                return Optional.empty();
            }
            if( value instanceof String stringValue ) {
                return Optional.of(stringValue);
            }
            return Optional.of(Objects.toString(value));
        }

        @SuppressWarnings("unchecked")
        public Optional<List<String>> getStringList( String key ) {
            final var value = values.get(key);

            if( value == null ) {
                return Optional.empty();
            }
            if( value instanceof List<?> listValue ) {
                return Optional.of( (List<String>)listValue );
            }
            if( value instanceof String stringValue ) {
                return Optional.of(List.of(stringValue.split(" ")));
            }
            throw new IllegalStateException("property '%s' doesn't contain a list of strings! ");
        }
    }

    public static SkillParser of( String markdown ) {
        return new SkillParser(markdown);
    }

    /**
     * Map containing the parsed front matter key-value pairs.
     */
    private final Map<String,Object> frontMatter;

    /**
     * The content of the markdown document (everything after the front matter).
     */
    private String content = "";

    /**
     * Constructs a new MarkdownParser and parses the provided markdown content. Parses
     * the markdown content to extract front matter and body content.
     * <p>
     * Front matter must start with "---" at the beginning of the document and end with
     * another "---". Everything between these delimiters is parsed as front matter.
     * Everything after the closing delimiter is treated as content.
     * @param markdown the markdown string to parse, may contain front matter delimited by
     * triple dashes (---). Can be null or empty.
     */
    private SkillParser(String markdown) {

        if (markdown == null || markdown.isEmpty()) {
            frontMatter = Map.of();
            return;
        }

        frontMatter = new HashMap<>();
        // Check if document starts with front-matter delimiter (---)
        if (markdown.startsWith("---")) {
            // Find the closing delimiter
            int endIndex = markdown.indexOf("---", 3);

            if (endIndex != -1) {
                // Extract front-matter section
                String frontMatterSection = markdown.substring(3, endIndex).trim();
                parseFrontMatter(frontMatterSection);

                // Extract remaining content (skip the closing --- and any following
                // newlines)
                content = markdown.substring(endIndex + 3).trim();
            }
            else {
                // No closing delimiter found, treat entire document as content
                content = markdown;
            }
        }
        else {
            // No front-matter, entire document is content
            content = markdown;
        }

    }

    private void parseFrontMatter(String frontMatterSection) {
        String[] lines = frontMatterSection.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) {
                continue;
            }

            // Split on first colon
            int colonIndex = trimmedLine.indexOf(':');
            if (colonIndex > 0) {
                String key = trimmedLine.substring(0, colonIndex).trim();
                String value = trimmedLine.substring(colonIndex + 1).trim();

                if ("|".equals(value)) {
                    List<String> blockLines = new ArrayList<>();

                    while (i + 1 < lines.length) {
                        String nextLine = lines[i + 1];
                        if (!nextLine.isEmpty() && !Character.isWhitespace(nextLine.charAt(0))) {
                            break;
                        }
                        blockLines.add(nextLine);
                        i++;
                    }

                    frontMatter.put(key, normalizeBlockValue(blockLines));
                    continue;
                }

                // Handle YAML block sequence (array syntax):
                //   key:
                //     - item1
                //     - item2
                if (value.isEmpty()) {
                    List<String> arrayItems = new ArrayList<>();

                    while (i + 1 < lines.length) {
                        String nextLine = lines[i + 1];
                        String trimmedNext = nextLine.trim();
                        if (!trimmedNext.startsWith("- ")) {
                            break;
                        }
                        arrayItems.add(trimmedNext.substring(2).trim());
                        i++;
                    }

                    if (!arrayItems.isEmpty()) {
                        frontMatter.put(key, arrayItems);
                        continue;
                    }
                }

                // Removes surrounding quotes from a value string if present.
                value = removeQuotes(value);

                frontMatter.put(key, value);
            }
        }
    }

    private String normalizeBlockValue(List<String> blockLines) {
        int indent = blockLines.stream()
            .filter(line -> !line.trim().isEmpty())
            .mapToInt(this::leadingWhitespaceCount)
            .min()
            .orElse(0);

        return blockLines.stream()
            .map(line -> stripIndent(line, indent))
            .collect(Collectors.joining("\n"))
            .stripTrailing();
    }

    private int leadingWhitespaceCount(String value) {
        int count = 0;
        while (count < value.length() && Character.isWhitespace(value.charAt(count))) {
            count++;
        }
        return count;
    }

    private String stripIndent(String value, int indent) {
        int stripCount = Math.min(indent, leadingWhitespaceCount(value));
        return value.substring(stripCount);
    }

    private String removeQuotes(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Returns a copy of the parsed front matter as a map.
     * <p>
     * The returned map contains all key-value pairs extracted from the front matter
     * section. If no front matter was present or the input was null/empty, returns an
     * empty map.
     * @return a new map containing the front matter key-value pairs
     */
    public FrontMatter getFrontMatter() {
        return new FrontMatter( Map.copyOf(frontMatter));
    }

    /**
     * Returns the content portion of the markdown document.
     * <p>
     * This is everything after the closing front matter delimiter (---), with leading and
     * trailing whitespace trimmed. If no front matter was present, returns the entire
     * document. If the input was null or empty, returns an empty string.
     * @return the markdown content as a string
     */
    public String getContent() {
        return content;
    }

}
