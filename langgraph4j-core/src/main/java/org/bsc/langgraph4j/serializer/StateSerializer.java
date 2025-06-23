package org.bsc.langgraph4j.serializer;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.*;
import java.util.Map;
import java.util.Objects;

public abstract class StateSerializer<State extends AgentState> implements Serializer<State> {

    private final AgentStateFactory<State> stateFactory;

    protected StateSerializer( AgentStateFactory<State> stateFactory) {
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory cannot be null");
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

    @Override
    public final void write(State object, ObjectOutput out) throws IOException {
        writeData(object.data(), out);
    }

    @Override
    public final State read(ObjectInput in) throws IOException, ClassNotFoundException {
        return stateFactory().apply( readData(in) );
    }

    public abstract void writeData( Map<String,Object> data, ObjectOutput out) throws IOException ;

    public abstract Map<String,Object> readData( ObjectInput in ) throws IOException, ClassNotFoundException ;

    public final byte[] dataToBytes(Map<String,Object> data) throws IOException {
        Objects.requireNonNull( data, "object cannot be null" );
        try( ByteArrayOutputStream stream = new ByteArrayOutputStream() ) {
            ObjectOutputStream oas = new ObjectOutputStream(stream);
            writeData(data, oas);
            oas.flush();
            return stream.toByteArray();
        }
    }

    public final Map<String,Object> dataFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        Objects.requireNonNull( bytes, "bytes cannot be null" );
        if( bytes.length == 0 ) {
            throw new IllegalArgumentException("bytes cannot be empty");
        }
        try( ByteArrayInputStream stream = new ByteArrayInputStream( bytes ) ) {
            ObjectInputStream ois = new ObjectInputStream(stream);
            return readData(ois);
        }
    }

}
