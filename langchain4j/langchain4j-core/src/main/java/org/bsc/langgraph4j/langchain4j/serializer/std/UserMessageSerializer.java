package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;

/**
 * The UserMessageSerializer class implements the NullableObjectSerializer interface for the UserMessage type.
 * It provides methods to serialize and deserialize UserMessage objects.
 */
public class UserMessageSerializer implements NullableObjectSerializer<UserMessage> {

    /**
     * Serializes the given UserMessage object to the specified ObjectOutput.
     *
     * @param object the UserMessage object to serialize
     * @param out the ObjectOutput to write the serialized data to
     * @throws IOException if an I/O error occurs during serialization
     * @throws IllegalArgumentException if the content type of the UserMessage is unsupported
     */
    @Override
    public void write(UserMessage object, ObjectOutput out) throws IOException {

        if( object.hasSingleText() ) {
            Serializer.writeUTF( object.singleText(), out );
        }
        else {
            out.writeObject( object.contents() );
        }
        writeNullableUTF( object.name(), out);
        out.writeObject( object.attributes() );
    }

    /**
     * Deserializes a UserMessage object from the specified ObjectInput.
     *
     * @param in the ObjectInput to read the serialized data from
     * @return the deserialized UserMessage object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    @Override
    public UserMessage read(ObjectInput in) throws IOException, ClassNotFoundException {

        final var builder = UserMessage.builder();
        try {
            final var text = Serializer.readUTF(in);
            builder.addContent(TextContent.from(text));
        }
        catch( EOFException ex ) {
            // This exception is managed to keep backward compatibility

            @SuppressWarnings("unchecked")
            final var contents = (List<Content>)in.readObject();
            builder.contents(contents);
        }
        readNullableUTF(in).ifPresent(builder::name);

        @SuppressWarnings("unchecked")
        final var attributes = (Map<String, Object>) in.readObject();
        return builder.attributes(attributes).build();
    }
}
