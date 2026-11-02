package org.bsc.langgraph4j.serializer.std;

import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Instant;

public  record CheckpointSerializer(
        StdStateSerializer<AgentState> stateSerializer) implements NullableObjectSerializer<Checkpoint> {

    @Override
    public void write(Checkpoint object, ObjectOutput out) throws IOException {
        StdSerializer.writeUTF(object.id(), out);
        writeNullableUTF(object.nodeId(), out);
        writeNullableUTF(object.nextNodeId(), out);
        AgentState state = stateSerializer.stateFactory().apply(object.state());
        stateSerializer.write( state, out);
        // null here means the storage never supplied a time, written as absent so it reads back as null
        writeNullableUTF( object.savedAt() != null ? object.savedAt().toString() : null, out );
    }

    @Override
    public Checkpoint read(ObjectInput in) throws IOException, ClassNotFoundException {
        return Checkpoint.builder()
                .id(StdSerializer.readUTF(in))
                .nodeId(readNullableUTF(in).orElse(null))
                .nextNodeId(readNullableUTF(in).orElse(null))
                .state(stateSerializer.read(in))
                .savedAt(readNullableUTF(in).map(Instant::parse).orElse(null))
                .build();
    }

}
