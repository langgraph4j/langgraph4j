package org.bsc.langgraph4j.serializer.std;

import org.bsc.langgraph4j.LG4JLoggable;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

public class ObjectStreamStateSerializer<State extends AgentState> extends StateSerializer<State> implements LG4JLoggable{

    static class ListSerializer implements NullableObjectSerializer<List<Object>> {

        @Override
        public void write(List<Object> object, ObjectOutput out) throws IOException {
            out.writeInt( object.size() );

            for( Object value : object ) {
                try {
                    writeNullableObject( value, out );
                } catch (IOException ex) {
                    log.error( "Error writing collection value", ex );
                    throw ex;
                }
            }

            out.flush();

        }

        @Override
        public List<Object> read(ObjectInput in) throws IOException, ClassNotFoundException {
            List<Object> result = new ArrayList<>();

            int size = in.readInt();

            for (int i = 0; i < size; i++) {

                Object value = readNullableObject(in).orElse(null);

                result.add(value);

            }

            return result;
        }
    }

    static class MapSerializer implements NullableObjectSerializer<Map<String,Object>> {

        @Override
        public void write(Map<String,Object> object, ObjectOutput out) throws IOException {
            out.writeInt( object.size() );

            for( Map.Entry<String,Object> e : object.entrySet() ) {
                try {
                    Serializer.writeUTF(e.getKey(), out);

                    writeNullableObject( e.getValue(), out );

                } catch (IOException ex) {
                    log.error( "Error writing map key '{}'", e.getKey(), ex );
                    throw ex;
                }
            }

            out.flush();

        }

        @Override
        public Map<String, Object> read(ObjectInput in) throws IOException, ClassNotFoundException {
            Map<String, Object> result = new HashMap<>();

            int size = in.readInt();

            for( int i = 0; i < size; i++ ) {
                String key = Serializer.readUTF(in);

                Object value = readNullableObject(in).orElse(null);

                result.put(key, value);

            }
            return result;
        }

    }

    private final SerializerMapper mapper = new SerializerMapper();
    private final MapSerializer mapSerializer = new MapSerializer();
    private final Map<String, Object> transientData = new HashMap<>(8);

    public ObjectStreamStateSerializer( AgentStateFactory<State> stateFactory ) {
        super(stateFactory);
        mapper.register( List.class, new ListSerializer() );
        mapper.register( Map.class, new MapSerializer() );
        mapper.register( Set.class, new SetSerializer() );
    }

    public SerializerMapper mapper() {
        return mapper;
    }

    @Override
    public final void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        final Map<String,Object> serializedData;

        if( transientAttributeSet.isEmpty() ) {
            serializedData = data;
        } else {
            serializedData = new HashMap<>(data);

            for( String key : transientAttributeSet ) {
                if( serializedData.containsKey(key) ) {
                    transientData.put(key, serializedData.remove(key));
                }
            }
        }

        mapSerializer.write(serializedData, mapper.objectOutputWithMapper(out));
    }

    @Override
    public final Map<String, Object> readData(ObjectInput in) throws IOException, ClassNotFoundException {
        final var data = mapSerializer.read( mapper.objectInputWithMapper(in) );
        for( String key : transientAttributeSet ) {
            if( transientData.containsKey(key) ) {
                data.put(key, transientData.get(key));
            }
        }
        return data;
    }


}
