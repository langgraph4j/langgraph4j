package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.event.*;
import com.agui.community.core.message.Role;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Custom LangGraph4j {@link NodeOutput} implementation that acts as a bridge between the
 * LangGraph4j output streaming and the AG-UI event model.
 * <p>
 * Besides the standard node/state information carried by {@link NodeOutput}, an
 * {@code AGUINodeOutput} also carries a list of AG-UI {@link Event}s that a node execution
 * wants to emit (e.g. text message events, step lifecycle events). Consumers of the graph's
 * streaming output (see {@link AGUIAgentBase#onNextEvents(com.agui.community.core.agent.RunAgentInput, NodeOutput)})
 * can recognize instances of this class and forward its {@link #events()} directly to the
 * AG-UI subscriber.
 *
 * @param <State> the type of the agent state carried by this output
 */
public class AGUINodeOutput<State extends AgentState> extends NodeOutput<State> {

    /**
     * Builder for {@link AGUINodeOutput} instances, used to accumulate the AG-UI
     * {@link Event}s to be carried by the resulting output before binding them to a
     * node id and state via {@link #build(String, AgentState)}.
     */
    static public class Builder {
        protected List<Event> events = new ArrayList<>();

        /**
         * Adds a single AG-UI event to this builder.
         *
         * @param event the event to add
         * @return this builder, for chaining
         */
        public Builder addEvent(Event event) {
            events.add(event);
            return this;
        }

        /**
         * Adds one or more AG-UI events to this builder.
         *
         * @param event the events to add
         * @return this builder, for chaining
         */
        public Builder addEvents(Event ...event) {
            events.addAll(List.of(event));
            return this;
        }

        /**
         * Adds the sequence of events representing a single, non-streamed text message:
         * a {@link TextMessageStartEvent}, a {@link TextMessageContentEvent} carrying the
         * whole content, and a {@link TextMessageEndEvent}.
         *
         * @param messageId the identifier of the message
         * @param role      the role of the message author
         * @param content   the full text content of the message
         * @return this builder, for chaining
         */
        public Builder singleTextContentEvents(String messageId, Role role, String content) {
            return  addEvents( new TextMessageStartEvent(messageId, role),
                    new TextMessageContentEvent(messageId, content),
                    new TextMessageEndEvent(messageId));
        }

        /**
         * Adds a {@link StepStartedEvent} for the given step (node) name.
         *
         * @param stepName the name of the step/node that started
         * @param rawEvent an optional raw event payload to attach, may be {@code null}
         * @return this builder, for chaining
         */
        public Builder stepStartedEvent(String stepName, @Nullable  Object rawEvent) {
            return  addEvents( new StepStartedEvent(
                    stepName,
                    System.currentTimeMillis(),
                    rawEvent));
        }

        /**
         * Adds a {@link StepStartedEvent} for the given step (node) name, with no raw payload.
         *
         * @param stepName the name of the step/node that started
         * @return this builder, for chaining
         */
        public Builder stepStartedEvent(String stepName) {
            return  stepStartedEvent(stepName, null);
        }

        /**
         * Adds a {@link StepFinishedEvent} for the given step (node) name.
         *
         * @param stepName the name of the step/node that finished
         * @param rawEvent an optional raw event payload to attach, may be {@code null}
         * @return this builder, for chaining
         */
        public Builder stepFinishedEvent(String stepName, @Nullable  Object rawEvent) {
            return  addEvents( new StepFinishedEvent(
                    stepName,
                    System.currentTimeMillis(),
                    rawEvent));
        }

        /**
         * Adds a {@link StepFinishedEvent} for the given step (node) name, with no raw payload.
         *
         * @param stepName the name of the step/node that finished
         * @return this builder, for chaining
         */
        public Builder stepFinishedEvent(String stepName) {
            return  stepFinishedEvent(stepName, null);
        }

        /**
         * Builds the {@link AGUINodeOutput}, binding the accumulated events to the given
         * node id and state.
         *
         * @param nodeId the identifier of the node that produced this output
         * @param state  the agent state associated with this output
         * @param <State> the type of the agent state
         * @return the newly built node output
         */
        public <State extends AgentState> AGUINodeOutput<State> build(String nodeId, State state) {
            return new AGUINodeOutput<>(nodeId, state, events);
        }
    }

    /**
     * Creates a new {@link Builder} to accumulate AG-UI events before building an
     * {@link AGUINodeOutput}.
     *
     * @param <State> the type of the agent state
     * @return a new builder instance
     */
    public static <State extends AgentState> Builder builder() {
        return new Builder();
    }


    private final List<Event> events;

    /**
     * Creates a new node output carrying the given AG-UI events.
     *
     * @param node   the identifier of the node that produced this output
     * @param state  the agent state associated with this output
     * @param events the AG-UI events to carry; must not be {@code null}
     */
    protected AGUINodeOutput(String node, State state, List<Event> events) {
        super(node, state);
        this.events = requireNonNull(events, "events must not be null");
    }

    /**
     * Returns the AG-UI events carried by this node output.
     *
     * @return the list of AG-UI events
     */
    public List<Event> events() {
        return events;
    }

}
