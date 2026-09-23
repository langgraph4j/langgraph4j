package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import org.bsc.langgraph4j.*;

import java.util.concurrent.Flow;


/**
 * Represents an agent compliant with the AG-UI protocol, capable of running against a
 * {@link RunAgentInput} and streaming back a reactive sequence of {@link Event}s.
 * <p>
 * Implementations are expected to bridge a langgraph4j graph (or any other execution engine)
 * to the AG-UI event model, exposing a unique identifier and an asynchronous execution entry point.
 */
public interface AGUIAgent extends LG4JLoggable {

    /**
     * Returns the unique identifier of this agent.
     * it will be referenced in the AG-UI protocol to distinguish between different agents and their runs.
     *
     * @return the agent id
     */
    String id();

    /**
     * Runs the agent with the given input and returns a {@link Flow.Publisher} that emits the
     * resulting AG-UI {@link Event}s as they are produced.
     *
     * @param input the input describing the run to execute (e.g. thread id, messages, state, tools)
     * @return a publisher streaming the events generated during the run
     */
    Flow.Publisher<? extends Event> run(RunAgentInput input);

}
