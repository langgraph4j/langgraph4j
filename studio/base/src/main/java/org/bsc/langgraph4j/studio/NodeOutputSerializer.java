package org.bsc.langgraph4j.studio;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.bsc.langgraph4j.subgraph.SubGraphOutput;
import org.slf4j.Logger;

import java.io.IOException;

import static java.lang.String.format;

/**
 * Serializer for NodeOutput objects, extending the StdSerializer class.
 * This class is responsible for converting NodeOutput instances into JSON format.
 */
@SuppressWarnings("rawtypes")
class NodeOutputSerializer extends StdSerializer<NodeOutput> {
    Logger log = LangGraphStudioServer.log;

    /**
     * Constructs a new NodeOutputSerializer.
     * Calls the superclass constructor with the NodeOutput class type.
     */
    protected NodeOutputSerializer() {
        super( NodeOutput.class );
    }

    /**
     * Serializes a NodeOutput instance into JSON.
     *
     * @param nodeOutput the NodeOutput instance to serialize
     * @param gen the JsonGenerator used to write JSON
     * @param serializerProvider the provider that can be used to get serializers for other types
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void serialize(NodeOutput nodeOutput, JsonGenerator gen, SerializerProvider serializerProvider) throws
            IOException {
        log.trace( "NodeOutputSerializer start! {}", nodeOutput.getClass() );
        gen.writeStartObject();
        if( nodeOutput instanceof StateSnapshot<?> snapshot) {
            var checkpoint = snapshot.config().checkPointId();
            log.trace( "checkpoint: {}", checkpoint );
            if( checkpoint.isPresent() ) {
                gen.writeStringField("checkpoint", checkpoint.get());
            }
        }

        if( nodeOutput instanceof SubGraphOutput<?> subgraph) {
            gen.writeStringField("node", format( "%s_%s", subgraph.node(), subgraph.subGraphId() ));
        }
        else {
            gen.writeStringField("node", nodeOutput.node());

        }

        // serializerProvider.defaultSerializeField("state", nodeOutput.state().data(), gen);

        gen.writeObjectField("state", nodeOutput.state().data());

        if( nodeOutput instanceof StateSnapshot<?> snapshot ) {
            gen.writeObjectField("next", snapshot.next() );
        }
        gen.writeEndObject();
    }
}
