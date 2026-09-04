package org.bsc.langgraph4j.state;

import java.util.*;
import java.util.function.Supplier;

import org.bsc.langgraph4j.LG4JLoggable;

import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;


/**
 * AppenderChannel is a {@link Channel} implementation that
 * is used to accumulate a list of values.
 *
 * @param <T> the type of the values being accumulated
 * @see Channel
 */
public class AppenderChannel<T> implements Channel<List<T>>, LG4JLoggable {
    /**
     * A functional interface that is used to remove elements from a list.
     *
     * @param <T> the type of elements in the list
     */
    @FunctionalInterface
    public interface RemoveIdentifier<T> {
        /**
         * Compares the specified element with the element at the given index.
         *
         * @param element  the element to be compared
         * @param atIndex  the index of the element to compare with
         * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
         */
        int compareTo(T element, int atIndex );
    }

    public record ReplaceAllWith<T>(List<T> newValues ) {

        public static <T> ReplaceAllWith<T> of(List<T> newValues ) {
            return new ReplaceAllWith<>(newValues);
        }
        public static <T> ReplaceAllWith<T> of(T newValue ) {
            return new ReplaceAllWith<>( List.of(newValue) );
        }
    }

    /**
     * Reducer that disallow duplicates
     * @param <T>
     */
    public static class ReducerDisallowDuplicate<T> implements Reducer<List<T>> {

        @Override
        public List<T> apply(List<T> left, List<T> right) {
            if (left == null) {
                return right;
            }
            for (T rValue : right) {
                // remove duplicate
                if (left.stream().noneMatch(lValue -> Objects.equals(lValue, rValue))) {
                    left.add(rValue);
                }
            }
            return left;

        }
    }

    /**
     * Reducer that allow duplicates
     * @param <T>
     */
    public static class ReducerAllowDuplicate<T> implements Reducer<List<T>> {

        @Override
        public List<T> apply(List<T> left, List<T> right) {
            if (left == null) {
                return right;
            }
            left.addAll(right);
            return left;
        }
    }


    private final Reducer<List<T>> reducer;
    private final Supplier<List<T>> defaultProvider;

    /**
     * Returns an {@link Optional} containing the current reducer if it is non-null.
     *
     * @return an {@code Optional} describing the current reducer wrapped in a non-empty optional,
     *         or an empty optional if no such reducer exists
     */
    @Override
    public Optional<Reducer<List<T>>> getReducer() {
        return ofNullable(reducer);
    }

    /**
     * Returns the default provider or {@code Optional.empty()} if no default provider is set.
     *
     * @return an {@code Optional} containing the default provider, or {@code Optional.empty()}
     */
    @Override
    public Optional<Supplier<List<T>>> getDefault() {
        return ofNullable(defaultProvider);
    }

    /**
     * Constructs a new instance of {@code AppenderChannel} with the specified default provider.
     *
     * @param reducer a binary operator that is used to combine two lists into one
     * @param defaultProvider a supplier for the default list that will be used when no other list is available
     */
    protected AppenderChannel( Reducer<List<T>> reducer,  Supplier<List<T>> defaultProvider ) {
        this.reducer = reducer;
        this.defaultProvider = defaultProvider;
    }

    /**
     * This method removes elements from a given list based on the specified {@link RemoveIdentifier}.
     * It creates a copy of the original list, performs the removal operation, and returns an immutable view of the result.
     *
     * @param list The list from which elements will be removed.
     * @param removeIdentifier An instance of {@link RemoveIdentifier} that defines how to identify elements for removal.
     * @return An unmodifiable view of the modified list with specified elements removed.
     */
    private List<T> remove(List<T> list, RemoveIdentifier<T> removeIdentifier ) {
        var result = new ArrayList<>(list);
        removeFromList(result, removeIdentifier);
        return unmodifiableList(result);
    }

