package org.bsc.langgraph4j.serializer.plain_text;

import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;


public abstract class PlainTextStateSerializer<State extends AgentState> extends StateSerializer<State> {

    protected PlainTextStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);
    }


}
