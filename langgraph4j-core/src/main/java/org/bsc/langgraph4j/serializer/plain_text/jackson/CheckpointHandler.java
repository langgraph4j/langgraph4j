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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;

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
            writeSavedAt(cp, gen);
            gen.writeFieldName("state");
            gen.writeRawValue( stateSerializer.writeDataAsString( cp.state()) );
            gen.writeEndObject();
        }

        /**
         * Writes the saved-at time, or nothing at all when the storage did not
         * supply one, so that a payload can always be read back into a checkpoint
         * whose saved-at time is {@code null}.
         */
        private void writeSavedAt(Checkpoint cp, JsonGenerator gen) throws IOException {
            final var savedAt = cp.savedAt();
            if( savedAt == null ) {
                gen.writeNullField("savedAt");
                return;
            }
            gen.writeStringField("savedAt", savedAt.toString());
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

            // savedAt is optional, and payloads written before the rename still carry it
            // under its former name, so read both before falling back to the builder default.
            final var savedAt = readSavedAt(root);
            if( savedAt != null ) {
                resultBuilder.savedAt( savedAt );
            }

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

        /**
         * Reads the saved-at time, preferring the current name and falling back to
         * {@code createdAt}, the name it carried in 1.9.6 and 1.9.7. Returns null
         * when neither is present or usable, which leaves the builder to decide.
         */
        private @Nullable Instant readSavedAt(ObjectNode root) {
            var savedAt = root.get("savedAt");
            if( savedAt == null || savedAt.isNull() ) {
                savedAt = root.get("createdAt");
            }
            if( savedAt == null || savedAt.isNull() ) {
                return null;
            }
            return Instant.parse( savedAt.asText() );
        }

    }

}
