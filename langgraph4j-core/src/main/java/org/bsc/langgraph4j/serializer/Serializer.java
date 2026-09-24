package org.bsc.langgraph4j.serializer;

import java.io.IOException;
import java.util.Objects;

public interface Serializer<T> {

    String contentType();

    String writeDataAsString(T data) throws IOException;
    T readDataFromString(String string) throws IOException, ClassNotFoundException;

    default T cloneObject(T object) throws IOException, ClassNotFoundException {
        Objects.requireNonNull( object, "object cannot be null" );
        return readDataFromString(writeDataAsString(object));
    }

}
