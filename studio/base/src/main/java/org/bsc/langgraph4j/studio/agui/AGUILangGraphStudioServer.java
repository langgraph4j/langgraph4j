package org.bsc.langgraph4j.studio.agui;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentRegistry;
import org.bsc.langgraph4j.studio.InitGraphData;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public interface AGUILangGraphStudioServer {

    /**
     * Servlet for initializing the graph representation format.
     */
    class InitServlet extends HttpServlet implements LG4JLoggable {

        final AGUIAgentRegistry agentRegistry;
        final AGUILangGraphStudioJacksonSerializer serializer;

        /**
         * Constructs a GraphInitServlet.
         *
         */
        public InitServlet(AGUIAgentRegistry agentRegistry, AGUILangGraphStudioJacksonSerializer serializer) {
            this.agentRegistry = requireNonNull(agentRegistry, "agentRegistry cannot be null");
            this.serializer = requireNonNull(serializer, "serializer cannot be null");
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);

        }

        private Optional<InitGraphData> initGraphDataFromRequest(HttpServletRequest request) {

            return ofNullable(request.getParameter("agent"))
                    .flatMap(agentId -> Optional.of(agentRegistry.agent(agentId))
                            .map(AGUILangGraphStudioAgent.class::cast))
                    .map(AGUILangGraphStudioAgent::toInitGraphData);
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

            final var initGraphData = initGraphDataFromRequest(request);

            var resultJson = "{}";

            if (initGraphData.isPresent()) {
                resultJson = serializer.serialize(initGraphData.get());
            }

            log.trace("{}", resultJson);

            // Start asynchronous processing
            final PrintWriter writer = response.getWriter();
            writer.println(resultJson);
            writer.close();
        }
    }

    /**
     * Servlet for handling graph stream requests.
     */
    class StreamServlet extends HttpServlet implements LG4JLoggable {
        final AGUIAgentRegistry agentRegistry;
        final AGUILangGraphStudioJacksonSerializer aguiSerializer;


        /**
         * Constructs a StreamServlet.
         *
         */
        public StreamServlet(AGUIAgentRegistry agentRegistry, AGUILangGraphStudioJacksonSerializer aguiSerializer) {

            this.agentRegistry = requireNonNull(agentRegistry, "agentRegistry cannot be null");
            this.aguiSerializer = requireNonNull(aguiSerializer, "aguiSerializer cannot be null");

        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
        }


        private Optional<String> agentIdFromRequest(HttpServletRequest request) {

            return ofNullable(request.getPathInfo())
                    .flatMap(p -> Arrays.stream(p.split("/"))
                            .reduce((a, b) -> b));
        }

        /**
         * Cancel running iteration
         *
         */
        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            super.doDelete(req, resp);
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

            final RunAgentInput input = aguiSerializer.deserialize(
                    new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8),   // Java 9+
                    RunAgentInput.class);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Accept", "application/json");
            resp.setContentType("text/event-stream");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("X-Accel-Buffering", "no");   // disable nginx buffering

            final var asyncContextTimeout = ofNullable(getServletConfig().getInitParameter("asyncContextTimeout"))
                    .map(Long::parseLong);

            final var agentId = agentIdFromRequest(req)
                    .orElseThrow(() -> new ServletException("agent id is not found in req"));

            final var agent = agentRegistry.agent(agentId)
                    .map(AGUILangGraphStudioAgent.class::cast)
                    .orElseThrow(() -> new ServletException(format("agent not found with id: [%s]", agentId)));

            //final var session = requireNonNull( req.getSession(true), "session cannot be null");

            // Start asynchronous processing
            final var asyncContext = req.startAsync();
            asyncContextTimeout.ifPresent(asyncContext::setTimeout);
            final var out = resp.getOutputStream();

            final var subscriptionRef = new AtomicReference<Flow.Subscription>();
            final var finished = new AtomicBoolean();

            final Runnable finish = () -> {
                if (finished.compareAndSet(false, true)) {
                    try {
                        asyncContext.complete();
                    } catch (IllegalStateException ignored) {
                    }
                }
            };
            final Runnable cancelUpstream = () -> {
                var s = subscriptionRef.get();
                if (s != null) s.cancel();
            };

            asyncContext.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent e) {
                    cancelUpstream.run();
                }

                @Override
                public void onTimeout(AsyncEvent e) {
                    cancelUpstream.run();
                    finish.run();
                }

                @Override
                public void onError(AsyncEvent e) {
                    cancelUpstream.run();
                    finish.run();
                }

                @Override
                public void onStartAsync(AsyncEvent e) {
                }
            });

            resp.flushBuffer();

            agent.run(input)
                    .subscribe(new Flow.Subscriber<Event>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscriptionRef.set(subscription);
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(Event event) {
                            if (finished.get()) return;
                            try {
                                final var data = aguiSerializer.serialize(event);
                                log.info("graph next: {}", data);
                                out.write(toSse(data));
                                out.flush();
                                asyncContext.getResponse().flushBuffer();
                                TimeUnit.SECONDS.sleep(1);
                            } catch (IOException | InterruptedException |
                                     RuntimeException e) {   // client gone or serialization failure
                                log.warn("write failed, cancelling", e);
                                cancelUpstream.run();
                                finish.run();
                            }
                        }

                        @Override
                        public void onError(Throwable ex) {
                            log.error("graph iteration completed with error", ex);
                            finish.run();
                        }

                        @Override
                        public void onComplete() {
                            log.info("graph iteration completed!");
                            finish.run();
                        }
                    });


        }

    }

    /**
     * SSE framing: every line of the payload gets its own "data: " prefix, event ends with a blank line.
     */
    private static byte[] toSse(String payload) {
        final var sb = new StringBuilder();
        payload.lines().forEach(l -> sb.append("data: ").append(l).append('\n'));   // Java 11+
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
