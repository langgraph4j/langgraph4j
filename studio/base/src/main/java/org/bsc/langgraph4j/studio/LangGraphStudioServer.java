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
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
public interface LangGraphStudioServer {

    Logger log = LoggerFactory.getLogger(LangGraphStudioServer.class);

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

    /**
     * Metadata for an argument in a request.
     *
     * @param name the name of the argument.
     * @param type the type of the argument.
     * @param required whether the argument is required.
     */
    record ArgumentMetadata(
            String name,
            ArgumentType type,
            boolean required,
            @JsonIgnore Function<Object,Object> converter
    ) {
        public ArgumentMetadata {
            requireNonNull(name, "name cannot be null");
            requireNonNull(type, "type cannot be null");
        }
        public ArgumentMetadata(String name, ArgumentType type, boolean required) {
            this(name, type, required, null);
        }

        public enum ArgumentType { STRING, IMAGE };
    }

    /**
     * Represents an entry in a thread with its outputs.
     *
     * @param id the ID of the thread.
     * @param entries the outputs of the thread.
     */
    record ThreadEntry(String id, List<? extends NodeOutput<? extends AgentState>> entries) {}

    /**
     * Initialization data for the graph.
     *
     * @param id the graph identifier
     * @param title the title of the graph.
     * @param diagram the graph content.
     * @param args the arguments for the graph.
     * @param threads the thread entries.
     */
    record InitGraphData(
            String id,
            String title,
            String diagram,
            List<ArgumentMetadata> args,
            List<ThreadEntry> threads) {

        public InitGraphData {
            requireNonNull( id, "id cannot be null");
        }
        public InitGraphData(String id, String title, String diagram, List<ArgumentMetadata> args) {
            this(id, title, diagram, args, List.of(new ThreadEntry("default", List.of())));
        }
    }

    /**
     * Serializer for InitData objects.
     */
    class InitDataSerializer extends StdSerializer<InitGraphData> {
        Logger log = LangGraphStudioServer.log;

        protected InitDataSerializer(Class<InitGraphData> t) {
            super(t);
        }

        /**
         * Serializes the InitData object to JSON.
         *
         * @param initData the InitData object to serialize.
         * @param jsonGenerator the JSON generator.
         * @param serializerProvider the serializer provider.
         * @throws IOException if an I/O error occurs.
         */
        @Override
        public void serialize(InitGraphData initData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            log.trace("InitDataSerializer start!");
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", initData.id());
            jsonGenerator.writeStringField("graph", initData.diagram());
            jsonGenerator.writeStringField("title", initData.title());
            jsonGenerator.writeObjectField("args", initData.args());

            jsonGenerator.writeArrayFieldStart("threads");
            for (var thread : initData.threads()) {
                jsonGenerator.writeStartArray();
                jsonGenerator.writeString(thread.id());
                jsonGenerator.writeStartArray(thread.entries());
                jsonGenerator.writeEndArray();
                jsonGenerator.writeEndArray();
            }
            jsonGenerator.writeEndArray();

            jsonGenerator.writeEndObject();
        }
    }

    record Instance( String title,
                     StateGraph<? extends AgentState> graph,
                     CompileConfig compileConfig,
                     List<ArgumentMetadata> args,
                     ObjectMapper objectMapper,
                     Map<PersistentConfig, CompiledGraph<? extends AgentState>> cache
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
            this(title, graph, compileConfig, args, objectMapperFromGraph(graph), new HashMap<>());
        }

