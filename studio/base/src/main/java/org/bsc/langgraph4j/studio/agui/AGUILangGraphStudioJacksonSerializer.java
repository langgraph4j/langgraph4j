package org.bsc.langgraph4j.studio.agui;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.studio.InitGraphData;
import org.bsc.langgraph4j.studio.serializer.InitGraphDataSerializer;
import org.bsc.langgraph4j.studio.serializer.NodeOutputSerializer;

public class AGUILangGraphStudioJacksonSerializer extends com.agui.json.AGUIJacksonSerializer {

    public AGUILangGraphStudioJacksonSerializer() {
        super();

        final var module = new SimpleModule();
        module.addSerializer(InitGraphData.class, new InitGraphDataSerializer());
        module.addSerializer(NodeOutput.class, new NodeOutputSerializer());
        objectMapper.registerModule(module);

    }
}
