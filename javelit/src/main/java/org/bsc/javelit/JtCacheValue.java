package org.bsc.javelit;

import io.javelit.core.Jt;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public record JtCacheValue<T>(String key ) {

    public String key() {
        return key;
    }

    @SuppressWarnings("unchecked")
    public Optional<T> value() {
        return Optional.ofNullable( (T) Jt.cache().get( key() ));
    }

    @SuppressWarnings("unchecked")
    public T computeIfAbsent( Function<String, T> getter ) {
        return (T)Jt.cache().computeIfAbsent( key(), getter);
    }

    public void setValue( T value ) {
        Jt.cache().put( key(), value );
    }

    @SuppressWarnings("unchecked")
    public Optional<T> clear() {
        return Optional.ofNullable((T)Jt.cache().remove( key() ));
    }

    public <R> R ifNotPresentOrElseGet(Function<JtCacheValue<T>, R> eval, Supplier<R> defaultValue ) {
        if(value().isEmpty()) {
            return eval.apply(this);
        }
        return defaultValue.get();
    }

    public <R> R ifNotPresentOrElse(Function<JtCacheValue<T>, R> eval, R defaultValue ) {
        if(value().isEmpty()) {
            return eval.apply(this);
        }
        return defaultValue;
    }

    @Override
    @NonNull
    public String toString() {
        return "CachedValue{key='%s', value=%s}".formatted(key, value().orElse(null));
    }
}