package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.util.Collection;
import java.util.Optional;

/**
 * Represents an entity that can have different versions associated with it.
 * Experimental feature
 */
public interface HasVersions {

    /**
     * Retrieves a collection of integer versions associated with the specified thread ID.
     *
     * @param threadId the ID of the thread for which to retrieve version information
     * @return a {@code Collection<Integer>} containing the versions, or an empty collection if no versions are found
     */
    Collection<Integer> versionsByThreadId(String threadId );

    /**
     * Retrieves the collection of versions associated with a specific thread ID from the given {@link RunnableConfig}.
     * 
     * @param config The configuration object containing the thread ID information.
     * @return A {@code Collection<Integer>} representing the versions associated with the thread ID, or an empty collection if not specified.
     */
    default Collection<Integer> versionsByThreadId( RunnableConfig config ) {
        return versionsByThreadId( threadId(config) );
    }

    /**
     * Retrieves the last version associated with a specific thread ID.
     *
     * @param threadId The unique identifier of the thread.
     * @return An {@link Optional} containing the last version if found, otherwise an empty {@link Optional}.
     */
    Optional<Integer> lastVersionByThreadId(String threadId );

    /**
         * Retrieves the last version associated with a specific thread ID.
         *
         * @param config The configuration containing the thread ID.
         * @return An {@link Optional} containing the last version if found, or an empty {@link Optional} otherwise.
         */
    default Optional<Integer> lastVersionByThreadId( RunnableConfig config ) {
        return lastVersionByThreadId( threadId(config) );
    }

    String threadId( RunnableConfig config );

}