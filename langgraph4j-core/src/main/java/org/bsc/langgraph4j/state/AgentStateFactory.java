package org.bsc.langgraph4j.state;


import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * A factory interface for creating instances of {@link AgentState}.
 *
 * @param <State> the type of the agent state
 */
public interface AgentStateFactory<State extends AgentState> extends Function<Map<String,Object>, State> {

    /**
     * Initializes the state with default values from the provided schema.
     * This method processes the provided schema to extract default values for each channel that has a defined default value.
     *
     * @param schema the schema containing channel definitions
     * @return a map containing the initial data with default values applied
     */
    default Map<String,Object> initialDataFromSchema( AgentState.Schema schema  ) {
        return initialDataFromSchema( requireNonNull(schema,"schema cannot be null").channels() );
    }

    /**
     * Initializes the state with default values from the provided channels.
     * This method processes the provided channels to extract default values for each channel that has a defined default value.
     *
     * @param channels the map of channel definitions
     * @return a map containing the initial data with default values applied
     */
    default Map<String,Object> initialDataFromSchema( Map<String,Channel<?>> channels  ) {
        return channels.entrySet().stream()
                .filter( c -> c.getValue().getDefault().isPresent() )
                .collect(Collectors.toMap(Map.Entry::getKey, e ->
                        e.getValue().getDefault().get().get()
                ));
    }

    /**
     * Initializes this state from the given schema.
     * This method processes the provided schema to extract default values for each channel that has a defined default value.
     *
     * @param schema the schema containing channel definitions
     * @return the initialized state with default values applied
     */
    default State applyFromSchema( AgentState.Schema schema )  {
        return applyFromSchema( requireNonNull(schema,"schema cannot be null").channels() );
    }

    /**
     * Initializes this state from the given schema.
     * This method processes the provided schema map to extract default values for each channel that has a defined default value.
     *
     * @param schema the schema map containing channel definitions
     * @return the initialized state with default values applied
     */
    default State applyFromSchema( Map<String,Channel<?>> schema )  {
        return apply( initialDataFromSchema(schema) );
    }


}