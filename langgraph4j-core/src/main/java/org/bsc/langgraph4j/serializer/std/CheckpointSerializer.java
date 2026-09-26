package org.bsc.langgraph4j.serializer.std;

import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public  record CheckpointSerializer(
        StdStateSerializer<AgentState> stateSerializer) implements NullableObjectSerializer<Checkpoint> {

    @Override
    public void write(Checkpoint object, ObjectOutput out) throws IOException {
        StdSerializer.writeUTF(object.getId(), out);
        writeNullableUTF(object.getNodeId(), out);
        writeNullableUTF(object.getNextNodeId(), out);
        AgentState state = stateSerializer.stateFactory().apply(object.getState());
        stateSerializer.write( state, out);
    }

    @Override
    public Checkpoint read(ObjectInput in) throws IOException, ClassNotFoundException {
        return Checkpoint.builder()
                .id(StdSerializer.readUTF(in))
                .nodeId(readNullableUTF(in).orElse(null))
                .nextNodeId(readNullableUTF(in).orElse(null))
                .state(stateSerializer.read(in))
                .build();
    }

}
