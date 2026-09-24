package org.bsc.langgraph4j.serializer.plain_text.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class JacksonCheckpointListSerializer implements Serializer<LinkedList<Checkpoint>> {

    protected final JsonMapper objectMapper;

    public <State extends AgentState> JacksonCheckpointListSerializer(JacksonStateSerializer<State>  stateSerializer ) {
        final var checkpointHandler = new CheckpointHandler( stateSerializer );

        final var typeMapper = new TypeMapper();
        typeMapper.register( new TypeMapper.Reference<Checkpoint>( Checkpoint.class.getName()) {} );

        final var module = new SimpleModule();
        module.addSerializer( Checkpoint.class,  checkpointHandler.serializer() );
        module.addDeserializer( Checkpoint.class, checkpointHandler.deserializer() );
        module.addDeserializer( List.class, new GenericListDeserializer(typeMapper));
        this.objectMapper = JsonMapper.builder()
                .enable( StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION )
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .addModule( module )
                .build();
    }


    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String writeDataAsString(LinkedList<Checkpoint> data) throws IOException {
        return objectMapper.writeValueAsString(data);
    }

    @Override
    public LinkedList<Checkpoint> readDataFromString(String string) throws IOException {
        final var list =  objectMapper.readValue(string, new TypeReference<List<Checkpoint>>() {} );
        return new LinkedList<>(list);
    }

}
