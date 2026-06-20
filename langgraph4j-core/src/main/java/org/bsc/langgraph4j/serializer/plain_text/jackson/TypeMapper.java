package org.bsc.langgraph4j.serializer.plain_text.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;

import static java.util.Objects.requireNonNull;

public class TypeMapper {

    public static String TYPE_PROPERTY = "@type";

    public static abstract class Reference<T> extends TypeReference<T> {

        private final String typeName;

        public Reference( String typeName ) {
            super();
            this.typeName = requireNonNull(typeName, "typeName cannot be null");
        }

        /**
         * Creates a reference using the fully qualified name of the specified type.
         *
         * @param type the Java class whose name is used as the mapped type name
         * @throws NullPointerException if {@code type} is null
         * @since 1.9
         */
        public Reference( Class<T> type ) {
            super();
            this.typeName = requireNonNull(type, "type cannot be null").getName();
        }

        public String getTypeName() { return typeName; }


    }


    private final Set<Reference<?>> references = new HashSet<>();

    public <T> TypeMapper register( Reference<T> reference ) {
        requireNonNull( reference, "reference cannot be null");
        references.add( reference );
        return this;
    }

    public <T> boolean unregister( Reference<T> reference) {
        requireNonNull( reference, "reference cannot be null");
        return references.remove( reference );
    }

    public Optional<Reference<?>> getReference( String type ) {
        requireNonNull( type, "type cannot be null");
        return references.stream()
                    .filter( ref -> Objects.equals( ref.getTypeName(), type) )
                    .findFirst();
    }


}
