package org.bsc.langgraph4j.serializer.plain_text.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Base Implementation of {@link PlainTextStateSerializer} using GSON library
 * . Need to be extended from specific state implementation
 * @param <State> The type of the agent state to be serialized/deserialized.
 */
@Deprecated(forRemoval = true, since = "1.9")
public abstract class GsonStateSerializer<State extends AgentState> extends PlainTextStateSerializer<State> {

    protected final Gson gson;
    private final Map<String, Object> transientData = new HashMap<>(8);

    protected GsonStateSerializer(AgentStateFactory<State> stateFactory, Gson gson) {
        super(stateFactory);
        this.gson = gson;
    }

    protected GsonStateSerializer(AgentStateFactory<State> stateFactory) {
        this(stateFactory, new GsonBuilder()
                                .serializeNulls()
                                .create());
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public final String writeDataAsString(State state) throws IOException {
        final Map<String,Object> serializedData;

        if( transientAttributeSet.isEmpty() ) {
            serializedData = state.data();
        } else {
            serializedData = new HashMap<>(state.data());

            for( String key : transientAttributeSet ) {
                if( serializedData.containsKey(key) ) {
                    transientData.put(key, serializedData.remove(key));
                }
            }
        }

        return gson.toJson(serializedData);
    }

    @Override
    public final State readDataFromString(String string) throws IOException {
        final var data =  gson.fromJson(string, new TypeToken<Map<String, Object>>() {});

        for( String key : transientAttributeSet ) {
            if( transientData.containsKey(key) ) {
                data.put(key, transientData.get(key));
            }
        }
        return stateFactory().apply(data);

    }

}