package org.bsc.langgraph4j.serializer.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.TypeMapper;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonSerializerTest {

    static class State extends AgentState {

        /**
         * needed for Jackson deserialization unless use a custom deserializer
         */
        protected State() {
            super( Map.of() );
        }

        /**
         * Constructs an AgentState with the given initial data.
         *
         * @param initData the initial data for the agent state
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    static class MyStateSerializer extends JacksonStateSerializer<State> {
        public MyStateSerializer() {
            super(State::new);
        }

    }

    static class MyJacksonStateSerializer extends JacksonStateSerializer<State> {

        public MyJacksonStateSerializer() {
            super(State::new, JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .build());
        }
    }


    @Test
    public void serializeWithTypeInferenceTest() throws IOException, ClassNotFoundException {

        State state = new State( Map.of( "prop1", "value1") );

        var serializer = new MyStateSerializer();

        var type = serializer.getStateType();

        assertTrue(type.isPresent());
        assertEquals(State.class, type.get());

        byte[] bytes = serializer.objectToBytes(state);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        AgentState deserializedState = serializer.bytesToObject(bytes);

        assertNotNull(deserializedState);
        assertEquals( 1, deserializedState.data().size() );
        assertEquals( "value1", deserializedState.data().get("prop1") );
    }

    @Test
    public void NodOutputJacksonSerializationTest() throws Exception {

        final var serializer = new MyJacksonStateSerializer();

        NodeOutput<AgentState> output = new NodeOutput<>("node", new State(Map.of()));
        var mapper = serializer.objectMapper();
        var json = mapper.writeValueAsString(output);
        assertEquals("""
                {"end":false,"node":"node","start":false,"state":{"data":{}}}""", json );

        output = new NodeOutput<>("node", new State(Map.of()));
        json = serializer.objectMapper().writeValueAsString(output);

        assertEquals( """
                {"end":false,"node":"node","start":false,"state":{"data":{}}}""", json );
    }

    @Test
    public void TypeMapperTest() throws Exception {

        var mapper = new TypeMapper();

        var tr = new TypeReference<State>() {};
        System.out.println(tr.getType());
        mapper.register( new TypeMapper.Reference<State>("MyState") { } );

        var ref = mapper.getReference("MyState");

        assertTrue( ref.isPresent() );
        assertEquals( "MyState", ref.get().getTypeName() );
        System.out.println( ref.get().getType() );
        assertEquals( State.class, ref.get().getType() );
    }

    record Person ( String name, int age ){}

    @Test
    public void valueFromNodeTest() throws Exception {

        final var serializer = new MyJacksonStateSerializer();

        var data = Map.of(
                "integer", 10,
                "string", "value",
                "boolean", true,
                "long", 10_000_000_000_000L,
                "double", 10_000.34567,
                "big_decimal", new BigDecimal(123412345678901L),
                "person", new Person("John", 30));

        var state = serializer.stateFactory().apply( data );

        var bytes = serializer.objectToBytes( state );

        var clonedState = serializer.bytesToObject( bytes );

        var clonedData = clonedState.data();

        assertEquals( data.size(), clonedData.size() );
        assertEquals( data.get("integer"), clonedData.get("integer") );
        assertEquals( data.get("string"), clonedData.get("string") );
        assertEquals( data.get("boolean"), clonedData.get("boolean") );
        assertEquals( data.get("long"), clonedData.get("long") );
        assertEquals( data.get("double"), clonedData.get("double") );
        assertInstanceOf( Number.class, data.get("big_decimal"));
        assertInstanceOf( Number.class, clonedData.get("big_decimal"));
        assertEquals( Objects.toString(data.get("big_decimal")), Objects.toString(clonedData.get("big_decimal")) );
        assertInstanceOf( Map.class, clonedData.get("person") );
        @SuppressWarnings("unchecked")
        final Map<String,Object> personMap = (Map<String, Object>) clonedData.get("person");
        assertInstanceOf( Person.class, data.get("person") );
        final Person person = (Person) data.get("person");
        assertEquals( person.name(), personMap.get("name"));
        assertEquals( person.age(), personMap.get("age"));

    }

    @Test
    void checkPointSerializeTest() throws Exception {

        final var serializer = new MyJacksonStateSerializer();

        var checkpoints = new LinkedList<Checkpoint>();
        var stateData = new HashMap<String,Object>();


        for( int i = 1; i < 5; i++) {

            stateData.put("prop%d".formatted(i), "value%d".formatted(i));

            final var state = new State( stateData );

            final var cp = Checkpoint.builder()
                    .id("ID%d".formatted(i))
                    .nodeId("NODE%d".formatted(i))
                    .nextNodeId("NEXT_NODE%d".formatted(i + 1))
                    .state(state)
                    .build();

            checkpoints.add(cp);
        }

        final var cpSerializer = new JacksonCheckpointListSerializer(serializer);

        var result = cpSerializer.writeDataAsString( checkpoints );

        assertNotNull( result );

        assertEquals("""
                [
                {"@type":"org.bsc.langgraph4j.checkpoint.Checkpoint","id":"ID1","nodeId":"NODE1","nextNodeId":"NEXT_NODE2","state":{"prop1":"value1"}},
                {"@type":"org.bsc.langgraph4j.checkpoint.Checkpoint","id":"ID2","nodeId":"NODE2","nextNodeId":"NEXT_NODE3","state":{"prop2":"value2","prop1":"value1"}},
                {"@type":"org.bsc.langgraph4j.checkpoint.Checkpoint","id":"ID3","nodeId":"NODE3","nextNodeId":"NEXT_NODE4","state":{"prop2":"value2","prop1":"value1","prop3":"value3"}},
                {"@type":"org.bsc.langgraph4j.checkpoint.Checkpoint","id":"ID4","nodeId":"NODE4","nextNodeId":"NEXT_NODE5","state":{"prop2":"value2","prop1":"value1","prop4":"value4","prop3":"value3"}}]
                """.replace("\n", ""), result );

        final var newCheckpoints = cpSerializer.readDataFromString( result );

        assertNotNull( newCheckpoints );
        assertEquals( checkpoints.size(), newCheckpoints.size() );
        for( int i = 0 ; i < checkpoints.size(); i++ ) {
            assertEquals( checkpoints.get(i).getId(), newCheckpoints.get(i).getId() );
            assertEquals( checkpoints.get(i).getNodeId(), newCheckpoints.get(i).getNodeId() );
            assertEquals( checkpoints.get(i).getNextNodeId(), newCheckpoints.get(i).getNextNodeId() );
            assertEquals( checkpoints.get(i).getState(), newCheckpoints.get(i).getState() );
        }

    }

    private record NonSerializableElement(  String value )  {
        public static NonSerializableElement of(String value) {
            return new NonSerializableElement(value);
        }
    }

    @Test
    public void transientAttributesAreKeptInMemory() throws Exception {

        final var stateSerializer = new MyJacksonStateSerializer();
        stateSerializer.declareTransientAttributes("transient");


        final var state = stateSerializer.stateOf(Map.of(
                "a", "b",
                "transient", NonSerializableElement.of("I'M NOT SERIALIZABLE")
        ));

        final var jsonString = stateSerializer.writeDataAsString( state.data() );

        assertNotNull(jsonString);

        final var jsonDeserializedData = stateSerializer.objectMapper().readValue( jsonString, new TypeReference<Map<String,Object>>(){});

        assertFalse( jsonDeserializedData.containsKey("transient") );
        assertTrue( jsonDeserializedData.containsKey("a") );
        assertEquals("b", jsonDeserializedData.get("a"));

        final var stateDeserializedData = stateSerializer.readDataFromString( jsonString );

        assertTrue( stateDeserializedData.containsKey("transient") );
        assertEquals(NonSerializableElement.of("I'M NOT SERIALIZABLE"), stateDeserializedData.get("transient"));
        assertTrue( stateDeserializedData.containsKey("a") );
        assertEquals("b", stateDeserializedData.get("a"));
    }

}
