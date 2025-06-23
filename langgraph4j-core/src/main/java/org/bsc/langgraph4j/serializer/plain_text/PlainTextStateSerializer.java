package org.bsc.langgraph4j.serializer.plain_text;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class PlainTextStateSerializer<State extends AgentState> extends StateSerializer<State> {

    protected PlainTextStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);
    }

    @Override
    public String contentType() {
        return "plain/text";
    }

    @SuppressWarnings("unchecked")
    public Class<State> getStateType() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType parameterizedType) {
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                return (Class<State>) typeArguments[0];
            }
        }
        throw new IllegalStateException("Unable to determine state type");
    }


    public State read( String data ) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytesStream =  new ByteArrayOutputStream();

        try(ObjectOutputStream out = new ObjectOutputStream( bytesStream )) {
            out.writeUTF(data);
            out.flush();
        }

        try(ObjectInput in = new ObjectInputStream( new ByteArrayInputStream( bytesStream.toByteArray() ) ) ) {
            return read(in);
        }

    }

    public State read( Reader reader ) throws IOException, ClassNotFoundException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return read( sb.toString() );
    }

}
