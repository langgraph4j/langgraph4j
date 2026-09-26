package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.serializer.std.StdSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * The SystemMessageSerializer class implements the Serializer interface for the SystemMessage type.
 * It provides methods to serialize and deserialize SystemMessage objects.
 */
public class SystemMessageSerializer implements StdSerializer<SystemMessage> {
    
    /**
     * Serializes the given SystemMessage object to the specified ObjectOutput stream.
     *
     * @param object the SystemMessage object to serialize
     * @param out the ObjectOutput stream to write the serialized object to
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void write(SystemMessage object, ObjectOutput out) throws IOException {
        StdSerializer.writeUTF(object.text(), out);
    }

    /**
     * Deserializes a SystemMessage object from the specified ObjectInput stream.
     *
     * @param in the ObjectInput stream to read the serialized object from
     * @return the deserialized SystemMessage object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    @Override
    public SystemMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        String text = StdSerializer.readUTF(in);
        return SystemMessage.systemMessage(text);
    }
}
