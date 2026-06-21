package org.bsc.langgraph4j.serializer.plain_text.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base Implementation of {@link PlainTextStateSerializer} using Jackson library.
 * Need to be extended from specific state implementation
 *
 * @param <State> The type of the agent state to be serialized/deserialized.
 */
public abstract class JacksonStateSerializer <State extends AgentState> extends PlainTextStateSerializer<State> {
    protected final ObjectMapper objectMapper;

    protected TypeMapper typeMapper = new TypeMapper();
    private final Map<String, Object> transientData = new HashMap<>(8);

    protected JacksonStateSerializer( AgentStateFactory<State> stateFactory ) {
        this( stateFactory, new ObjectMapper() );

    }

    protected JacksonStateSerializer( AgentStateFactory<State> stateFactory, ObjectMapper objectMapper) {
        super(stateFactory);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        final var module = new SimpleModule();
        module.addDeserializer( Map.class, new GenericMapDeserializer(typeMapper) );
        module.addDeserializer( List.class, new GenericListDeserializer(typeMapper) );

        this.objectMapper.registerModule( module );

    }

    public TypeMapper typeMapper() {
        return typeMapper;
    }
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    public final String writeDataAsString(Map<String, Object> data) throws IOException {

        final Map<String,Object> serializedData;

        if( transientAttributeSet.isEmpty() ) {
            serializedData = data;
        } else {
            serializedData = new HashMap<>(data);

            for( String key : transientAttributeSet ) {
                if( serializedData.containsKey(key) ) {
                    transientData.put(key, serializedData.remove(key));
                }
            }
        }

        return objectMapper.writeValueAsString(serializedData);
    }

    @Override
    public final Map<String, Object> readDataFromString(String string) throws IOException {
        final var data =  objectMapper.readValue(string, new TypeReference<Map<String, Object>>() {});
        for( String key : transientAttributeSet ) {
            if( transientData.containsKey(key) ) {
                data.put(key, transientData.get(key));
            }
        }
        return data;
    }

}
