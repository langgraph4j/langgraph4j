package org.bsc.langgraph4j.studio;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.dsl.JsonDslGenerator;
import org.bsc.langgraph4j.serializer.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.entryOf;


/**
 * Interface for a LangGraph Streaming Server.
 * Provides methods to start the server and manage streaming of graph data.
 */
public interface LangGraphStudioServer extends LG4JLoggable {

    boolean LEGACY_MERMAID_SUPPORT  = false;

    /**
     * Configuration for persistent session data.
     *
     * @param sessionId the ID of the session.
     * @param threadId the ID of the thread.
     */
    record PersistentConfig(String sessionId, String instanceId, String threadId) {
        public PersistentConfig {
            requireNonNull(sessionId);
            requireNonNull(instanceId);
        }
    }




    class CacheEntry {
        final CompiledGraph<? extends AgentState> compiledGraph;
        AsyncGenerator.Cancellable<? extends NodeOutput<? extends AgentState>> generator;

        public CacheEntry(CompiledGraph<? extends AgentState> compiledGraph) {
            this.compiledGraph = compiledGraph;
        }
    }

    record Instance( String title,
                     StateGraph<? extends AgentState> graph,
                     CompileConfig compileConfig,
                     List<ArgumentMetadata> args,
                     ObjectMapper objectMapper,
                     Map<PersistentConfig, CacheEntry> cache
    ) {
        public Instance {
            requireNonNull(graph, "graph cannot be null");
            requireNonNull(compileConfig, "compileConfig cannot be null");
            requireNonNull(args, "args cannot be null");
        }

        private static ObjectMapper objectMapperFromGraph(StateGraph<? extends AgentState> graph) {
            final ObjectMapper result;
            if (graph.getStateSerializer() instanceof JacksonStateSerializer<? extends AgentState> jsonSerializer) {
                result = jsonSerializer.objectMapper().copy();

            } else {
                result = new ObjectMapper();
                result.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            }

            var module = new SimpleModule();
            module.addSerializer(NodeOutput.class, new NodeOutputSerializer());
            result.registerModule(module);

            return result;

        }

        public Instance(String title,
                        StateGraph<? extends AgentState> graph,
                        CompileConfig compileConfig,
                        List<ArgumentMetadata> args) {
            this(title, graph, compileConfig, args, objectMapperFromGraph(graph), new ConcurrentHashMap<>());
        }

        public InitGraphData toInitGraphData(String id) {
            requireNonNull(id, "id cannot be null");
            try {
                var compiledGraph = graph.compile();

                final var representation = (LEGACY_MERMAID_SUPPORT ) ?
                        compiledGraph.getGraph(GraphRepresentation.Type.MERMAID, /*initData.title()*/ null, false)
                                        .content() :
                        compiledGraph.reduce( new JsonDslGenerator<>() );

                return new InitGraphData(id, title(), representation, args());

            } catch (GraphStateException e) {

                return new InitGraphData(id, title(), format("""
                        flowchart TD
                               error["%s"]
                               error@{ shape: text}
                        """, e.getMessage()), args());
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final List<ArgumentMetadata> inputArgs = new ArrayList<>();
            private String title = null;
            private CompileConfig compileConfig;
            private StateGraph<? extends AgentState> graph;


            /**
             * Sets the title for the server.
             *
             * @param title the title to be set
             * @return the Builder instance
             */
            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder addInputStringArgs( List<ArgumentMetadata> args ) {
                inputArgs.addAll( requireNonNull( args, "args cannot be null" ) );
                return this;
            }

            public Builder addInputStringArg(String name, boolean required, Function<Object,Object> converter) {
                inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.STRING, required, converter));
                return this;
            }

            /**
             * Adds an input string argument to the server configuration.
             *
             * @param name     the name of the argument
             * @param required whether the argument is required
             * @return the Builder instance
             */
            public Builder addInputStringArg(String name, boolean required) {
                return addInputStringArg(name, required, null);
            }

            /**
             * Adds an input string argument to the server configuration with required set to true.
             *
             * @param name the name of the argument
             * @return the Builder instance
             */
            public Builder addInputStringArg(String name) {
                return addInputStringArg(name, true);
            }

            /**
             * Adds an input image argument to the server configuration.
             *
             * @param name     the name of the argument
             * @param required whether the argument is required
             * @return the Builder instance
             */
            public Builder addInputImageArg(String name, boolean required) {
                inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.IMAGE, required, null));
                return this;
            }

            /**
             * Adds an input image argument to the server configuration with required set to true.
             *
             * @param name the name of the argument
             * @return the Builder instance
             */
            public Builder addInputImageArg(String name) {
                return addInputImageArg(name, true, null);
            }


            public Builder addInputImageArg(String name, boolean required, Function<Object,Object> converter) {
                inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.IMAGE, required, converter));
                return this;
            }

            /**
             * Sets the checkpoint saver for the server.
             *
             * @param compileConfig the graph compiler config to be used
             * @return the Builder instance
             */
            public Builder compileConfig(CompileConfig compileConfig ) {
                this.compileConfig = compileConfig;
                return this;
            }

            /**
             * Sets the state graph for the server.
             *
             * @param stateGraph the state graph to be used
             * @param <State>    the type of the state
             * @return the Builder instance
             */
            public <State extends AgentState> Builder graph(StateGraph<State> stateGraph) {
                this.graph = stateGraph;
                return this;
            }

            public Instance build() {

                if( compileConfig != null  ) {

                    if( compileConfig.checkpointSaver().isEmpty() ) {
                        throw new IllegalStateException(format("checkpointSaver cannot be null in instance with title: \"%s\"", title));
                    }
                }
                else {
                    compileConfig = CompileConfig.builder()
                            .checkpointSaver( new MemorySaver() )
                            .build();
                }

                return new Instance(
                        ofNullable(title).orElse("LangGraph Studio"),
                        graph,
                        compileConfig,
                        inputArgs
                        );
            }
        }

    }

    /**
     * Servlet for initializing the graph representation format.
     */
    class GraphInitServlet extends HttpServlet {

        Logger log = LangGraphStudioServer.log;

        final Map<String,Instance> instanceMap ;
        final ObjectMapper objectMapper = new ObjectMapper();
        /**
         * Constructs a GraphInitServlet.
         *
         */
        public GraphInitServlet( Map<String,Instance> instanceMap ) {
            this.instanceMap = requireNonNull(instanceMap, "instanceMap cannot be null");
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);

            var module = new SimpleModule();
            module.addSerializer(InitGraphData.class, new InitGraphDataSerializer(InitGraphData.class));
            objectMapper.registerModule(module);

        }

        private Optional<InitGraphData> initGraphDataFromRequest( HttpServletRequest request ) {

            return ofNullable(request.getParameter("instance"))
                    .flatMap( instanceId -> ofNullable(instanceMap.get(instanceId))
                                                    .map( instance -> instance.toInitGraphData(instanceId) ));
        }

        /**
         * Handles GET requests to retrieve the graph initialization data.
         *
         * @param request the HTTP request.
         * @param response the HTTP response.
         * @throws ServletException if a servlet error occurs.
         * @throws IOException if an I/O error occurs.
         */
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");


            var initGraphData = initGraphDataFromRequest( request );

            final String resultJson;

            if( initGraphData.isPresent() ) {
                resultJson = objectMapper.writeValueAsString( initGraphData.get() );
            }
            else {
                /*
                var initGraphDataList = instanceMap.entrySet().stream()
                        .map( entry -> entry.getValue().toInitGraphData(entry.getKey()) )
                        .toList();
                resultJson = objectMapper.writeValueAsString( initGraphDataList );
                 */
                var initData = instanceMap.entrySet().stream().map( entry -> entry.getValue().toInitGraphData(entry.getKey())).findFirst();
                resultJson = initData.isPresent() ?
                    objectMapper.writeValueAsString( initData.get() ) :
                    "{}";
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
    class GraphStreamServlet extends HttpServlet {
        final Map<String,Instance> instanceMap;
        final Logger log = LangGraphStudioServer.log;


        /**
         * Constructs a GraphStreamServlet.
         *
         */
        public GraphStreamServlet( Map<String,Instance> instanceMap ) {

            this.instanceMap = requireNonNull(instanceMap, "instanceMap cannot be null");

        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
        }

        /**
         * Compiles the configuration for the given persistent configuration.
         *
         * @param config the persistent configuration.
         * @return the compiled configuration.
         */
        private CompileConfig compileConfig( Instance instance, PersistentConfig config) {
            return instance.compileConfig();
        }

        /**
         * Creates a runnable configuration based on the persistent configuration.
         *
         * @param config the persistent configuration.
         * @return the runnable configuration.
         */
        RunnableConfig runnableConfig(PersistentConfig config) {
            return RunnableConfig.builder()
                    .addMetadata(RunnableConfig.STUDIO_METADATA_KEY, true)
                    .threadId(config.threadId())
                    .build();
        }

        /**
         * Serializes the output to the given writer.
         *
         * @param writer the writer to serialize to.
         * @param threadId the ID of the thread.
         * @param output the output to serialize.
         */
        private void serializeOutput( Instance instance,
                                      PrintWriter writer,
                                      String threadId,
                                      NodeOutput<? extends AgentState> output) {
            try {
                final var outputAsString = instance.objectMapper()
                        .writer()
                        .withAttribute( "threadId", threadId )
                        .writeValueAsString(output);
                writer.println(outputAsString);
            } catch (IOException e) {
                log.warn("error serializing state", e);
            }
        }

        private Optional<String> instanceIdFromRequest( HttpServletRequest request ) {

            return ofNullable(request.getPathInfo())
                        .map( p -> p.substring(1) );
        }

        /**
         * Cancel running iteration
         *
         */
        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            final var instanceId = instanceIdFromRequest( req )
                    .orElseThrow( () -> new ServletException("instance id is not found in req"));

            final var instance = requireNonNull( instanceMap.get( instanceId ), format("instance not found with id: [%s]", instanceId));

            final var session = requireNonNull( req.getSession(true), "session cannot be null");

            final var threadId = ofNullable(req.getParameter("thread"))
                    .orElseThrow(() -> new IllegalStateException("Missing thread id!"));

            var persistentConfig = new PersistentConfig( session.getId(), instanceId, threadId);

            var cacheEntry = instance.cache().get(persistentConfig);

            if( cacheEntry == null ) {
                log.warn( "cache for instanceId: {} and threadId: {} not found!", instanceId, threadId);
                return;
            }
            if( cacheEntry.generator == null ) {
                log.warn( "generator for instanceId: {} and threadId: {} is null!", instanceId, threadId);
                return;
            }
            if( cacheEntry.generator.isCancelled() ) {
                log.warn( "generator for instanceId: {} and threadId: {} is already cancelled!", instanceId, threadId);
                return;
            }
            // TODO verify if generator is already completed or interrupted

            var result = cacheEntry.generator.cancel(true);

            log.info( "cancel generator requested for instanceId: {} and threadId: {} with result: {}", instanceId, threadId, result);
        }

        private void cacheGeneratorCleanUp(  LangGraphStudioServer.Instance instance, PersistentConfig config ) {
            var cacheEntry = requireNonNull(instance, "instance cannot be null")
                                .cache()
                                .get( requireNonNull(config, "config cannot be null") );
            if( cacheEntry != null ) {
                cacheEntry.generator = null;
            }
        }


        /**
         * Handles POST requests to stream graph data.
         *
         * @param req the HTTP req.
         * @param resp the HTTP resp.
         * @throws ServletException if a servlet error occurs.
         * @throws IOException if an I/O error occurs.
         */
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setHeader("Accept", "application/json");
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            final var instanceId = instanceIdFromRequest( req )
                                .orElseThrow( () -> new ServletException("instance id is not found in req"));

            final var instance = requireNonNull( instanceMap.get( instanceId ), format("instance not found with id: [%s]", instanceId));

            final var session = requireNonNull( req.getSession(true), "session cannot be null");

            final var threadId = ofNullable(req.getParameter("thread"))
                    .orElseThrow(() -> new IllegalStateException("Missing thread id!"));

            final var resume = ofNullable(req.getParameter("resume"))
                    .map(Boolean::parseBoolean).orElse(false);

            final PrintWriter writer = resp.getWriter();

            // Start asynchronous processing
            var asyncContext = req.startAsync();

            try {

                final var persistentConfig = new PersistentConfig( session.getId(), instanceId, threadId);

                var cacheEntry = instance.cache().get(persistentConfig);

                final Map<String, Object> candidateDataMap;
                if ( /*resume && */ instance.graph().getStateSerializer() instanceof PlainTextStateSerializer<? extends AgentState> textSerializer) {
                    candidateDataMap = textSerializer.read(new InputStreamReader(req.getInputStream())).data();
                } else {
                    candidateDataMap = instance.objectMapper().readValue(req.getInputStream(), new TypeReference<>() {});
                }

                var dataMap = candidateDataMap.entrySet().stream()
                        .map( entry -> {
                            var newValue = instance.args().stream()
                                    .filter(arg -> arg.name().equals(entry.getKey()) && arg.converter() != null).findAny()
                                    .map(arg -> arg.converter().apply(entry.getValue()));
                            return newValue.map( v -> entryOf(entry.getKey(), v ))
                                    .orElse(entry);
                        })
                        .collect( Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue) );

                if (resume) {
                    log.trace("RESUME REQUEST PREPARE");

                    if (cacheEntry.compiledGraph == null) {
                        throw new IllegalStateException("Missing CompiledGraph in session!");
                    }

                    var checkpointId = ofNullable(req.getParameter("checkpoint"))
                            .orElseThrow(() -> new IllegalStateException("Missing checkpoint id!"));

                    var node = req.getParameter("node");

                    var runnableConfig = RunnableConfig.builder()
                            .addMetadata(RunnableConfig.STUDIO_METADATA_KEY, true)
                            .threadId(threadId)
                            .checkPointId(checkpointId)
                            .nextNode(node)
                            .build();

                    var stateSnapshot = cacheEntry.compiledGraph.getState(runnableConfig);

                    runnableConfig = stateSnapshot.config();

                    log.trace("RESUME UPDATE STATE FORM {} USING CONFIG {}\n{}", node, runnableConfig, dataMap);

                    runnableConfig = cacheEntry.compiledGraph.updateState(runnableConfig, dataMap, node);

                    log.trace("RESUME REQUEST STREAM {}", runnableConfig);

                    cacheEntry.generator = cacheEntry.compiledGraph.streamSnapshots( GraphInput.resume(), runnableConfig);

                } else {

                    log.trace("dataMap: {}", dataMap);

                    if (cacheEntry == null) {
                        cacheEntry = new CacheEntry( instance.graph().compile( compileConfig(instance, persistentConfig)) ) ;
                        instance.cache().put(persistentConfig, cacheEntry);
                    }

                    cacheEntry.generator  = cacheEntry.compiledGraph.streamSnapshots( GraphInput.args(dataMap), runnableConfig(persistentConfig));
                }

                cacheEntry.generator.forEachAsync(s -> {
                            try {
                                serializeOutput(instance, writer, threadId, s);
                                writer.println();
                                writer.flush();
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                throw new CompletionException(e);
                            }
                        })
                        .whenComplete( ( result, ex) -> {
                            if( ex != null ) {
                                log.error("graph iteration completed with error", ex);
                            }
                            else {
                                log.info( "graph iteration completed with result {}!", result);
                            }

                            writer.close();
                            asyncContext.complete();
                            cacheGeneratorCleanUp( instance, persistentConfig );

                        })
                        ;

            } catch (Throwable e) {
                log.error("Error streaming", e);
                throw new ServletException(e);
            }
        }

    }


}


