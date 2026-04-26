package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;

import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Represents the outcome of a {@link CommandAction} within a LangGraph4j graph.
 * A {@code Command} encapsulates instructions for the graph's next step, including
 * a target node to transition to and a map of updates to be applied
 * to the {@link org.bsc.langgraph4j.state.AgentState}.
 *
 * @param gotoNode containing the name of the next node to execute.
 * @param update   A {@link Map} containing key-value pairs representing updates
 *                 to be merged into the current agent state. An empty map indicates
 *                 no state updates.
 */
public record Command(String gotoNode, Map<String,Object> update) {
    private static final Command EMPTY_COMMAND = new Command( Map.of() );

    public static Command emptyCommand() {
        return EMPTY_COMMAND;
    }

    public String gotoNode() {
        return Objects.requireNonNull(gotoNode, "gotoNode cannot be null");
    }

    public Map<String,Object> update() {
        return ofNullable(update).orElseGet(Map::of);
    }

    public Optional<String> gotoNodeSafe() {
        return ofNullable(gotoNode);
    }

    /**
     * check for null values
     */
    public Command {
        if( gotoNode == null && update == null ) {
            throw new IllegalArgumentException("gotoNode and update cannot both be null");
        }
    }

    /**
     * Constructs a {@code Command} that specifies only the next node to transition to,
     * with no state updates.
     * If {@code gotoNode} is null, it will be treated as an empty {@link Optional}.
     *
     * @param gotoNode The name of the next node to transition to. Can be null.
     */
    public Command( String gotoNode ) {
        this( gotoNode, null );
    }

    public Command( Map<String,Object> update ) {
        this( null, update );
    }

    /**
     * Returns a new Command whose update map is the merge of the current update map
     * and the provided map. Values in {@code newUpdate} override existing keys.
     *
     * @param newUpdate  the map of state updates to merge into the current update map;
     *
     */
    public Command withMergedUpdate( Map<String,Object> newUpdate ) {
        return new Command( gotoNode, mergeMap( update(), newUpdate ) );
    }

    /**
     * Returns a new {@code Command} whose update map is the merge of the current update map
     * and the provided {@code newUpdate} map, using a custom {@code mergeFunction} to resolve
     * conflicts when both maps contain a value for the same key.
     *
     * <p>When a key exists in both the current update and {@code newUpdate}, the
     * {@code mergeFunction} is called with the two conflicting values (current value first,
     * new value second) to produce the value that will appear in the resulting map.
     * Keys present in only one map are included as-is.
     *
     * <p>The resulting {@code Command} preserves the same {@code gotoNode} as this instance.
     *
     * <p>Example — keep the longer string on collision:
     * <pre>{@code
     * Command merged = command.withMergedUpdate(
     *     Map.of("key", "newValue"),
     *     (existing, incoming) -> existing.toString().length() >= incoming.toString().length()
     *                             ? existing : incoming
     * );
     * }</pre>
     *
     * @param newUpdate      the map of state updates to merge into the current update map;
     * @param mergeFunction  a {@link BinaryOperator} invoked when the same key is present in
     *                       both maps; the first argument is the value from the current update,
     *                       the second argument is the value from {@code newUpdate};
     *                       must not be {@code null}
     * @return a new {@code Command} with the merged update map and the same target node
     * @throws NullPointerException if {@code mergeFunction} is {@code null}
     * @see #withMergedUpdate(Map)
     */
    public Command withMergedUpdate( Map<String,Object> newUpdate, BinaryOperator<Object> mergeFunction ) {
        return new Command( gotoNode, mergeMap( update(), newUpdate, mergeFunction ) );
    }

    @Override
    public String toString() {
        if( update == null && gotoNode == null ) {
            return "empty command";
        }
        if( update == null ) {
            return "goto node '%s'%n".formatted( gotoNode );
        }

        return "goto node '%s' with update %s%n".formatted( gotoNode, CollectionsUtils.toString(update));
    }
}