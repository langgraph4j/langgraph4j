package org.bsc.langgraph4j.utils;

import org.bsc.langgraph4j.LG4JLoggable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public interface SqlResource extends LG4JLoggable  {

    class Commands {
        public final Map<String, String> map;

        public static Commands load(String commandResourcePath) throws Exception {
            return new Commands(commandResourcePath);
        }
        private Commands(String commandResourcePath) throws Exception {
            map = loadSqlCommands(commandResourcePath);
        }

        public String get( String key ) {
            return ofNullable(map.get(key))
                    .orElseThrow(() -> new IllegalStateException("SQL command '%s' not found in resource".formatted(key)));
        }
        public List<String> getMultiple( String key ) {
            final var result =  Stream.of(get(key).split(";"))
                    .map(String::trim)
                    .filter(sql -> !sql.isEmpty())
                    .toList();
            if( result.isEmpty() ) {
                throw new IllegalStateException("SQL commands '%s' not found in resource".formatted(key));
            }
            return result;
        }

    }

    static Map<String,String> loadSqlCommands(String resourcePath ) throws Exception {
        requireNonNull(resourcePath, "resourceName cannot be null");
        final var classLoader = SqlResource.class.getClassLoader();

        try (var inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("SQL resource '%s' not found!".formatted(resourcePath));

            }
            final var content =  new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            final var commands = new LinkedHashMap<String, String>();
            final var sqlBlock = new StringBuilder();

            final Consumer<String> putCommand = (command) -> {
                var sql = sqlBlock.toString();

                if (!command.isEmpty() && !sql.isEmpty()) {
                    if( commands.containsKey(command)  ) {
                        throw new IllegalStateException("Duplicate command '%s' found in SQL resource!".formatted(command));
                    }
                    commands.put(command, sql);
                }
            };

            String currentCommand = null;

            for (String line : content.split("\\R", -1)) {
                //line = line.stripLeading();
                if (line.startsWith("--")) {
                    if (currentCommand != null) {
                        putCommand.accept(currentCommand);
                    }
                    currentCommand = line.substring(2).trim();
                    sqlBlock.setLength(0);
                }
                else if (currentCommand != null) {
                    sqlBlock.append(line).append('\n');
                }
            }

            if (currentCommand != null) {
                putCommand.accept(currentCommand);
            }

            return commands.entrySet().stream()
                    .filter(entry -> !entry.getValue().isBlank())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        }
    }



}
