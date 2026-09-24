package org.bsc.langgraph4j.serializer.std;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public interface NullableObjectSerializer<T> extends StdSerializer<T> {

    default void writeNullableObject(Object object, ObjectOutput out) throws IOException {
        if( object == null ) {
            out.writeByte(0);
        }
        else {
            out.writeByte(1);
            out.writeObject( object );
        }
    }

    default Optional<Object> readNullableObject(ObjectInput in) throws IOException, ClassNotFoundException {
        byte b = in.readByte();
        return ( b == 0 ) ? Optional.empty() : Optional.of( in.readObject() );
    }

    default void writeNullableUTF(String object, ObjectOutput out) throws IOException {
        if( object == null ) {
            out.writeInt(-1);
            return;
        }
        if( object.isEmpty()) {
            out.writeInt(0);
            return;
        }
        byte[] utf8Bytes = object.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8Bytes.length); // prefix with length
        out.write(utf8Bytes);
    }

    default Optional<String> readNullableUTF(ObjectInput in) throws IOException {
        int length = in.readInt();
        if( length < 0 ) {
            return Optional.empty();
        }
        if( length == 0 ) {
            return Optional.of("");
        }
        byte[] utf8Bytes = new byte[length];
        in.readFully(utf8Bytes);
        return Optional.of(new String(utf8Bytes, StandardCharsets.UTF_8));
    }

}
