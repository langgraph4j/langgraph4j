package org.bsc.langgraph4j.utils;

import org.bsc.langgraph4j.LG4JLoggable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public interface SqlResource extends LG4JLoggable  {

    @FunctionalInterface
    interface Process<R>  {

        R apply(String content) throws Exception;
    }

    class Commands {
        private final Map<String, String> sqlCommands;

        public Commands(String commandResourcePath) throws Exception {
            sqlCommands = extractSqlCommands(requireNonNull(commandResourcePath, "commandResourcePath cannot be null"));
        }

        public String get( String key ) {
            return ofNullable(sqlCommands.get(key))
                    .orElseThrow(() -> new IllegalStateException("SQL command '%s' not found in resource".formatted(key)));
        }

        private Map<String, String> extractSqlCommands(String resourcePath) throws Exception {

            final Optional<Map<String, String>> result = load(
                    requireNonNull(resourcePath, "resourcePath cannot be null"), (String content) -> {

                        var commands = new LinkedHashMap<String, String>();
                        String currentCommand = null;
                        var sqlBlock = new StringBuilder();

                        for (String line : content.split("\\R", -1)) {
                            line = line.stripLeading();
                            if (line.startsWith("--")) {
                                if (currentCommand != null) {
                                    putCommand(commands, currentCommand, sqlBlock);
                                }
                                currentCommand = line.substring(2).trim();
                                sqlBlock.setLength(0);
                            }
                            else if (currentCommand != null) {
                                sqlBlock.append(line).append('\n');
                            }
                        }

                        if (currentCommand != null) {
                            putCommand(commands, currentCommand, sqlBlock);
                        }

                        return commands;
                    });
            return result.orElseThrow(() -> new IllegalStateException("Failed to load SQL commands from resource '%s'".formatted(resourcePath)));
        }

        private static void putCommand(Map<String, String> commands, String command, StringBuilder sqlBlock) {
            var sql = sqlBlock.toString();

            if (!command.isEmpty() && !sql.isEmpty()) {
                if( commands.containsKey(command)  ) {
                    throw new IllegalStateException("Duplicate command '%s' found in SQL resource!".formatted(command));
                }
                commands.put(command, sql);
            }
        }

    }

    static <R> Optional<R> load(String resourcePath, Process<R> process ) throws Exception {
        requireNonNull(resourcePath, "resourceName cannot be null");
        final var classLoader = SqlResource.class.getClassLoader();

        try (var inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            final var content =  new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return ofNullable(process.apply(content));
        }
    }



}
