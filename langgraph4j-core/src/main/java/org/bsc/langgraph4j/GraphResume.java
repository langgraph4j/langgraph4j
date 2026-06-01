package org.bsc.langgraph4j;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record GraphResume(
        Map<String,Object> value
) implements GraphInput {
    public GraphResume {
        requireNonNull( value, "value cannot be null");
    }
    public GraphResume() {this(Map.of()); }

}
