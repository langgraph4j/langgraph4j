package org.bsc.langgraph4j.utils;

import org.bsc.langgraph4j.HasMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public interface MetadataUtils {


    static String toString( HasMetadata metadataSupplier ) {
        if(metadataSupplier == null || metadataSupplier.metadataKeys().isEmpty()) {
            return "{}";
        }
        return metadataSupplier.metadataKeys().stream()
                .map( key -> {
                    final var value = metadataSupplier.metadata(key).orElse(null);

                    if( value instanceof Collection<?> elements ) {
                        return "%s=%s".formatted( key, CollectionsUtils.toString(elements));
                    }
                    if( value instanceof Map<?,?> elements ) {
                        return "%s=%s".formatted( key, CollectionsUtils.toString(elements));
                    }
                    return "%s=%s".formatted( key, Objects.toString(value));
                })
                .collect(Collectors.joining("\n\t", "{\n\t", "\n}") );
    }
}
