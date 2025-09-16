package org.bsc.langgraph4j.jetty;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * >> FOR TEST ONLY <<
 * Custom CORS Filter for Jetty — no external dependencies.
 * Supports:
 * - Allowed origins (whitelist)
 * - Credentials support
 * - Preflight (OPTIONS) handling
 * - Standard headers and methods
 */
public class CorsFilter implements Filter {

    // 👇 Configure allowed origins — use config file or DB in production
    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(
            List.of("http://localhost:1234")
    );

    private static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Content-Type, Authorization, X-Requested-With, Accept";
    private static final int MAX_AGE_SECONDS = 1800; // 30 minutes

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Get the Origin header from the client
        String origin = req.getHeader("Origin");

        // ✅ Only allow if origin is in our whitelist
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true"); // Required if frontend uses credentials
        }

        // Always set these for all requests (even if origin not allowed — avoids preflight errors)
        res.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
        res.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        res.setHeader("Access-Control-Max-Age", String.valueOf(MAX_AGE_SECONDS));

        // ✅ Handle preflight (OPTIONS) request — respond immediately
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setStatus(HttpServletResponse.SC_OK);
            return; // Do NOT call chain.doFilter() — we handled it
        }
        // ✅ For non-OPTIONS requests (GET, POST, etc.), continue the filter chain
        chain.doFilter( new FakeSessionRequestWrapper(req), res);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        System.out.println("✅ CustomCorsFilter initialized");
    }

    @Override
    public void destroy() {
        System.out.println("❌ CustomCorsFilter destroyed");
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
