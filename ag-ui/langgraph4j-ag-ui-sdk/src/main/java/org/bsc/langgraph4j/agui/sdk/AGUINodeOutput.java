package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.event.*;
import com.agui.community.core.message.Role;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class AGUINodeOutput<State extends AgentState> extends NodeOutput<State> {

    static public class Builder {
        protected List<Event> events = new ArrayList<>();


        public Builder addEvent(Event event) {
            events.add(event);
            return this;
        }

        public Builder addEvents(Event ...event) {
            events.addAll(List.of(event));
            return this;
        }

        public Builder singleTextContentEvents(String messageId, Role role, String content) {
            return  addEvents( new TextMessageStartEvent(messageId, role),
                    new TextMessageContentEvent(messageId, content),
                    new TextMessageEndEvent(messageId));
        }

        public Builder stepStartedEvent(String stepName, @Nullable  Object rawEvent) {
            return  addEvents( new StepStartedEvent(
                    stepName,
                    System.currentTimeMillis(),
                    rawEvent));
        }

        public Builder stepStartedEvent(String stepName) {
            return  stepStartedEvent(stepName, null);
        }

        public Builder stepFinishedEvent(String stepName, @Nullable  Object rawEvent) {
            return  addEvents( new StepFinishedEvent(
                    stepName,
                    System.currentTimeMillis(),
                    rawEvent));
        }

        public Builder stepFinishedEvent(String stepName) {
            return  stepFinishedEvent(stepName, null);
        }

        public <State extends AgentState> AGUINodeOutput<State> build(String nodeId, State state) {
            return new AGUINodeOutput<>(nodeId, state, events);
        }
    }

    public static <State extends AgentState> Builder builder() {
        return new Builder();
    }


    private final List<Event> events;

    protected AGUINodeOutput(String node, State state, List<Event> events) {
        super(node, state);
        this.events = requireNonNull(events, "events must not be null");
    }

    public List<Event> events() {
        return events;
    }

}
