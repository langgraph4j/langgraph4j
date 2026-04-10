package org.bsc.langgraph4j.action;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.HasMetadata;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record NodeResult(
        Map<String,Object> data,
        AsyncGenerator<?> generator,
        Map<String,Object> metadata
    ) implements HasMetadata {

    public static NodeResult withData( Map<String,Object> data ) {
        return new NodeResult(data,null,null);
    }
    public static NodeResult withDataAndMetadata( Map<String,Object> data, Map<String,Object> metadata ) {
        return new NodeResult(data,null,metadata);
    }
    public static NodeResult withGenerator( AsyncGenerator<?> embedGenerator, Map<String,Object> data ) {
        return new NodeResult(data,embedGenerator,null);
    }
    public static NodeResult withGeneratorAndMetadata( AsyncGenerator<?> embedGenerator, Map<String,Object> data, Map<String,Object> metadata ) {
        return new NodeResult(data,embedGenerator,metadata);
    }

    public boolean hasGenerator() {
        return generator != null;
    }
    public boolean hasData() {
        return data != null;
    }
    public boolean hasMetadata() {
        return metadata != null;
    }

    @Override
    public Optional<Object> metadata(String key) {
        return ofNullable(metadata).flatMap(m -> ofNullable(m.get(key)));
    }

    @Override
    public Set<String> metadataKeys() {
        return ofNullable(metadata).map(Map::keySet).orElseGet(Set::of);
    }
}
