package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * This class is responsible for serializing and deserializing
 * instances of ToolExecutionResultMessage. It implements the
 * Serializer interface to provide custom serialization logic.
 */
public class ToolExecutionResultMessageSerializer implements NullableObjectSerializer<ToolExecutionResultMessage> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ToolExecutionResultMessageSerializer.class);
    /**
     * Serializes the given ToolExecutionResultMessage object to the
     * provided ObjectOutput stream.
     *
     * @param object the ToolExecutionResultMessage object to serialize
     * @param out the ObjectOutput stream to write the serialized data to
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void write(ToolExecutionResultMessage object, ObjectOutput out) throws IOException {
        if( object.id() == null ) {
            log.trace( "ToolExecutionResultMessage id is null!" );
        }
        writeNullableUTF( object.id(), out );
        Serializer.writeUTF( object.toolName(), out );
        Serializer.writeUTF( object.text(), out );
        out.writeBoolean( ofNullable(object.isError()).orElse(false) );
        out.writeObject( object.attributes() );
    }

    /**
     * Deserializes a ToolExecutionResultMessage object from the
     * provided ObjectInput stream.
     *
     * @param in the ObjectInput stream to read the serialized data from
     * @return the deserialized ToolExecutionResultMessage object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object
     *         cannot be found
     */
    @Override
    public ToolExecutionResultMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        String id = readNullableUTF( in ).orElse( null );
        String toolName = Serializer.readUTF(in);
        String text = Serializer.readUTF(in);
        Boolean isError = in.readBoolean();
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) in.readObject();
        return ToolExecutionResultMessage.builder()
                .id( id )
                .toolName( toolName )
                .text( text )
                .isError( isError )
                .attributes( attributes )
                .build();
    }
}
