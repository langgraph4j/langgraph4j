package org.bsc.langgraph4j.spring.ai.serializer.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.spring.ai.serializer.jackson.SerializationHelper.deserializeMetadata;
import static org.bsc.langgraph4j.spring.ai.serializer.jackson.SerializationHelper.serializeMetadata;

public interface ToolResponseMessageHandler {
    enum Field {
        RESPONSES("responses")
        ;

        final String name;

        Field(String name ) {
            this.name = name;
        }
    }

    class Serializer extends StdSerializer<ToolResponseMessage> {

        public Serializer() {
            super(ToolResponseMessage.class);
        }

        @Override
        public void serialize(ToolResponseMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();

            gen.writeStringField("@type", msg.getMessageType().name());
            gen.writeObjectField( Field.RESPONSES.name, msg.getResponses() );

            serializeMetadata( gen, msg.getMetadata() );

            gen.writeEndObject();
        }

    }

    class Deserializer extends StdDeserializer<ToolResponseMessage> {

        public Deserializer() {
            super(ToolResponseMessage.class);
        }

        @Override
        public ToolResponseMessage deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            var mapper = (ObjectMapper) jsonParser.getCodec();
            ObjectNode node = mapper.readTree(jsonParser);

            var responsesNode = node.findValue( Field.RESPONSES.name);
            var metadata = deserializeMetadata( mapper, node );

            if( responsesNode.isNull() || responsesNode.isEmpty() ) {
                return ToolResponseMessage.builder()
                        .responses( List.of() )
                        .metadata(metadata)
                        .build();
            }

            var responses = new ArrayList<ToolResponseMessage.ToolResponse>( responsesNode.size() );
            for( var responseNode : responsesNode ) {
                responses.add( mapper.treeToValue( responseNode, ToolResponseMessage.ToolResponse.class ) );
            }

            return ToolResponseMessage.builder()
                    .responses( responses )
                    .metadata(metadata)
                    .build();
        }
    }

    class ToolResponseSerializer extends StdSerializer<ToolResponseMessage.ToolResponse> {
        protected ToolResponseSerializer() {
            super(ToolResponseMessage.ToolResponse.class);
        }

        @Override
        public void serialize(ToolResponseMessage.ToolResponse toolResponse, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", ToolResponseMessage.ToolResponse.class.getName());
            gen.writeStringField("id", toolResponse.id());
            gen.writeStringField("name", toolResponse.name());
            gen.writeStringField("responseData", toolResponse.responseData());
            gen.writeEndObject();
        }

    }

    class ToolResponseDeserializer extends StdDeserializer<ToolResponseMessage.ToolResponse> {
        protected ToolResponseDeserializer() {
            super(ToolResponseMessage.ToolResponse.class);
        }

        @Override
        public ToolResponseMessage.ToolResponse deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
            var mapper = (ObjectMapper) jsonParser.getCodec();
            JsonNode node = mapper.readTree(jsonParser);

            return new ToolResponseMessage.ToolResponse(
                    node.get("id").asText(),
                    node.get("name").asText(),
                    node.get("responseData").asText()
            );
        }

    }

}
