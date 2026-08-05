package org.bsc.langgraph4j.langchain4j.serializer.std;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;


/**
 * The AiMessageSerializer class implements the Serializer interface for the AiMessage type.
 * It provides methods to serialize and deserialize AiMessage objects.
 */
public class AiMessageSerializer implements NullableObjectSerializer<AiMessage> {
    
    /**
     * Serializes the given AiMessage object to the specified output stream.
     *
     * @param object the AiMessage object to serialize
     * @param out the output stream to write the serialized object to
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void write(AiMessage object, ObjectOutput out) throws IOException {
        boolean hasToolExecutionRequests = object.hasToolExecutionRequests();

        writeNullableUTF( object.thinking(), out );
        out.writeObject( object.attributes() );
        out.writeBoolean( hasToolExecutionRequests );

        if( hasToolExecutionRequests ) {
            out.writeObject( object.toolExecutionRequests() );
        }

        // text and tool execution requests are NOT mutually exclusive (e.g. Gemini and other
        // providers can return both in the same turn) — always write text, regardless of whether
        // tool execution requests are also present.
        writeNullableUTF( object.text(), out );
    }

    /**
     * Deserializes an AiMessage object from the specified input stream.
     *
     * @param in the input stream to read the serialized object from
     * @return the deserialized AiMessage object
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    @Override
    @SuppressWarnings("unchecked")
    public AiMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        final var builder = AiMessage.builder();

        readNullableUTF(in).ifPresent( builder::thinking );
        if( in.readObject() instanceof Map<?,?> attributes ) {
            builder.attributes( (Map<String, Object>)attributes );
        }

        boolean hasToolExecutionRequests = in.readBoolean();
        if( hasToolExecutionRequests ) {
            List<ToolExecutionRequest> toolExecutionRequests = (List<ToolExecutionRequest>)in.readObject();
            builder.toolExecutionRequests( toolExecutionRequests );
        }

        readNullableUTF(in).ifPresent( builder::text );

        return builder.build();
    }
}
