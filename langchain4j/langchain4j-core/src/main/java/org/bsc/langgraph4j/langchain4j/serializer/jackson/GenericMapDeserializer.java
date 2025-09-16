package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.ChatMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class GenericMapDeserializer extends StdDeserializer<Map<String, Object>> {

    public GenericMapDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        var mapper = (ObjectMapper) p.getCodec();
        ObjectNode node = mapper.readTree(p);

        Map<String, Object> result = new HashMap<>();

        for( var property : node.properties() ) {

            String key = property.getKey();
            JsonNode valueNode = property.getValue();

            // Example: Detect type based on field name or value structure
            Object value;
            if (valueNode.isObject() && valueNode.has("@type")) {
                // Deserialize to a specific class
                value = mapper.treeToValue(valueNode, ChatMessage.class);
            } else if (valueNode.isInt()) {
                value = valueNode.intValue();
            } else if (valueNode.isTextual()) {
                value = valueNode.textValue();
            } else {
                // Fallback generic deserialization
                value = mapper.treeToValue(valueNode, Object.class);
            }

            result.put(key, value);
        }

        return result;
    }
}
