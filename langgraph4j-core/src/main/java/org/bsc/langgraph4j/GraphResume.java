package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record GraphResume(
        Map<String,Object> value,
        Checkpoint checkpoint
) implements GraphInput {
    public GraphResume {
        requireNonNull( value, "value cannot be null");
    }
    public GraphResume() {this(Map.of(), null); }
    public GraphResume( Map<String,Object> value ) {this(value, null); }
    public GraphResume( Checkpoint checkpoint ) {
        this(Map.of(), checkpoint);
    }
    public GraphResume( Checkpoint checkpoint, Map<String,Object> value ) {
        this(value, checkpoint);
    }

    @Override
    public String toString() {
        return "GraphResume{ %s, %s }".formatted(
                        CollectionsUtils.toString(value),
                        checkpoint
                );

    }
}
