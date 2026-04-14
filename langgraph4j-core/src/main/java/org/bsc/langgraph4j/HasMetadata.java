package org.bsc.langgraph4j;

import org.bsc.langgraph4j.utils.TypeRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * return metadata keys
     *
     * @since 1.8
     * @return metadata keys if any or empty set
     */
    Set<String> metadataKeys();


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
        protected B this$() {
            return (B)this;
        }

        public B putMetadata( Map<String,Object> newMetadata ) {
            if( newMetadata != null && !newMetadata.isEmpty() ) {
                if (metadata == null) {
                    // Lazy initialization of metadata map
                    metadata = new HashMap<>();
                }
                metadata.putAll(newMetadata);
            }
            return this$();
        }

        public B putMetadata( String key, Object value ) {
            requireNonNull(key, "key cannot be null");
            if( metadata == null ) {
                // Lazy initialization of metadata map
                metadata = new HashMap<>();
            }
            metadata.put( key, value);
            return this$();
        }

        public B putMetadataIfAbsent( String key, Object value ) {
            requireNonNull(key, "key cannot be null");
            if( metadata == null ) {
                // Lazy initialization of metadata map
                metadata = new HashMap<>();
            }
            metadata.putIfAbsent( key, value);
            return this$();
        }

        public B addMetadata( String key, Object value ) {
            requireNonNull(key, "key cannot be null");
            if( metadata == null ) {
                // Lazy initialization of metadata map
                metadata = new HashMap<>();
            }
            else {
                if( metadata.containsKey(key)) {
                    throw new IllegalArgumentException( "Metadata key [%s] already exists: ".formatted(key));
                }
            }

            metadata.put( key, value);

            return this$();
        }

        public B removeMetadata( String key ) {
            if( metadata != null ) {
                metadata.remove(requireNonNull(key, "key cannot be null"));
            }
            return this$();
        }

    }

}

