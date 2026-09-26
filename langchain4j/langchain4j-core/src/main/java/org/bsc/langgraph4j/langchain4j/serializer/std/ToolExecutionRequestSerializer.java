package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.bsc.langgraph4j.serializer.std.StdSerializer;
import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * The ToolExecutionRequestSerializer class implements the Serializer interface
 * for the ToolExecutionRequest type. It provides methods to serialize and 
 * deserialize ToolExecutionRequest objects.
 */
public class ToolExecutionRequestSerializer implements NullableObjectSerializer<ToolExecutionRequest> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ToolExecutionResultMessageSerializer.class);

    /**
     * Serializes the given ToolExecutionRequest object to the provided ObjectOutput.
     *
     * @param object the ToolExecutionRequest object to serialize
     * @param out the ObjectOutput to write the serialized data to
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void write(ToolExecutionRequest object, ObjectOutput out) throws IOException {
        if( object.id() == null ) {
            log.trace( "ToolExecutionRequest id is null!" );
        }
        writeNullableUTF(object.id(), out);
        StdSerializer.writeUTF( object.name(), out );
        StdSerializer.writeUTF( object.arguments(), out );
    }

    /**
     * Deserializes a ToolExecutionRequest object from the provided ObjectInput.
     *
     * @param in the ObjectInput to read the serialized data from
     * @return the deserialized ToolExecutionRequest object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    @Override
    public ToolExecutionRequest read(ObjectInput in) throws IOException, ClassNotFoundException {
        return ToolExecutionRequest.builder()
                .id( readNullableUTF(in).orElse(null) )
                .name(StdSerializer.readUTF(in))
                .arguments(StdSerializer.readUTF(in))
                .build();
    }
}
