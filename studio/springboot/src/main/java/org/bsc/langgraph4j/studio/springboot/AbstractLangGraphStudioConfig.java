package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.studio.LangGraphStudioServer;

import java.util.Map;


/**
 * Abstract base class for LangGraph Studio configuration, simplifying the setup for a single flow.
 * <p>
 * This class is designed for scenarios where only one graph (flow) needs to be exposed
 * through the LangGraph Studio.
 *
 * @deprecated This class is scheduled for removal. Implement {@link LangGraphStudioConfig} directly
 *             and provide a custom implementation for the {@link #instanceMap()} method to configure
 *             one or more graph instances.
 */
@Deprecated(forRemoval = true)
public abstract class AbstractLangGraphStudioConfig extends LangGraphStudioConfig {

    public abstract LangGraphFlow getFlow();

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return Map.of("sample", getFlow().toInstance());
    }

}
