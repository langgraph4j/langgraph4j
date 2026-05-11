package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agent.*;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.messages.Message;

import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * Represents the core component responsible for executing agent logic.
 * It includes methods for building and managing the execution graph,
 * as well as handling agent actions and state transitions.
 *
 * @author lambochen
 */
public interface AgentExecutor {

    /**
     * Class responsible for building a state graph.
     */
    class Builder extends ReactAgentBuilder<Builder, State> {

        public Builder() {
        }

        public Builder(ReactAgentBuilder<?, State> builder) {
            super(builder);
        }

        /**
         * Builds and returns a StateGraph with the specified configuration.
         * Initializes the stateSerializer if it's null. Then, constructs a new StateGraph object using the provided schema
         * and serializer, adds an initial edge from the START node to "agent", and then proceeds to add nodes for "agent" and
         * "action". It also sets up conditional edges from the "agent" node based on whether or not to continue.
         *
         * @return A configured StateGraph object.
         * @throws GraphStateException If there is an issue with building the graph state.
         */
        public StateGraph<State> build( ) throws GraphStateException {

            final var callModelAction = new CallModelAction<State>(this );

            final var executeToolsAction = new ExecuteToolsAction<State>( tools() );

            return agentBuilder
                    .stateSerializer( ofNullable(stateSerializer)
                            .orElseGet( () -> new SpringAIJacksonStateSerializer<>(State::new) ) )
                    .schema( ofNullable(schema).orElse( MessagesState.SCHEMA) )
                    .callModelAction( callModelAction )
                    .executeToolsAction( executeToolsAction )
                    .build();

        }

    }

    /**
     * Returns a new instance of {@link Builder}.
     *
     * @return a new {@link Builder} object
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Represents the state of an agent in a system.
     * This class extends {@link AgentState} and defines constants for keys related to input, agent outcome,
     * and intermediate steps. It includes a static map schema that specifies how these keys should be handled.
     */
    class State extends MessagesState<Message> {

        /**
         * Constructs a new State object using the initial data provided in the initData map.
         *
         * @param initData the map containing the initial settings for this state
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

    }

}