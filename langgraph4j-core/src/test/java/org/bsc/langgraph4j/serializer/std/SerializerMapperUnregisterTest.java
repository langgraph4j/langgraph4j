package org.bsc.langgraph4j.serializer.std;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SerializerMapper#register} keys its registry by the type being serialized,
 * so {@link SerializerMapper#unregister} must look the entry up by the serializer's
 * own type instead of treating that type as a registry key.
 */
class SerializerMapperUnregisterTest {

    static class StringSerializer implements StdSerializer<String> {

        @Override
        public void write(String object, ObjectOutput out) throws IOException {
            StdSerializer.writeUTF(object, out);
        }

        @Override
        public String read(ObjectInput in) throws IOException {
            return StdSerializer.readUTF(in);
        }
    }

    static class UnusedSerializer implements StdSerializer<Integer> {

        @Override
        public void write(Integer object, ObjectOutput out) { }

        @Override
        public Integer read(ObjectInput in) { return null; }
    }

    @Test
    void unregisterShouldRemoveTheRegisteredSerializer() {

        final var mapper = new SerializerMapper();

        mapper.register( String.class, new StringSerializer() );

        assertTrue( mapper.getSerializer( String.class ).isPresent(),
                "precondition: the serializer is registered for String" );

        assertTrue( mapper.unregister( StringSerializer.class ),
                "unregister() should report that it removed the serializer" );

        assertTrue( mapper.getSerializer( String.class ).isEmpty(),
                "the serializer should no longer be returned after unregister()" );
    }

    @Test
    void unregisterShouldReportFalseForASerializerThatWasNeverRegistered() {

        final var mapper = new SerializerMapper();

        mapper.register( String.class, new StringSerializer() );

        assertFalse( mapper.unregister( UnusedSerializer.class ),
                "unregister() should not claim to remove a serializer that was never registered" );

        assertTrue( mapper.getSerializer( String.class ).isPresent(),
                "an unrelated unregister() call must leave other registrations untouched" );
    }
}
