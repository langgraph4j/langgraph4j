package org.bsc.langgraph4j.agui.sdk;

import com.agui.json.AGUIJacksonSerializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AGUIApplication {

    @Bean
    public com.agui.community.core.serialization.Serializer aguiSerializer() {

        return new AGUIJacksonSerializer();

    }

    @Bean
    AGUIAgentRegistry createAgentExecutor() {

        return new AGUIAgentRegistry(
                new AGUIAgentExecutorINTERRUPT("INTERRUPT"),
                new AGUIAgentExecutorHITL("HITL"));
    }

    public static void main(String[] args) {
            SpringApplication.run(AGUIApplication.class, args);
        }

}
