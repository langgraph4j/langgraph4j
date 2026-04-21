package org.bsc.langgraph4j.internal.hook;

import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public abstract class HookCalls<T> {
    public enum Type { LIFO, FIFO }
    Map<String, Deque<T>> callMap;
    Deque<T> callList;
    final Type type;

    HookCalls( Type type ) {
        this.type = requireNonNull(type, "type cannot be null");
    }
    public void add(T call ) {
        requireNonNull( call, "call cannot be null");

        if( callList == null ) { // Lazy Initialization
            callList = new ArrayDeque<>();
        }

        if( type == Type.LIFO )
            callList.addFirst(call);
        else
            callList.addLast(call);
    }

    public void add(String nodeId, T call ) {
        requireNonNull( nodeId, "nodeId cannot be null");
        requireNonNull( call, "call cannot be null");

        if( callMap == null ) { // Lazy Initialization
            callMap = new HashMap<>();
        }

        var callList = callMap.computeIfAbsent(nodeId, k -> new ArrayDeque<>());
        if( type == Type.LIFO )
            callList.addFirst(call);
        else
            callList.addLast(call);

    }

    public boolean isEmpty() {
        return (callList == null || callList.isEmpty()) && (callMap == null || callMap.isEmpty()) ;
    }

    public Stream<T> callListAsStream( ) {
        return ofNullable(callList).stream().flatMap(Collection::stream);
    }

    public Stream<Map.Entry<String,Deque<T>>> callMapAsStream() {
        return ofNullable(callMap).stream()
                .map( Map::entrySet )
                .flatMap( Collection::stream );
    }

    public Stream<T> callMapAsStream( String nodeId ) {
        requireNonNull( nodeId, "nodeId cannot be null");
        return ofNullable(callMap).stream()
                .flatMap( map ->
                        ofNullable( map.get(nodeId) ).stream()
                                .flatMap( Collection::stream ));
    }

    @Override
    public String toString() {
        var superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType parameterizedType) {
            var typeArg = parameterizedType.getActualTypeArguments()[0];
            return typeArg.getTypeName();
        }
        return "unknown Hook type";
    }
}

