package org.bsc.langgraph4j.studio.jetty;

import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;


/**
 * Represents a streaming server for LangGraph using Jetty.
 * LangGraphStreamingServer is an interface that represents a server that supports streaming
 * of LangGraph. Implementations of this interface can be used to create a web server that exposes an API for interacting with compiled language graphs.
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
     * Builder class for constructing LangGraphStreamingServerJetty instances.
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

        /**
         * Builds and returns a LangGraphStreamingServerJetty instance.
         *
         * @return a new LangGraphStreamingServerJetty instance
         * @throws NullPointerException if the stateGraph is null
         * @throws Exception if an error occurs during server setup
         */
        public LangGraphStudioServer4Jetty build() throws Exception {

            Server server = new Server();

            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);

            var resourceHandler = new ResourceHandler();

            var baseResource = ResourceFactory.of(resourceHandler).newClassLoaderResource("webapp");
            resourceHandler.setBaseResource(baseResource);

            resourceHandler.setDirAllowed(true);

            var context = new ServletContextHandler(ServletContextHandler.SESSIONS);

            context.setSessionHandler(new org.eclipse.jetty.ee10.servlet.SessionHandler());

            context.addServlet(new ServletHolder(new GraphInitServlet(instanceMap)), "/init");

            context.addServlet(new ServletHolder(new GraphStreamServlet(instanceMap)), "/stream/*");

            if( filterHandler != null ) {
                filterHandler.accept(context);
            }
            var handlerList = new Handler.Sequence(resourceHandler, context  );

            server.setHandler(handlerList);

            return new LangGraphStudioServer4Jetty(server);
        }
    }
}
