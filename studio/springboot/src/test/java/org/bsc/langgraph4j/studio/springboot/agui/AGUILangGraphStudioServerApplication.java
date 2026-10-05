package org.bsc.langgraph4j.studio.springboot.agui;

import org.bsc.langgraph4j.studio.springboot.CORSFilterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AGUILangGraphSampleConfig.class,
        CORSFilterConfig.class})
public class AGUILangGraphStudioServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AGUILangGraphStudioServerApplication.class, args);
    }
}
