package org.bsc.langgraph4j.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Stateless helpers for inspecting and unwrapping exception cause chains.
 *
 * <p>All methods are static, side-effect free and thread-safe.
 */
public interface ExceptionUtils {

    /**
     * Returns the root cause of the given throwable, i.e. the last element of its cause chain.
     *
     * @param throwable the throwable to inspect; must not be {@code null}
     * @return the deepest cause, or {@code throwable} itself if it has no cause
     * @throws NullPointerException if {@code throwable} is {@code null}
     */
    static Throwable getRootCause(Throwable throwable) {
        requireNonNull(throwable);
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
                .reduce((first, second) -> second)
                .orElse(throwable);
    }

    /**
     * Searches the cause chain (starting from {@code throwable} itself) for a throwable of the given type.
     *
     * <p>The match is on the exact class ({@code getClass().equals(type)}), so subclasses of
     * {@code type} are not matched. If several elements match, the deepest one is returned.
     *
     * @param throwable the head of the cause chain; if {@code null} the result is empty
     * @param type      the exact exception class to look for
     * @param <Ex>      the exception type
     * @return the deepest matching cause, or an empty {@link Optional} if none matches
     */
    static <Ex extends Throwable> Optional<Ex> findCauseByType( Throwable throwable, Class<Ex> type ) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause )
                .filter( ( ex ) -> ex.getClass().equals(type) )
                .reduce((first, second) -> second)
                .map( type::cast );
    }

    /**
     * Tells whether the given throwable is a generic wrapper that can be unwrapped.
     *
     * @param e the throwable to test; must not be {@code null}
     * @return {@code true} if {@code e} is an {@link InvocationTargetException},
     *         {@link CompletionException} or {@link ExecutionException} and has a non-null cause
     * @throws NullPointerException if {@code e} is {@code null}
     * @see #unwrap(Throwable)
     */
    static boolean isWrapper(Throwable e) {
        return (  e instanceof InvocationTargetException
                || e instanceof CompletionException
                || e instanceof ExecutionException) && e.getCause() != null;
    }

    /**
     * Strips generic wrapper exceptions to expose the meaningful underlying cause.
     *
     * <p>Walks the cause chain while {@link #isWrapper(Throwable)} holds and keeps the last
     * element produced by that walk. Note that the walk only emits throwables that are themselves
     * wrappers, so the result is the innermost wrapper (the one whose cause is not a wrapper),
     * not that wrapper's cause.
     *
     * @param e the throwable to unwrap; if {@code null} or not a wrapper it is returned as is
     * @return the innermost wrapper in the chain, or {@code e} if it is not a wrapper
     * @see #isWrapper(Throwable)
     */
    static Throwable unwrap( @NonNull Throwable e) {
        ;
        return Stream.iterate( requireNonNull(e, "Throwable must not be null"),
                        ExceptionUtils::isWrapper,
                        Throwable::getCause)
                .reduce((first, second) -> second)
                .orElse(e);
    }

}
