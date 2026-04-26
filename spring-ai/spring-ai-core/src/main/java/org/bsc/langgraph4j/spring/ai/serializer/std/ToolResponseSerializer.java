package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

class ToolResponseSerializer implements NullableObjectSerializer<ToolResponseMessage.ToolResponse> {

    @Override
    public void write(ToolResponseMessage.ToolResponse object, ObjectOutput out) throws IOException {
        writeNullableUTF(object.id(), out);
        writeNullableUTF(object.name(), out);
        writeNullableUTF(object.responseData(), out);
    }

    @Override
    public ToolResponseMessage.ToolResponse read(ObjectInput in) throws IOException, ClassNotFoundException {

        final var id = readNullableUTF(in).orElse("");
        final var name = readNullableUTF(in).orElse("");
        final var responseData = readNullableUTF(in).orElse("");

        return new ToolResponseMessage.ToolResponse(
                id,
                name,
                responseData
        );
    }

}