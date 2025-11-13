package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;

class AssistantMessageSerializer implements NullableObjectSerializer<AssistantMessage> {



    @Override
    public void write(AssistantMessage object, ObjectOutput out) throws IOException {
        writeNullableUTF( object.getText(), out );
        out.writeObject(object.getMetadata());
        writeNullableObject(object.getToolCalls(), out);
        // out.writeObject(object.getMedia());
    }

    @Override
    @SuppressWarnings("unchecked")
    public AssistantMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        var text = readNullableUTF(in).orElse(null);
        var metadata = (Map<String, Object>)in.readObject();
        var toolCalls = (List<AssistantMessage.ToolCall>)readNullableObject(in).orElseGet(List::of);
        return AssistantMessage.builder()
            .content(text)
            .properties(metadata)
            .toolCalls(toolCalls)
            .build();
    }
}