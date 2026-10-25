package org.bsc.langgraph4j.studio.springboot.agui;

import org.bsc.langgraph4j.agui.AGUISampleAgent;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AGUILangGraphSampleConfig extends AGUILangGraphStudioBaseConfig {

    @Override
    protected AGUIAgentRegistry agentRegistry() throws Exception {

        return new AGUIAgentRegistry(
                AGUISampleAgent.baseAgent("base-agent"),
                AGUISampleAgent.agentWithInterruption("agent-with-interruption")
        );
    }
}
