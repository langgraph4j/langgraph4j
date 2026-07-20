package org.bsc.langgraph4j.metadata;

import org.bsc.langgraph4j.HasMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public final class MetadataBag implements HasMetadata {

    public static class Builder extends HasMetadata.Builder<Builder> {

        public Builder() {
            super();
        }

        public Builder(Map<String, Object> metadata) {
            super(metadata);
        }

        public Builder(HasMetadata metadataSupplier) {
            super(metadataSupplier);
        }

        public MetadataBag build() {
            return new MetadataBag(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder( @Nullable Map<String, Object> metadata ) {
        return new Builder(metadata);
    }

    public static Builder builder( HasMetadata metadataSupplier ) {
        return new Builder(metadataSupplier);
    }

    private final Map<String,Object> metadata;


    private MetadataBag( Builder builder ) {
        this.metadata = requireNonNull(builder, "builder must not be null")
                .metadata();
    }

    @Override
    public Optional<Object> metadata(String key) {
        return ofNullable(
                metadata.get( requireNonNull(key, "key must not be null")));
    }

    @Override
    public Set<String> metadataKeys() {
        return Set.copyOf(metadata.keySet());
    }
}
