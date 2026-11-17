package org.bsc.langgraph4j.annotation;

/**
 * Marker interface for classes that expose graph actions as methods annotated with
 * {@link NodeAction} or {@link EdgeCondition}.
 *
 * @since 1.9.6
 * @see ActionCatalogRepository#of(ActionCatalog)
 */
public interface ActionCatalog {

    @NodeAction( name = "doNothing", description = "No operation action")
    default void doNothing() {}
}
