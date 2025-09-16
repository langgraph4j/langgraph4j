package org.bsc.langgraph4j;

import org.bsc.langgraph4j.utils.TypeRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public interface HasMetadata {
    /**
     * return metadata value for key
     *
     * @param key given metadata key
     * @return metadata value for key if any
     *
     */
    Optional<Object> metadata( String key );

    /**
     * Returns a type-safe metadata value for the given key.
     * <p>
     * This method retrieves the metadata object and attempts to cast it to the specified type.
     *
     * @param <T> the type of the metadata value
     * @param key the metadata key
     * @param typeRef a {@link TypeRef} representing the desired type of the value
     * @return an {@link Optional} containing the metadata value cast to the specified type,
     *         or an empty {@link Optional} if the key is not found or the value cannot be cast.
     */
    default <T> Optional<T> metadata(String key, TypeRef<T> typeRef ) {
        return metadata(key).flatMap( typeRef::cast );
    }

    /**
     * return metadata value for key
     *
     * @param key given metadata key
     * @return metadata value for key if any
     * @deprecated use {@link #metadata(String)} instead
     */
    @Deprecated( forRemoval = true )
    default Optional<Object> getMetadata(String key ) {
        return metadata(key);
    };

    class Builder<B extends Builder<B>> {
        private Map<String,Object> metadata;

        public Map<String,Object> metadata() {
            return ofNullable(metadata).map(Map::copyOf).orElseGet(Map::of);
        }

        protected Builder() {}

        protected Builder( Map<String,Object> metadata ) {
            if( metadata != null && !metadata.isEmpty() ) {
                this.metadata = new HashMap<>(metadata);
            }
        }

        @SuppressWarnings("unchecked")
        public B addMetadata( String key, Object value ) {
            requireNonNull(key, "key cannot be null");
            if( metadata == null ) {
                // Lazy initialization of metadata map
                metadata = new HashMap<>();
            }
            else {
                if( metadata.containsKey(key)) {
                    throw new IllegalArgumentException( format("Metadata key [%s] already exists: ", key));
                }
            }


            metadata.put( key, value);

            return (B)this;
        };
    }

}

