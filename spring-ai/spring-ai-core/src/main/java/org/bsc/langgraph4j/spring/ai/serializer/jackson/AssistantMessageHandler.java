package org.bsc.langgraph4j.spring.ai.serializer.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.IOException;
import java.util.LinkedList;

import static org.bsc.langgraph4j.spring.ai.serializer.jackson.SerializationHelper.deserializeMetadata;
import static org.bsc.langgraph4j.spring.ai.serializer.jackson.SerializationHelper.serializeMetadata;

public interface AssistantMessageHandler {

    enum Field {
        TEXT("text"),
        TOOL_CALLS("toolCalls"),
        MEDIA("media");

        final String name;

        Field(String name ) {
            this.name = name;
        }
    }

    class Serializer extends StdSerializer<AssistantMessage> {


        public Serializer() {
            super(AssistantMessage.class);
        }

        @Override
        public void serialize(AssistantMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", msg.getMessageType().name());
            gen.writeStringField(Field.TEXT.name, msg.getText());
            gen.writeObjectField( Field.TOOL_CALLS.name, msg.getToolCalls() );

            serializeMetadata( gen, msg.getMetadata() );

            gen.writeEndObject();
        }
    }


    class Deserializer extends StdDeserializer<AssistantMessage> {

        protected Deserializer() {
            super(AssistantMessage.class);
        }

        @Override
        public AssistantMessage deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
            var mapper = (ObjectMapper) jsonParser.getCodec();
            ObjectNode node = mapper.readTree(jsonParser);

            var text = node.findValue(Field.TEXT.name).asText();
            var metadata = deserializeMetadata(mapper, node);
            var requestsNode = node.findValue(Field.TOOL_CALLS.name);

            if (requestsNode.isNull() || requestsNode.isEmpty()) {
                return AssistantMessage.builder()
                        .content(text)
                        .properties(metadata)
                        .build();
            }

            var requests = new LinkedList<AssistantMessage.ToolCall>();

            for (JsonNode requestNode : requestsNode) {
                var request = mapper.treeToValue(requestNode,
                        new TypeReference<AssistantMessage.ToolCall>() {
                        });

                requests.add(request);
            }

            return AssistantMessage.builder()
                    .properties(metadata)
                    .content(text)
                    .toolCalls(requests)
                    .build();
        }

    }


    class ToolCallSerializer extends StdSerializer<AssistantMessage.ToolCall> {
        protected ToolCallSerializer() {
            super(AssistantMessage.ToolCall.class);
        }

        @Override
        public void serialize(AssistantMessage.ToolCall toolCall, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", AssistantMessage.ToolCall.class.getName());
            gen.writeStringField("id", toolCall.id());
            gen.writeStringField("type", toolCall.type());
            gen.writeStringField("name", toolCall.name());
            gen.writeStringField("arguments", toolCall.arguments());
            gen.writeEndObject();
        }

    }

    class ToolCallDeserializer extends StdDeserializer<AssistantMessage.ToolCall> {
        protected ToolCallDeserializer() {
            super(AssistantMessage.ToolCall.class);
        }

        @Override
        public AssistantMessage.ToolCall deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
            var mapper = (ObjectMapper) jsonParser.getCodec();
            JsonNode node = mapper.readTree(jsonParser);

            return new AssistantMessage.ToolCall(
                    node.get("id").asText(),
                    node.get("type").asText(),
                    node.get("name").asText(),
                    node.get("arguments").asText()
            );
        }

    }
}
