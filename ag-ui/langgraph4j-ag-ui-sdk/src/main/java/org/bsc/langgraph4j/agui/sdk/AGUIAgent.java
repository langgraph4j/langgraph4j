package org.bsc.langgraph4j.agui.sdk;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import org.bsc.langgraph4j.*;

import java.util.concurrent.Flow;


public interface AGUIAgent extends LG4JLoggable {

    String id();

    Flow.Publisher<? extends Event> run(RunAgentInput input);

}
