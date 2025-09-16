package org.bsc.langgraph4j.studio.springboot;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Enumeration;


/**
 * >> FOR TEST ONLY <<
 * Spring Boot configuration for a custom CORS filter.
 * This is intended for testing purposes to allow requests from a local development server.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<Filter> corsFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            String origin = req.getHeader("Origin");
            if ("http://localhost:1234".equals(origin)) {
                res.setHeader("Access-Control-Allow-Origin", origin);
                res.setHeader("Access-Control-Allow-Credentials", "true");
            }

            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                res.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            chain.doFilter( new FakeSessionRequestWrapper(req), response);
        });

        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("inlineCorsFilter");
        return registration;
    }

    static class FakeSessionRequestWrapper extends HttpServletRequestWrapper {
        final HttpSession session;

        public FakeSessionRequestWrapper(HttpServletRequest request) {
            super(request);
            this.session = new FakeSession(request.getServletContext());
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }

        @Override
        public HttpSession getSession() {
            return session;
        }

        @Override
        public String getRequestedSessionId() {
            return session.getId();
        }

    }
    record FakeSession(ServletContext context) implements HttpSession {

            @Override
            public long getCreationTime() {
                return 0;
            }

            @Override
            public String getId() {
                return "SESSION1";
            }

            @Override
            public long getLastAccessedTime() {
                return 0;
            }

            @Override
            public ServletContext getServletContext() {
                return context;
            }

            @Override
            public void setMaxInactiveInterval(int i) {
            }

            @Override
            public int getMaxInactiveInterval() {
                return 0;
            }

            @Override
            public Object getAttribute(String s) {
                return null;
            }

            @Override
            public Enumeration<String> getAttributeNames() {
                return null;
            }

            @Override
            public void setAttribute(String s, Object o) {
            }

            @Override
            public void removeAttribute(String s) {
            }

            @Override
            public void invalidate() {
            }

            @Override
            public boolean isNew() {
                return false;
            }
        }

}