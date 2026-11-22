package org.bsc.langgraph4j.studio.jetty;

import jakarta.servlet.DispatcherType;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.ResourceFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


/**
 * Hosts the Studio UI and graph API in a shared Jetty servlet context.
 * Servlet filters apply to both API requests and static resources, including CORS preflight.
 * Configure instances and filters before starting; Jetty manages request threads and sessions.
 */
public class LangGraphStudioServer4Jetty implements LangGraphStudioServer {

    final Server server;

    /**
     * Constructs a LangGraphStreamingServerJetty with the specified server.
     *
     * @param server the server to be used by the streaming server
     * @throws NullPointerException if the server is null
     */
    private LangGraphStudioServer4Jetty(Server server) {
        Objects.requireNonNull(server, "server cannot be null");
        this.server = server;
    }

    /**
     * Starts the streaming server asynchronously.
     *
     * @return a CompletableFuture that completes when the server has started
     * @throws Exception if an error occurs while starting the server
     */
    public CompletableFuture<Void> start() throws Exception {
        return CompletableFuture.runAsync(() -> {
            try {
                server.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Runnable::run);
    }

    /**
     * Creates a new Builder for LangGraphStreamingServerJetty.
     *
     * @return a new Builder instance
     */
    static public Builder builder() {
        return new Builder();
    }

    /**
     * Configures the Jetty connector, graph servlets, static resources, and optional filters.
     * Use on a single configuration thread before starting the constructed server.
     */
    public static class Builder {
        private int port = 8080;
        private final Map<String,Instance> instanceMap = new HashMap<>();
        private Consumer<ServletContextHandler> filterHandler;

        /**
         * Sets the port for the server.
         *
         * @param port the port number
         * @return the Builder instance
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder instance(String id, Instance instance) {
            instanceMap.put(id, instance);
            return this;
        }

        public Builder instance( Map.Entry<String,Instance> entry) {
            instanceMap.put(entry.getKey(), entry.getValue());
            return this;
        }

        public Builder filter( Consumer<ServletContextHandler> filterHandler ) {
            this.filterHandler = filterHandler;
            return this;
        }

        public Builder cors(String... allowedOrigins) {
            final var filter = new CorsFilter(allowedOrigins);
            return filter( ctx -> ctx.addFilter(filter, "/*", EnumSet.of(DispatcherType.REQUEST)));
        }

        /**
         * Builds an unstarted Studio server with a shared servlet context for the UI and API.
         *
         * <p>Static resources use the default servlet so OPTIONS requests reach configured
         * filters before resource handling. Call {@link LangGraphStudioServer4Jetty#start()}
         * after completing configuration; Jetty owns the resources for the server lifetime.
         *
         * @return a new, non-null server configured from this builder
         * @throws Exception if server or classpath resource setup fails
         */
        public LangGraphStudioServer4Jetty build() throws Exception {

            // Configure the listening connector without starting request processing.
            Server server = new Server();

            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);

            // Keep API and static requests inside the same filter and session lifecycle.
            var context = new ServletContextHandler(ServletContextHandler.SESSIONS);

            context.setSessionHandler(new org.eclipse.jetty.ee10.servlet.SessionHandler());

            context.addServlet(new ServletHolder(new GraphInitServlet(instanceMap)), "/init");

            context.addServlet(new ServletHolder(new GraphStreamServlet(instanceMap)), "/stream/*");

            context.setBaseResource(ResourceFactory.of(context).newClassLoaderResource("webapp"));
            context.setWelcomeFiles(new String[]{"index.html"});
            var defaultServlet = new ServletHolder(new DefaultServlet());
            defaultServlet.setInitParameter("dirAllowed", "true");
            // The default mapping serves assets while the more specific API mappings take precedence.
            context.addServlet(defaultServlet, "/");

            // Install filters before startup so preflight cannot bypass their CORS headers.
            if( filterHandler != null ) {
                filterHandler.accept(context);
            }
            server.setHandler(context);

            return new LangGraphStudioServer4Jetty(server);
        }
    }
}
