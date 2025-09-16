package org.bsc.langgraph4j.studio.springboot;

import jakarta.annotation.PostConstruct;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

public abstract class LangGraphStudioConfig {

    private Map<String, LangGraphStudioServer.Instance> instanceMap;

    public abstract Map<String, LangGraphStudioServer.Instance> instanceMap();

    @PostConstruct
    public void init() {
        instanceMap = instanceMap();
    }

    @Bean
    public ServletRegistrationBean<LangGraphStudioServer.GraphInitServlet> initServletBean() {

        var initServlet = new LangGraphStudioServer.GraphInitServlet(instanceMap);
        var bean = new ServletRegistrationBean<>(
                initServlet, "/init");
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    public ServletRegistrationBean<LangGraphStudioServer.GraphStreamServlet> streamingServletBean() {

        var initServlet = new LangGraphStudioServer.GraphStreamServlet(instanceMap);
        var bean = new ServletRegistrationBean<>(
                initServlet, "/stream/*");
        bean.setLoadOnStartup(1);
        return bean;
    }

}
