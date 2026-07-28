package org.bsc.langgraph4j.studio.springboot;

import jakarta.servlet.http.HttpServlet;
import org.bsc.langgraph4j.studio.LG4JEmbedViewerService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

public abstract class LG4JEmbedViewerConfig {

    protected abstract  LG4JEmbedViewerService viewerService();

    @Bean
    public ServletRegistrationBean<HttpServlet> initViewerServletBean() {

        return viewerService().registerServlet( ( path, servlet ) ->{
            final var bean = new ServletRegistrationBean<>(servlet, path);
            bean.setLoadOnStartup(1);
            return bean;
        });
    }

}
