package org.bsc.langgraph4j.serializer;

import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.StdStateSerializer;
import org.bsc.langgraph4j.state.AgentState;

import java.util.LinkedList;

public interface CheckpointListSerializer {

    static Serializer<LinkedList<Checkpoint>> of( StateSerializer<? extends AgentState> stateSerializer) {
        if (stateSerializer instanceof StdStateSerializer<?> stdStateSerializer) {
            return new org.bsc.langgraph4j.serializer.std.StdCheckpointListSerializer(stdStateSerializer);
        } else if (stateSerializer instanceof JacksonStateSerializer<?> jacksonStateSerializer) {
            return new org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer(jacksonStateSerializer);
        } else {
            throw new IllegalArgumentException("Unsupported StateSerializer type: " + stateSerializer.getClass().getName());
        }
    }
}
