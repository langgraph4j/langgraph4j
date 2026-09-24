package org.bsc.langgraph4j.serializer.plain_text;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.utils.Types;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;


public abstract class PlainTextStateSerializer<State extends AgentState> extends StateSerializer<State> {

    protected PlainTextStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);
    }

    public final State readDataFromReader( Reader reader ) throws IOException, ClassNotFoundException {
        final var stringWriter = new StringWriter();
        reader.transferTo(stringWriter);
        return readDataFromString(stringWriter.toString());
    }

    @SuppressWarnings("unchecked")
    public Optional<Class<State>> getStateType() {
        return Types.parameterizedType(getClass())
                .map(ParameterizedType::getActualTypeArguments)
                .filter( args -> args.length > 0 )
                .map( args -> (Class<State>)args[0] );
    }


}
