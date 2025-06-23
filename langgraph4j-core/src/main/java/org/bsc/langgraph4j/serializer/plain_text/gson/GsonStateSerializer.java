package org.bsc.langgraph4j.serializer.plain_text.gson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Base Implementation of {@link PlainTextStateSerializer} using GSON library
 * . Need to be extended from specific state implementation
 * @param <State> The type of the agent state to be serialized/deserialized.
 */
public abstract class GsonStateSerializer<State extends AgentState> extends PlainTextStateSerializer<State> {

    protected final Gson gson;

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
    public final void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        String json = gson.toJson(data);
        out.writeUTF(json);
    }

    @Override
    public final Map<String, Object> readData(ObjectInput in) throws IOException, ClassNotFoundException {
        String json = in.readUTF();
        var typeToken = new TypeToken<Map<String, Object>>() {};
        return gson.fromJson(json, typeToken);
    }

}