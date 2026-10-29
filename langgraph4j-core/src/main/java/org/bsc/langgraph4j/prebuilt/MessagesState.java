package org.bsc.langgraph4j.prebuilt;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the agent's state as a collection of messages
 *
 * @param <T> the type of messages stored in this state
 */
public class MessagesState<T> extends AgentState {

    public static final String MESSAGES = "messages";
    /**
     * @deprecated Use {@link #MESSAGES} instead. This constant is retained for backward compatibility.
     */
    @Deprecated
    public static final String MESSAGES_STATE = "messages";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_STATE, Channels.appender(ArrayList::new)
    );

    /**
     * Constructs a new instance of MessagesState with the provided initial data.
     *
     * @param initData A {@code Map} containing the initial data for this state object.
     */
    public MessagesState(Map<String, Object> initData) {
        super( initData  );
    }


    /**
     * Retrieves the list of messages.
     *
     * @return the list of messages
     * @throws RuntimeException if the "messages" key is not found in the state
     */
    public List<T> messages() {
        return this.<List<T>>value(MESSAGES_STATE)
                .orElseThrow(() -> new RuntimeException("messages not found"));
    }

    /**
     * Returns the last value in the list, if present.
     *
     * @return an Optional containing the last value if present, otherwise an empty Optional
     */
    public Optional<T> lastMessage() {
        var messages = messages();
        return ( messages.isEmpty() ) ?
                Optional.empty() :
                Optional.of(messages.get( messages.size() - 1 ));
    }

    /**
     * Returns the value at the specified position from the end of the list, if present.
     *
     * @param n the position from the end of the list
     * @return an Optional containing the value at the specified position if present, otherwise an empty Optional
     */
    public Optional<T> lastMinus(int n) {
        var messages = messages();
        if ( n < 0 || messages.isEmpty() )  {
            return Optional.empty();
        }
        var index = messages.size() - n - 1;
        return ( index < 0 ) ?
                Optional.empty() :
                Optional.of(messages.get(index));
    }

}