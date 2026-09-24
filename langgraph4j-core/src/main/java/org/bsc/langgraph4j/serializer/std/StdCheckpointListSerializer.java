package org.bsc.langgraph4j.serializer.std;

import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedList;

public class StdCheckpointListSerializer implements StdSerializer<LinkedList<Checkpoint>> {

    private final CheckpointSerializer serializer;

    @SuppressWarnings("unchecked")
    public <State extends AgentState> StdCheckpointListSerializer(StdStateSerializer<State> stateSerializer) {
        this.serializer = new CheckpointSerializer((StdStateSerializer<AgentState>) stateSerializer);
    }

    @Override
    public void write(LinkedList<Checkpoint> checkpoints, ObjectOutput out) throws IOException {
        out.writeInt(checkpoints.size());
        for (var checkpoint : checkpoints) {
            serializer.write(checkpoint, out);
        }
    }

    @Override
    public LinkedList<Checkpoint> read(ObjectInput in) throws IOException, ClassNotFoundException {
        final var result = new LinkedList<Checkpoint>();

        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            result.add(serializer.read(in));
        }

        return result;
    }
}
