package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.Serializer;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;

class ToolResponseMessageSerializer implements Serializer<ToolResponseMessage> {

    @Override
    public void write(ToolResponseMessage object, ObjectOutput out) throws IOException {
        out.writeObject( object.getResponses() );
        out.writeObject( object.getMetadata() );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResponseMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        var response = (List<ToolResponseMessage.ToolResponse>)in.readObject();
        var metadata = (Map<String,Object>)in.readObject();
        return ToolResponseMessage.builder().responses(response).metadata(metadata).build();
    }
}
