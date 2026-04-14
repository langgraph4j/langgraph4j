package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.io.IOException;

public interface ToolExecutionRequestHandler {

    class Serializer extends StdSerializer<ToolExecutionRequest> {

        public Serializer() {
            super(ToolExecutionRequest.class);
        }

        @Override
        public void serialize(ToolExecutionRequest msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", msg.getClass().getName());
            if( msg.id() == null )
                gen.writeNullField( "id");
            else
                gen.writeStringField("id", msg.id());
            gen.writeStringField("name", msg.name());
            gen.writeStringField("arguments", msg.arguments());
            gen.writeEndObject();
        }
    }

    class Deserializer extends StdDeserializer<ToolExecutionRequest> {

        protected Deserializer() {
            super(ToolExecutionRequest.class);
        }

        /**
         * Deserializes a JSON representation of a ToolExecutionRequest.
         *
         * @param parser the JsonParser used to read the JSON data
         * @param ctx the DeserializationContext that can be used to access additional information during deserialization
         * @return a ToolExecutionRequest object populated with data from the JSON
         * @throws IOException if there is an issue reading the JSON data
         * @throws JacksonException if there is a problem with Jackson processing
         */
        @Override
        public ToolExecutionRequest deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JacksonException {
            final JsonNode node =  parser.getCodec().readTree(parser);
            final var idNode = node.get("id");
            return dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                    .id( idNode.isNull() ? null : idNode.asText() )
                    .name(node.get("name").asText())
                    .arguments(node.get("arguments").asText())
                    .build();
        }


    }

}
