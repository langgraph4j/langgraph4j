package org.bsc.langgraph4j.studio.springboot.agui;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentRegistry;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioJacksonSerializer;
import org.bsc.langgraph4j.studio.agui.AGUILangGraphStudioServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public abstract class AGUILangGraphStudioConfig {

    @Bean
    public AGUILangGraphStudioJacksonSerializer aguiSerializer() {

        return new AGUILangGraphStudioJacksonSerializer();

    }

    @Bean
    protected abstract AGUIAgentRegistry agentRegistry() throws Exception;

    @Bean
    public ServletRegistrationBean<AGUILangGraphStudioServer.InitServlet> initServletBean( AGUIAgentRegistry agentRegistry, AGUILangGraphStudioJacksonSerializer aguiSerializer ) {

        var initServlet = new AGUILangGraphStudioServer.InitServlet(agentRegistry, aguiSerializer);
        var bean = new ServletRegistrationBean<>(
                initServlet, "/init");
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    public ServletRegistrationBean<AGUILangGraphStudioServer.StreamServlet> streamingServletBean( AGUIAgentRegistry agentRegistry, AGUILangGraphStudioJacksonSerializer aguiSerializer ) {

        var initServlet = new AGUILangGraphStudioServer.StreamServlet(agentRegistry,aguiSerializer);
        var bean = new ServletRegistrationBean<>(
                initServlet, "/stream/*");
        bean.setLoadOnStartup(1);
        bean.setInitParameters(Map.of("asyncContextTimeout", "300000"));

        return bean;
    }

}
