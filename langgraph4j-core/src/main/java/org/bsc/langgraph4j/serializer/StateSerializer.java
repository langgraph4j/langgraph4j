package org.bsc.langgraph4j.serializer;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.utils.Types;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public abstract class StateSerializer<State extends AgentState> implements Serializer<State> {

    private final AgentStateFactory<State> stateFactory;
    protected Set<String> transientAttributeSet = new HashSet<>(8);

    protected StateSerializer(AgentStateFactory<State> stateFactory ) {
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory cannot be null");
    }

    public void declareTransientAttributes( String... attributes) {
        transientAttributeSet.addAll(Arrays.asList(attributes));
    }

    public final AgentStateFactory<State> stateFactory() {
        return stateFactory;
    }

    public final State stateOf( Map<String,Object> data) {
        Objects.requireNonNull( data, "data cannot be null");
        return stateFactory.apply( data);
    }

    public final State cloneObject( Map<String,Object> data) throws IOException, ClassNotFoundException {
        Objects.requireNonNull( data, "data cannot be null");
        return cloneObject( stateFactory().apply(data) );
    }

    public final String writeDataAsString(Map<String,Object> data) throws IOException {
        Objects.requireNonNull(data, "data cannot be null");
        return writeDataAsString( stateFactory().apply(data) );
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
