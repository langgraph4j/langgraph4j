package org.bsc.langgraph4j.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.async.v5.BlockingQueueProcessor;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.dsl.JsonDslGenerator;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.subgraph.SubGraphOutput;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;


/**
 * Interface for a LangGraph Streaming Server.
 * Provides methods to start the server and manage streaming of graph data.
 */
public final class LG4JEmbedViewerService implements LG4JLoggable {

    /**
     * Servlet for handling graph stream requests.
     */
    private class LG4JViewerServlet extends HttpServlet {

        private final long asyncContextTimeout;

        private LG4JViewerServlet( long asyncContextTimeout ) {
            this.asyncContextTimeout = asyncContextTimeout;
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
        }

        /**
         * Handles GET requests to retrieve the graph initialization data.
         *
         * @param request  the HTTP request.
         * @param response the HTTP response.
         * @throws ServletException if a servlet error occurs.
         * @throws IOException      if an I/O error occurs.
         */
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");


            final String resultJson = objectMapper.writeValueAsString(initGraphData);
            log.trace("{}", resultJson);

            // Start asynchronous processing
            final PrintWriter writer = response.getWriter();
            writer.println(resultJson);
            writer.close();
        }

        /**
         * Serializes the output to the given writer.
         *
         * @param writer   the writer to serialize to.
         * @param threadId the ID of the thread.
         * @param output   the output to serialize.
         */
        private void serializeOutput(PrintWriter writer,
                                     String threadId,
                                     NodeOutput<? extends AgentState> output) {
            try {
                final var outputAsString = objectMapper
                        .writer()
                        .withAttribute( "threadId", threadId )
                        .writeValueAsString(output);
                writer.println(outputAsString);
            } catch (IOException e) {
                log.warn("error serializing state", e);
            }
        }


        /**
         * Handles POST requests to stream graph data.
         *
         * @param req  the HTTP req.
         * @param resp the HTTP resp.
         * @throws ServletException if a servlet error occurs.
         * @throws IOException      if an I/O error occurs.
         */
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setHeader("Accept", "application/json");
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            final var threadId = ofNullable(req.getParameter("thread"))
                    .orElseThrow(() -> new IllegalStateException("Missing thread id!"));

            final PrintWriter writer = resp.getWriter();

            // Start asynchronous processing
            var asyncContext = req.startAsync();
            asyncContext.setTimeout(asyncContextTimeout);

            try (final AsyncGeneratorFlow.Generator<? extends NodeOutput<? extends AgentState>> generator = AsyncGeneratorFlow.builder()
                    .processor(processor)
                    .executor(ForkJoinPool.commonPool())
                    .build()) {
                generator.forEachAsync(s -> {
                            try {
                                serializeOutput(writer, threadId, s);
                                writer.println();
                                writer.flush();
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                throw new CompletionException(e);
                            }
                        })
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("graph iteration completed with error", ex);
                            } else {
                                log.info("graph iteration completed with result {}!", result);
                            }

                            //writer.close();
                            asyncContext.complete();

                        });

            } catch (Throwable e) {
                log.error("Error streaming", e);
                throw new ServletException(e);
            }
        }

    }

    public static class Builder {
        private String id = "graph_instance";
        private String title;
        private String diagram;
        private long asyncContextTimeout = 300_000;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder diagram(StateGraph<? extends AgentState> stateGraph) {
            requireNonNull(stateGraph, "stateGraph cannot be null");
            this.diagram = stateGraph.reduce(new JsonDslGenerator<>());
            return this;
        }

        public Builder asyncContextTimeout(long asyncContextTimeout) {
            this.asyncContextTimeout = asyncContextTimeout;
            return this;
        }

        public LG4JEmbedViewerService build() {
            return new LG4JEmbedViewerService(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InitGraphData initGraphData;
    public final AsyncGeneratorFlow.Processor<NodeOutput<? extends AgentState>> processor = new BlockingQueueProcessor<>();
    private final HttpServlet viewerServlet;


    public LG4JEmbedViewerService(Builder builder) {
        this.initGraphData = new InitGraphData(builder.id, builder.title, builder.diagram);
        this.viewerServlet = new LG4JViewerServlet(builder.asyncContextTimeout);

        final var module = new SimpleModule();
        module.addSerializer(InitGraphData.class, new InitGraphDataSerializer(InitGraphData.class));
        module.addSerializer(NodeOutput.class, new NodeOutputSerializer());
        objectMapper.registerModule(module);

    }

    public <R> R registerServlet(BiFunction<String, HttpServlet, R> servletRegistrar) {
        return servletRegistrar.apply("/viewer/*", viewerServlet);
    }

    public void dispatchAsync(NodeOutput<? extends AgentState> output) {

        processor.dispatchAsync( AsyncGenerator.Data.of(output));
        if(output.isEND() && !(output instanceof SubGraphOutput<?>) ) {
            processor.dispatchAsync( AsyncGenerator.Data.done(output) );
        }

    }
}