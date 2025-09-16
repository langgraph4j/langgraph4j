package org.bsc.quarkus;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "GraphStreamServlet", urlPatterns = "/stream/*", asyncSupported = true)
public class GraphStreamServletProxy extends HttpServlet {

    private final Servlet servlet;

    public GraphStreamServletProxy( Map<String, LangGraphStudioServer.Instance> instanceMap ) {
        super();
        servlet = new LangGraphStudioServer.GraphStreamServlet( instanceMap );
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        servlet.init(config);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        servlet.service(req, res);
    }

}
