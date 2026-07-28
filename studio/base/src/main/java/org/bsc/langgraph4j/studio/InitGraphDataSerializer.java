package org.bsc.langgraph4j.studio;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;

import java.io.IOException;

class InitGraphDataSerializer extends StdSerializer<InitGraphData> {
    Logger log = LangGraphStudioServer.log;

    protected InitGraphDataSerializer(Class<InitGraphData> t) {
        super(t);
    }

    /**
     * Serializes the InitData object to JSON.
     *
     * @param initData the InitData object to serialize.
     * @param jsonGenerator the JSON generator.
     * @param serializerProvider the serializer provider.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void serialize(InitGraphData initData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        log.trace("InitDataSerializer start!");
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("id", initData.id());
        jsonGenerator.writeStringField("graph", initData.diagram());
        jsonGenerator.writeStringField("title", initData.title());
        jsonGenerator.writeObjectField("args", initData.args());

        jsonGenerator.writeArrayFieldStart("threads");
        for (var thread : initData.threads()) {
            jsonGenerator.writeStartArray();
            jsonGenerator.writeString(thread.id());
            jsonGenerator.writeStartArray(thread.entries());
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndArray();
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }
}
