package org.bsc.langgraph4j.serializer.std;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class SerializerMapper {
    static final StdSerializer<Object> DEFAULT_SERIALIZER = new StdSerializer<Object>() {
        @Override
        public void write(Object object, ObjectOutput out) throws IOException {
            out.writeObject(object);
        }

        @Override
        public Object read(ObjectInput in) throws IOException, ClassNotFoundException {
            return in.readObject();
        }
    };

    static class Key {
        private final String _className;
        private final Class<?> _clazz;

        public static Key of(Class<?> clazz) {
            return new Key(clazz);
        }
        public static Key of(String  className ) {
            return new Key(className);
        }

        private Key(Class<?> clazz) {
            _className = clazz.getName();
            _clazz = clazz;
        }

        private Key(String className) {
            _className = className;
            _clazz = null;
        }

        String getTypeName() { return _className; }

        Class<?> getType() { return _clazz; }

        @Override
        public boolean equals(Object o) {
            return Objects.equals( o, _className );
        }

        @Override
        public int hashCode() {
            return Objects.hash(_className);
        }
    }
    private final Map<Key, StdSerializer<?>> _serializers = new HashMap<>();

    public SerializerMapper register( Class<?> clazz, StdSerializer<?> serializer ) {
        Objects.requireNonNull(clazz,"class cannot be null ");
        Objects.requireNonNull(clazz,"serializer cannot be null ");

        _serializers.put( Key.of(clazz), serializer);
        return this;
    }

    public boolean unregister( Class<? extends StdSerializer<?>> clazz ) {
        Objects.requireNonNull( clazz, "Serializer's class cannot be null" );
        StdSerializer<?> serializer = _serializers.remove( Key.of(clazz) );
        return serializer != null;
    }

    @SuppressWarnings("unchecked")
    public Optional<StdSerializer<Object>> getSerializer(Class<?> clazz ) {
        Objects.requireNonNull(clazz,"class cannot be null ");
        StdSerializer<?> ser = _serializers.get( Key.of(clazz) );

        return ( ser != null ) ?

            Optional.of((StdSerializer<Object>)ser) :
/*
            _serializers.entrySet().stream()
                    .filter( e -> e.getKey().getType().isAssignableFrom(clazz) )
                    .findFirst()
                    .map( e -> (Serializer<Object>)e.getValue() )
                ;
*/
            _serializers.entrySet().stream()
                    .filter( e -> e.getKey().getType().isAssignableFrom(clazz) )
                    .min((c1, c2) -> {
                        if (c1.getKey().equals(c2.getKey())) return 0;
                        if (c1.getKey().getType().isAssignableFrom(c2.getKey().getType())) return 1;   // c2 is more specific
                        if (c2.getKey().getType().isAssignableFrom(c1.getKey().getType())) return -1;  // c1 is more specific
                        return 0;
                    })
                    .map( e -> (StdSerializer<Object>)e.getValue() )
                ;
    }

    @SuppressWarnings("unchecked")
    public Optional<StdSerializer<Object>> getSerializer(String className ) {
        Objects.requireNonNull(className,"className cannot be null ");
        return Optional.ofNullable((StdSerializer<Object>)_serializers.get( Key.of(className) ));
    }

    public StdSerializer<Object> getDefaultSerializer() {
        return DEFAULT_SERIALIZER;
    }

    protected final ObjectOutput objectOutputWithMapper(ObjectOutput out) {
        Objects.requireNonNull( out, "ObjectOutput cannot be null");
        final ObjectOutputWithMapper mapperOut ;
        if( out instanceof ObjectOutputWithMapper ) {
            mapperOut = (ObjectOutputWithMapper)out;
        } else {
            mapperOut = new ObjectOutputWithMapper( out, this );
        }

        return mapperOut;
    }

    protected final ObjectInput objectInputWithMapper(ObjectInput in) {
        Objects.requireNonNull( in, "ObjectInput cannot be null");
        final ObjectInputWithMapper mapperIn ;
        if( in instanceof ObjectInputWithMapper ) {
            mapperIn = (ObjectInputWithMapper)in;
        } else {
            mapperIn = new ObjectInputWithMapper( in, this );
        }

        return mapperIn;

    }

    @Override
    public String toString() {
        List<String> typeNames = _serializers.keySet().stream().map(Key::getTypeName).collect(Collectors.toList());
        return format( "SerializerMapper: \n%s", String.join("\n", typeNames) );

    }

}
