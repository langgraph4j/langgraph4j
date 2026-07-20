package org.bsc.langgraph4j.serializer.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.serializer.plain_text.gson.GsonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GSonSerializerTest {

    static class State extends AgentState {

        /**
         * Constructs an AgentState with the given initial data.
         *
         * @param initData the initial data for the agent state
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    static class MyStateSerializer extends GsonStateSerializer<State> {
        public MyStateSerializer() {
            super(State::new);
        }

    }

    @Test
    public void serializeWithTypeInferenceTest() throws IOException, ClassNotFoundException {

        State state = new State( Map.of( "prop1", "value1") );

        GsonStateSerializer<State> serializer = new MyStateSerializer();

        var type = serializer.getStateType();

        assertTrue( type.isPresent() );
        assertEquals(State.class, type.get());

        byte[] bytes = serializer.objectToBytes(state);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        AgentState deserializedState = serializer.bytesToObject(bytes);

        assertNotNull(deserializedState);
        assertEquals( 1, deserializedState.data().size() );
        assertEquals( "value1", deserializedState.data().get("prop1") );
    }

    static class GsonSerializer extends GsonStateSerializer<AgentState> {

        public GsonSerializer() {
            super(AgentState::new, new GsonBuilder()
                    .serializeNulls()
                    .create());
        }

        Gson getGson() {
            return gson;
        }
    }

    @Test
    public void NodOutputJGsonSerializationTest() throws Exception {

        GsonSerializer serializer = new GsonSerializer();

        NodeOutput<AgentState> output = new NodeOutput<>("node", new State(Map.of()));
        String json = serializer.getGson().toJson(output);

        assertEquals( "{\"node\":\"node\",\"state\":{\"data\":{}}}", json );

        output = new NodeOutput<>("node", new State(Map.of()));
        json = serializer.getGson().toJson(output);

        assertEquals( "{\"node\":\"node\",\"state\":{\"data\":{}}}", json );
    }


}
