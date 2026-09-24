package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.data.message.*;

import org.bsc.langgraph4j.serializer.std.StdSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * The ChatMesssageSerializer class is responsible for serializing and deserializing 
 * ChatMessage objects. It implements the Serializer interface for the ChatMessage type.
 */
public class ChatMesssageSerializer implements StdSerializer<ChatMessage> {
    final AiMessageSerializer ai = new AiMessageSerializer();
    final UserMessageSerializer user = new UserMessageSerializer();
    final SystemMessageSerializer system = new SystemMessageSerializer();
    final ToolExecutionResultMessageSerializer toolExecutionResult = new ToolExecutionResultMessageSerializer();

    /**
     * Serializes the given ChatMessage object to the specified output stream.
     *
     * @param object the ChatMessage object to serialize
     * @param out the output stream to write the serialized object to
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void write(ChatMessage object, ObjectOutput out) throws IOException {
        out.writeObject(object.type());
        switch (object.type()) {
            case AI:
                ai.write((AiMessage) object, out);
                break;
            case USER:
                user.write((UserMessage) object, out);
                break;
            case SYSTEM:
                system.write((SystemMessage) object, out);
                break;
            case TOOL_EXECUTION_RESULT:
                toolExecutionResult.write((ToolExecutionResultMessage) object, out);
        }
    }

    /**
     * Deserializes a ChatMessage object from the specified input stream.
     *
     * @param in the input stream to read the serialized object from
     * @return the deserialized ChatMessage object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    @Override
    public ChatMessage read(ObjectInput in) throws IOException, ClassNotFoundException {

        ChatMessageType type = (ChatMessageType) in.readObject();

        switch (type) {
            case AI:
                return ai.read(in);
            case USER:
                return user.read(in);
            case SYSTEM:
                return system.read(in);
            case TOOL_EXECUTION_RESULT:
                return toolExecutionResult.read(in);
        }

        throw new IllegalArgumentException("Unsupported chat message type: " + type);
    }
}
