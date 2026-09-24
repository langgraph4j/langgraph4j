package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.StdSerializer;
import org.springframework.ai.chat.messages.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MessageSerializer implements StdSerializer<Message> {
    final UserMessageSerializer user = new UserMessageSerializer();
    final AssistantMessageSerializer assistant = new AssistantMessageSerializer();
    final SystemMessageSerializer system = new SystemMessageSerializer();
    final ToolResponseMessageSerializer tool = new ToolResponseMessageSerializer();

    @Override
    public void write(Message object, ObjectOutput out) throws IOException {
        out.writeObject(object.getMessageType());

        switch( object.getMessageType() ) {
            case USER -> user.write( (UserMessage)object, out );
            case ASSISTANT -> assistant.write( (AssistantMessage)object, out );
            case SYSTEM -> system.write( (SystemMessage) object, out );
            case TOOL -> tool.write( (ToolResponseMessage) object, out );
            default -> throw new IllegalArgumentException("Unsupported message type: " + object.getMessageType());
        }
    }

    @Override
    public Message read(ObjectInput in) throws IOException, ClassNotFoundException {

        MessageType type = (MessageType) in.readObject();

       return  switch (type) {
            case ASSISTANT -> assistant.read(in);
            case USER -> user.read(in);
            case SYSTEM -> system.read(in);
            case TOOL -> tool.read(in);
        };
    }

}