    /**
     * Removes an element from the list that matches the specified identifier.
     *
     * <p>This method iterates over the provided list and removes the first element for which the
     * {@link RemoveIdentifier#compareTo} method returns zero.</p>
     *
     * @param result         the list to be modified
     * @param removeIdentifier the identifier used to find the element to remove
     */
    private void removeFromList(List<T> result, RemoveIdentifier<T> removeIdentifier ) {
        // Use an iterator to safely remove elements during iteration
        var iterator = result.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            T element = iterator.next();
            if (removeIdentifier.compareTo(element, index++) == 0) {
                iterator.remove();
            }
        }
    }

    /**
     * Represents a record for data removal operations with generic types.
     * 
     * @param <T> the type of elements in the old values list
     */
    record RemoveData<T>( List<T> oldValues, List<?> newValues) {

        // copy constructor. make sure to copy the list to make them modifiable
        public RemoveData {
            oldValues = new ArrayList<>(oldValues);
            newValues = new ArrayList<>(newValues);
        }
    };

    /**
     * Evaluates the removal of identifiers from the new values list and updates the RemoveData object accordingly.
     *
     * @param oldValues   a {@code List} of old values
     * @param newValues   a {@code List} of new values containing {@code RemoveIdentifier}s to be evaluated for removal
     * @return            a {@literal RemoveData<T>} object with updated old and new values after removing identifiers
     */
    @SuppressWarnings("unchecked")
    private RemoveData<T> evaluateRemoval(List<T> oldValues, List<?> newValues ) {

        final var result = new RemoveData<>( oldValues, newValues );

        newValues.stream()
                 .filter( value -> value instanceof RemoveIdentifier<?> )
                 .forEach( value -> {
                        result.newValues().remove( value );
                        var removeIdentifier = (RemoveIdentifier<T>) value;
                        removeFromList( result.oldValues(), removeIdentifier );

                });
        return result;

    }

    @SuppressWarnings("unchecked")
    protected List<T> validateNewValues(List<?> list  ) {
        return (List<T>)list;
    }

    /**
     * Updates the value for a given key in the channel.
     * 
     * @param key     The key for which the value needs to be updated.
     * @param oldValue    The old value that is being replaced.
     * @param newValue    The new value to be set. If null, the old value will be returned.
     * 
     * @return          The updated old value or the new value if the update was successful.
     * 
     * @throws UnsupportedOperationException   If the channel does not support updates, typically due to an immutable list being used.
     */
    @SuppressWarnings("unchecked")
    public final Object update( String key, Object oldValue, Object newValue) {
        ;
        if( isMarkedForReset(newValue) ) {
            // if newValue is null or MARK_FOR_DELETION, the channel is reset to the default value
            return getDefault().orElse(ArrayList::new).get();
        }
        if( isMarkedForRemoval(newValue) ) {
            return null;
        }

        boolean oldValueIsList = oldValue instanceof List<?>;

        try {
            if( newValue instanceof ReplaceAllWith<?> replaceAll ) {
                return List.copyOf(replaceAll.newValues());
            }
            if( oldValueIsList && newValue instanceof RemoveIdentifier<?> ) {
                return remove( (List<T>)oldValue, (RemoveIdentifier<T>)newValue);
            }
            List<?> list = null;
            if (newValue instanceof List) {
                list = (List<Object>) newValue;
            } else if (newValue.getClass().isArray()) {
                list = Arrays.asList((T[])newValue);
            }
            else {
                list = List.of(newValue);
            }
            if (list.isEmpty()) {
                return oldValue;
            }
            var typedList = validateNewValues(list);
            if( oldValueIsList ) {
                var result = evaluateRemoval( (List<T>)oldValue, typedList );
                return Channel.super.update(key, result.oldValues(), result.newValues());
            }
            return Channel.super.update(key, oldValue, typedList);
        }
        catch (UnsupportedOperationException ex) {
            log.error("Unsupported operation: probably because the appendable channel has been initialized with a immutable List. Check please !");
            throw ex;
        }
    }

}