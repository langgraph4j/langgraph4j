package org.bsc.langgraph4j.serializer.plain_text.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.state.AgentState;

import java.io.IOException;

public record CheckpointHandler(StdSerializer<Checkpoint> serializer, StdDeserializer<Checkpoint>  deserializer )  {

    public <State extends AgentState> CheckpointHandler(JacksonStateSerializer<State>  stateSerializer ) {
            this( new Serializer<>(stateSerializer), new Deserializer<>(stateSerializer) );
    }

    private static class Serializer<State extends AgentState> extends StdSerializer<Checkpoint> {
        final JacksonStateSerializer<State> stateSerializer;

        public Serializer( JacksonStateSerializer<State> stateSerializer ) {
            super(Checkpoint.class);
            this.stateSerializer = stateSerializer;
        }

        @Override
        public void serialize(Checkpoint cp, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", Checkpoint.class.getName());
            gen.writeStringField( "id", cp.id());
            gen.writeStringField("nodeId", cp.nodeId());
            gen.writeStringField("nextNodeId", cp.nextNodeId());
            gen.writeFieldName("state");
            gen.writeRawValue( stateSerializer.writeDataAsString( cp.state()) );
            gen.writeEndObject();
        }
    }

    private static class Deserializer<State extends AgentState>  extends StdDeserializer<Checkpoint> {
        final JacksonStateSerializer<State> stateSerializer;

        protected Deserializer( JacksonStateSerializer<State> stateSerializer ) {
            super(Checkpoint.class);
            this.stateSerializer = stateSerializer;
        }

        @Override
        public Checkpoint deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
            final ObjectNode root = jsonParser.getCodec().readTree(jsonParser);

            final var resultBuilder = Checkpoint.builder();

            resultBuilder.id( root.get("id").asText() );
            resultBuilder.nodeId( root.get("nodeId").asText() );
            resultBuilder.nextNodeId( root.get("nextNodeId").asText() );

            final var stateJson = root.get("state").toString();
            resultBuilder.state( stateSerializer.readDataFromString( stateJson ) );

            /*
            try(JsonParser stateParser = root.get("state").traverse(jsonParser.getCodec()) ) {
                stateParser.nextToken(); // advance to START_OBJECT
                final var text = stateParser.getText();
                resultBuilder.state( stateSerializer.readDataFromString( text ) );
            }
            */
            return resultBuilder.build();
        }

    }

}
