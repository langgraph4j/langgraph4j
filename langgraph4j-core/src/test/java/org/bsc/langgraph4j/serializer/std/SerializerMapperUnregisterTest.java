package org.bsc.langgraph4j.serializer.std;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SerializerMapper#register} stores each serializer under the type it serializes,
 * so {@link SerializerMapper#unregister} has to be given that same type.
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

    static class IntegerSerializer implements StdSerializer<Integer> {

        @Override
        public void write(Integer object, ObjectOutput out) throws IOException {
            out.writeInt(object);
        }

        @Override
        public Integer read(ObjectInput in) throws IOException {
            return in.readInt();
        }
    }

    @Test
    void unregisterShouldRemoveTheSerializerOfTheGivenType() {

        final var mapper = new SerializerMapper();

        mapper.register( String.class, new StringSerializer() );

        assertTrue( mapper.getSerializer( String.class ).isPresent(),
                "precondition: a serializer is registered for String" );

        assertTrue( mapper.unregister( String.class ),
                "unregister() should report that it removed the serializer" );

        assertTrue( mapper.getSerializer( String.class ).isEmpty(),
                "the serializer should no longer be returned after unregister()" );
    }

    @Test
    void unregisterShouldOnlyRemoveTheGivenType() {

        final var mapper = new SerializerMapper();

        mapper.register( String.class, new StringSerializer() );
        mapper.register( Integer.class, new IntegerSerializer() );

        assertTrue( mapper.unregister( String.class ) );

        assertTrue( mapper.getSerializer( String.class ).isEmpty(),
                "the unregistered type should be gone" );
        assertTrue( mapper.getSerializer( Integer.class ).isPresent(),
                "a different type must keep its serializer" );
    }

    @Test
    void unregisterShouldReportFalseForATypeThatWasNeverRegistered() {

        final var mapper = new SerializerMapper();

        mapper.register( String.class, new StringSerializer() );

        assertFalse( mapper.unregister( Integer.class ),
                "unregister() should not claim to remove a type that was never registered" );

        assertTrue( mapper.getSerializer( String.class ).isPresent(),
                "an unrelated unregister() call must leave other registrations untouched" );
    }
}
