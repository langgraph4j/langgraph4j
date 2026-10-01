package org.bsc.langgraph4j.checkpoint.agui;

import com.agui.json.AGUIJacksonSerializer;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentRegistry;
import org.bsc.langgraph4j.agui.sdk.AGUISSEController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(AGUISSEController.class)
public class AGUIAgentApplication {

    public static void main(String[] args) {
            SpringApplication.run(AGUIAgentApplication.class, args);
        }

}
