package org.bsc.langgraph4j.state;


import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * A factory interface for creating instances of {@link AgentState}.
 *
 * @param <State> the type of the agent state
 */
public interface AgentStateFactory<State extends AgentState> extends Function<Map<String,Object>, State> {


    default Map<String,Object> initialDataFromSchema( Map<String,Channel<?>> schema  ) {
        return schema.entrySet().stream()
                .filter( c -> c.getValue().getDefault().isPresent() )
                .map( e ->
                    e.getValue().getDefault()
                            .flatMap( v -> ofNullable(v.get()) )
                            .map( v -> Map.entry( e.getKey(), v) )
                            .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Initializes this state from the given schema.
     * This method processes the provided schema map to extract default values for each channel that has a defined default value.
     *
     * @param schema the schema map containing channel definitions
     * @return the initialized state with default values applied
     */
    default State applyFromSchema(Map<String,Channel<?>> schema )  {
        return apply( initialDataFromSchema(schema) );
    }

}