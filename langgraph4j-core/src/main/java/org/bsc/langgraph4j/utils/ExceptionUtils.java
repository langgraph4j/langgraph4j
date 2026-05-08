package org.bsc.langgraph4j.utils;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public interface ExceptionUtils {

    static Throwable getRootCause(Throwable throwable) {
        requireNonNull(throwable);
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
                .reduce((first, second) -> second)
                .orElse(throwable);
    }

    static <Ex extends Throwable> Optional<Ex> findCauseByType( Throwable throwable, Class<Ex> type ) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause )
                .filter( ( ex ) -> ex.getClass().equals(type) )
                .reduce((first, second) -> second)
                .map( type::cast );
    }
}
