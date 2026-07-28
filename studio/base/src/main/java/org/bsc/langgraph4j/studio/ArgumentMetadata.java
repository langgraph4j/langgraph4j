package org.bsc.langgraph4j.studio;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Metadata for an argument in a request.
 *
 * @param name the name of the argument.
 * @param type the type of the argument.
 * @param required whether the argument is required.
 */
public record ArgumentMetadata(
        String name,
        ArgumentType type,
        boolean required,
        @JsonIgnore Function<Object,Object> converter
) {
    public ArgumentMetadata {
        requireNonNull(name, "name cannot be null");
        requireNonNull(type, "type cannot be null");
    }
    public ArgumentMetadata(String name, ArgumentType type, boolean required) {
        this(name, type, required, null);
    }

    public enum ArgumentType { STRING, IMAGE };
}
