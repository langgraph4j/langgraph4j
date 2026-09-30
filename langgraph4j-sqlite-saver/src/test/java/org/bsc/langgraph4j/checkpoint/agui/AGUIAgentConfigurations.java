package org.bsc.langgraph4j.checkpoint.agui;

import com.agui.json.AGUIJacksonSerializer;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AGUIAgentConfigurations {

    @Bean
    public com.agui.community.core.serialization.Serializer aguiSerializer() {
        return new AGUIJacksonSerializer();
    }

    @Bean
    AGUIAgentRegistry createAgentExecutor() {

        return new AGUIAgentRegistry(
                new AGUIAgentHITL(),
                new AGUIAgentWithError());
    }


}