        public InitGraphData toInitGraphData(String id) {
            requireNonNull(id, "id cannot be null");
            try {
                var compiledGraph = graph.compile();
                var graph = compiledGraph.getGraph(GraphRepresentation.Type.MERMAID, /*initData.title()*/ null, false);

                return new InitGraphData(id, title(), graph.content(), args());

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
            private final List<LangGraphStudioServer.ArgumentMetadata> inputArgs = new ArrayList<>();
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
                inputArgs.add(new LangGraphStudioServer.ArgumentMetadata(name, LangGraphStudioServer.ArgumentMetadata.ArgumentType.STRING, required, converter));
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
                inputArgs.add(new LangGraphStudioServer.ArgumentMetadata(name, LangGraphStudioServer.ArgumentMetadata.ArgumentType.IMAGE, required));
                return this;
            }

            /**
             * Adds an input image argument to the server configuration with required set to true.
             *
             * @param name the name of the argument
             * @return the Builder instance
             */
            public Builder addInputImageArg(String name) {
                return addInputImageArg(name, true);
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
     * Servlet for initializing the graph in mermaid format.
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
            module.addSerializer(InitGraphData.class, new InitDataSerializer(InitGraphData.class));
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
        private void serializeOutput( Instance instance, PrintWriter writer, String threadId, NodeOutput<? extends AgentState> output) {
            try {
                writer.printf("[ \"%s\",", threadId);
                writer.println();
                var outputAsString = instance.objectMapper().writeValueAsString(output);
                writer.println(outputAsString);
                writer.println("]");
            } catch (IOException e) {
                log.warn("error serializing state", e);
            }
        }

        private Optional<String> instanceIdFromRequest( HttpServletRequest request ) {

            return ofNullable(request.getPathInfo())
                        .map( p -> p.substring(1) );
        }

        /**
         * Handles POST requests to stream graph data.
         *
         * @param request the HTTP request.
         * @param response the HTTP response.
         * @throws ServletException if a servlet error occurs.
         * @throws IOException if an I/O error occurs.
         */
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setHeader("Accept", "application/json");
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            final var instanceId = instanceIdFromRequest( request )
                                .orElseThrow( () -> new ServletException("instance id is not found in request"));

            final var instance = requireNonNull( instanceMap.get( instanceId ), format("instance not founf with id: [%s]", instanceId));

            final var session = requireNonNull( request.getSession(true), "session cannot be null");

            final var threadId = ofNullable(request.getParameter("thread"))
                    .orElseThrow(() -> new IllegalStateException("Missing thread id!"));

            final var resume = ofNullable(request.getParameter("resume"))
                    .map(Boolean::parseBoolean).orElse(false);


            final PrintWriter writer = response.getWriter();

            // Start asynchronous processing
            var asyncContext = request.startAsync();

            try {
                AsyncGenerator<? extends NodeOutput<? extends AgentState>> generator = null;

                var persistentConfig = new PersistentConfig( session.getId(), instanceId, threadId);

                var compiledGraph = instance.cache().get(persistentConfig);

                final Map<String, Object> candidateDataMap;
                if ( /*resume && */ instance.graph().getStateSerializer() instanceof PlainTextStateSerializer<? extends AgentState> textSerializer) {
                    candidateDataMap = textSerializer.read(new InputStreamReader(request.getInputStream())).data();
                } else {
                    candidateDataMap = instance.objectMapper().readValue(request.getInputStream(), new TypeReference<>() {});
                }

                var dataMap = candidateDataMap.entrySet().stream()
                        .map( entry -> {
                            var newValue = instance.args().stream()
                                    .filter(arg -> arg.name().equals(entry.getKey()) && arg.converter() != null).findAny()
                                    .map(arg -> arg.converter.apply(entry.getValue()));
                            return newValue.map( v -> entryOf(entry.getKey(), v ))
                                    .orElse(entry);
                        })
                        .collect( Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue) );

                if (resume) {
                    log.trace("RESUME REQUEST PREPARE");

                    if (compiledGraph == null) {
                        throw new IllegalStateException("Missing CompiledGraph in session!");
                    }

                    var checkpointId = ofNullable(request.getParameter("checkpoint"))
                            .orElseThrow(() -> new IllegalStateException("Missing checkpoint id!"));

                    var node = request.getParameter("node");

                    var runnableConfig = RunnableConfig.builder()
                            .addMetadata(RunnableConfig.STUDIO_METADATA_KEY, true)
                            .threadId(threadId)
                            .checkPointId(checkpointId)
                            .nextNode(node)
                            .build();

                    var stateSnapshot = compiledGraph.getState(runnableConfig);

                    runnableConfig = stateSnapshot.config();

                    log.trace("RESUME UPDATE STATE FORM {} USING CONFIG {}\n{}", node, runnableConfig, dataMap);

                    runnableConfig = compiledGraph.updateState(runnableConfig, dataMap, node);

                    log.trace("RESUME REQUEST STREAM {}", runnableConfig);

                    generator = compiledGraph.streamSnapshots( GraphInput.resume(), runnableConfig);

                } else {

                    log.trace("dataMap: {}", dataMap);

                    if (compiledGraph == null) {
                        compiledGraph = instance.graph().compile( compileConfig(instance, persistentConfig)) ;
                        instance.cache().put(persistentConfig, compiledGraph);
                    }

                    generator = compiledGraph.streamSnapshots(dataMap, runnableConfig(persistentConfig));
                }

                generator.forEachAsync(s -> {
                            try {
                                serializeOutput(instance, writer, threadId, s);
                                writer.println();
                                writer.flush();
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                throw new CompletionException(e);
                            }
                        })
                        .thenAccept(v -> writer.close())
                        .thenAccept(v -> asyncContext.complete())
                        .exceptionally(e -> {
                            log.error("Error streaming", e);
                            writer.close();
                            asyncContext.complete();
                            return null;
                        });

            } catch (Throwable e) {
                log.error("Error streaming", e);
                throw new ServletException(e);
            }
        }
    }

}


