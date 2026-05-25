package org.bsc.langgraph4j.utils;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

/**
 * Utility class for creating collections.
 */
public final class CollectionsUtils {

    public static String toString( Collection<?> collection ) {
        if(collection.isEmpty()) {
            return "[]";
        }
        return collection.stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n\t", "[\n\t", "\n\t]"));

    }

    public static String toString( Map<?,?> map ) {
        if( map == null ) {
            return "null";
        }
        if( map.isEmpty()) {
            return "{}";
        }
        return map.entrySet().stream()
                .map( entry -> {
                    if( entry.getValue() instanceof Collection<?> elements ) {
                        return "%s=%s".formatted( entry.getKey(), toString(elements));
                    }
                    return Objects.toString(entry);
                })
                .collect(Collectors.joining("\n\t", "{\n\t", "\n}") );

    }

    /**
     * Returns the last value in the list, if present.
     *
     * @return an Optional containing the last value if present, otherwise an empty Optional
     */
    public static <T> Optional<T> lastOf( List<T> values ) {
        return (values == null || values.isEmpty()) ?
                Optional.empty() :
                Optional.of(values.get(values.size() - 1));
    }


    /**
     * Returns the value at the specified position from the end of the list, if present.
     *
     * @param n the position from the end of the list
     * @return an Optional containing the value at the specified position if present, otherwise an empty Optional
     */
    public static <T> Optional<T> lastMinus( List<T> values, int n) {
        if ( n < 0 || values == null || values.isEmpty() )  {
            return Optional.empty();
        }
        var index = values.size() - n - 1;
        return ( index < 0 ) ?
                Optional.empty() :
                Optional.of(values.get(index));
    }

    /**
     * Merges two maps into a new map, doesn't accept duplicates.
     *
     * @param map1        the first map
     * @param map2        the second map
     * @param <K>         the type of the keys in the maps
     * @param <V>         the type of the values in the maps
     * @return a new map containing all entries from both maps, collisions result in error
     * @throws NullPointerException if both maps are null
     */
    public static <K, V> Map<K, V> mergeMap( Map<K,V> map1, Map<K,V> map2 ) {
        if( map2 == null || map2.isEmpty() ) {
            return requireNonNull(map1, "map1 cannot be null");
        }
        if( map1 == null || map1.isEmpty() ) {
            return requireNonNull(map2, "map2 cannot be null");
        }

        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream() )
                .collect( Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Merges two maps into a new map, resolving key collisions using the specified merge function.
     *
     * @param map1        the first map
     * @param map2        the second map
     * @param mergeFunction the function used to resolve collisions between values associated with the same key
     * @param <K>         the type of the keys in the maps
     * @param <V>         the type of the values in the maps
     * @return a new map containing all entries from both maps, with collisions resolved by the merge function
     * @throws NullPointerException if both maps are null
     */
    public static <K, V> Map<K, V> mergeMap(Map<K, V> map1, Map<K, V> map2, BinaryOperator<V> mergeFunction) {
        if( map2 == null || map2.isEmpty() ) {
            return requireNonNull(map1, "map1 cannot be null");
        }
        if( map1 == null || map1.isEmpty() ) {
            return requireNonNull(map2, "map2 cannot be null");
        }

        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        requireNonNull(mergeFunction, "mergeFunction cannot be null")
                ));
    }

    /**
     * create an entry that accept null value
     *
     * @param key – the key represented by this entry
     * @param value – the value represented by this entry
     * @return new entry
     * Type parameters:
     * <K> – the type of the key
     * <V> – the type of the value
     */
    public static <K,V>  Map.Entry<K,V> entryOf( K key, V value ) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    @Deprecated(forRemoval = true)
    public static <T> List<T> listOf(Class<T> clazz) {
        return Collections.emptyList();
    }

    /**
     * Creates a list containing the provided elements. It allows null values.
     *
     * @param objects the elements to be included in the list
     * @param <T> the type of the elements
     * @return a list containing the provided elements
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... objects) {
        if( objects == null ) {
            return Collections.emptyList();
        }
        if ( objects.length == 0 ) {
            return Collections.emptyList();
        }
        if( objects.length == 1 ) {
            return Collections.singletonList(objects[0]);
        }
        return Collections.unmodifiableList(Arrays.asList(objects));
    }

    /**
     * Creates an empty map. It allows null values.
     *
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return an empty map
     */
    public static <K, V> Map<K, V> mapOf() {
        return emptyMap();
    }

    /**
     * Creates a map containing a single key-value pair. It allows null values.
     *
     * @param k1 the key
     * @param v1 the value
     * @param <K> the type of the key
     * @param <V> the type of the value
     * @return an unmodifiable map containing the provided key-value pair
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return Collections.singletonMap(k1, v1);
    }

    /**
     * Creates a map containing two key-value pairs. It allows null values.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return an unmodifiable map containing the provided key-value pairs
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> result = new HashMap<K, V>();
        result.put(k1, v1);
        result.put(k2, v2);
        return unmodifiableMap(result);
    }

    /**
     * Creates a map containing three key-value pairs. It allows null values.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return an unmodifiable map containing the provided key-value pairs
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> result = new HashMap<K, V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        return unmodifiableMap(result);
    }
    /**
     * Creates a map containing three key-value pairs. It allows null values.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value@
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return an unmodifiable map containing the provided key-value pairs
     */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> result = new HashMap<K, V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        return unmodifiableMap(result);
    }

    /**
     * Creates a map containing three key-value pairs. It allows null values.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value@
     * @param k5 the fifth key
     * @param v5 the fifth value@
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return an unmodifiable map containing the provided key-value pairs
     */
    public static<K,V>  Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> result = new HashMap<K, V>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        result.put(k5, v5);
        return unmodifiableMap(result);
    }
}
