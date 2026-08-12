package org.bsc.langgraph4j;

import java.util.Map.Entry;
import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.internal.edge.Edge;
import org.bsc.langgraph4j.internal.edge.EdgeCondition;
import org.bsc.langgraph4j.internal.edge.EdgeValue;
import org.bsc.langgraph4j.action.SubCompiledGraphNodeAction;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.bsc.langgraph4j.utils.TryFunction;
import org.bsc.langgraph4j.utils.TypeRef;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.action.SubCompiledGraphNodeAction.resumeSubGraphId;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Represents a compiled graph of nodes and edges.
 * This class manage the StateGraph execution
 *
 * @param <State> the type of the state associated with the graph
 */
public final class CompiledGraph<State extends AgentState> implements GraphDefinition<State>, LG4JLoggable {

    private static final String INTERRUPT_AFTER = "__INTERRUPTED__";

    /**
     * Enum representing various error messages related to graph runner.
     */
    public enum RunErrors {
        missingNodeInEdgeMapping("cannot find edge mapping for id: '%s' in conditional edge with sourceId: '%s' "),
        missingNode("node with id: '%s' doesn't exist!"),
        missingEdge("edge with sourceId: '%s' doesn't exist!"),
        executionError("%s");

        private final String errorMessage;

        RunErrors(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        /**
         * Creates a new GraphRunnerException with the formatted error message.
         *
         * @param args the arguments to format the error message
         * @return a new GraphRunnerException
         */
        GraphRunnerException exception(RunnableConfig config, String... args) {
            return new GraphRunnerException( config, errorMessage.formatted( (Object[]) args));
        }
    }


    public enum StreamMode {
        VALUES,
        SNAPSHOTS
    }

    public final StateGraph<State> stateGraph;

    final Map<String, AsyncNodeActionWithConfig<State>> nodes = new LinkedHashMap<>();
    final Map<String, EdgeValue<State>> edges = new LinkedHashMap<>();

    private final ProcessedNodesEdgesAndConfig<State> processedData;

    public final CompileConfig compileConfig;

    /**
     * Constructs a CompiledGraph with the given StateGraph.
     *
     * @param stateGraph the StateGraph to be used in this CompiledGraph
     */
    CompiledGraph(StateGraph<State> stateGraph, CompileConfig compileConfig ) throws GraphStateException {

        this.stateGraph = stateGraph;

        this.processedData = ProcessedNodesEdgesAndConfig.process( stateGraph, compileConfig );

        // CHECK INTERRUPTIONS
        for (String interruption : processedData.interruptsBefore() ) {
            if (!processedData.nodes().anyMatchById( interruption )) {
                throw StateGraph.Errors.interruptionNodeNotExist.exception(interruption);
            }
        }
        for (String interruption : processedData.interruptsAfter() ) {
            if (!processedData.nodes().anyMatchById( interruption )) {
                throw StateGraph.Errors.interruptionNodeNotExist.exception(interruption);
            }
        }

        // RE-CREATE THE EVENTUALLY UPDATED COMPILE CONFIG
        this.compileConfig = CompileConfig.builder(compileConfig)
                                .interruptsBefore(processedData.interruptsBefore())
                                .interruptsAfter(processedData.interruptsAfter())
                                .build();

        // EVALUATES NODES
        for (var n : processedData.nodes().elements ) {
            var factory = n.actionFactory();
            requireNonNull(factory, "action factory for node id '%s' is null!".formatted( n.id()));
            nodes.put(n.id(), factory.apply(compileConfig));
        }

        // EVALUATE EDGES
        for( var e : processedData.edges().elements ) {
            var targets = e.targets();
            if (targets.size() == 1) {
                edges.put(e.sourceId(), targets.get(0));
            }
            else {
                Supplier<Stream<EdgeValue<State>>> parallelNodeStream = () ->
                        targets.stream().filter( target -> nodes.containsKey(target.id()) );

                var parallelNodeEdges = parallelNodeStream.get()
                        .map( target -> new Edge<State>(target.id()))
                        .filter( ee -> processedData.edges().elements.contains( ee ) )
                        .map(  ee -> processedData.edges().elements.indexOf( ee ) )
                        .map( index -> processedData.edges().elements.get(index) )
                        .toList();

                var  parallelNodeTargets = parallelNodeEdges.stream()
                                                .map( ee -> ee.target().id() )
                                                .collect(Collectors.toSet());

                if( parallelNodeTargets.size() > 1  ) {

                    var conditionalEdges = parallelNodeEdges.stream()
                                                .filter( ee -> ee.target().value() != null )
                                                .toList();
                    if(!conditionalEdges.isEmpty()) {
                        throw StateGraph.Errors.unsupportedConditionalEdgeOnParallelNode.exception(
                                e.sourceId(),
                                conditionalEdges.stream().map(Edge::sourceId).toList() );
                    }
                    throw StateGraph.Errors.illegalMultipleTargetsOnParallelNode.exception(e.sourceId(), parallelNodeTargets );
                }

                var actions = parallelNodeStream.get()
                                    //.map( target -> nodes.remove(target.id()) )
                                    .map( target -> nodes.get(target.id()) )
                                    .toList();

                var parallelNode = new ParallelNode<>( e.sourceId(), actions, stateGraph.getChannels() );

                nodes.put( parallelNode.id(), parallelNode.actionFactory().apply(compileConfig) );

                edges.put( e.sourceId(), new EdgeValue<>( parallelNode.id() ) );

                edges.put( parallelNode.id(), new EdgeValue<>( parallelNodeTargets.iterator().next() ));

            }

        }
    }

    /**
     * Gets the history of graph states relate to a specific Thread ID. Useful for:
     * - Debugging execution history
     * - Implementing time travel
     * - Analyzing graph behavior
     *
     * @param config the RunnableConfig containing the thread ID information
     * @return collection of StateSnapshots of the given Thread ID. The first element of collection is the last state
     */
    public Collection<StateSnapshot<State>> getStateHistory( RunnableConfig config ) {
        final var saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

        return saver.list(config).stream()
                .map( checkpoint -> StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) )
                .toList();
    }

    /**
     * Same of {@link #stateOf(RunnableConfig)} but throws an IllegalStateException if checkpoint is not found.
     *
     * @param config the RunnableConfig
     * @return the StateSnapshot of the given RunnableConfig
     * @throws IllegalStateException if the saver is not defined, or no checkpoint is found
     */
    public StateSnapshot<State> getState( RunnableConfig config ) {
        return stateOf(config).orElseThrow( () -> (new IllegalStateException("Missing Checkpoint!")) );
    }

    /**
     * Get the StateSnapshot of the given RunnableConfig.
     *
     * @param config the RunnableConfig
     * @return an Optional of StateSnapshot of the given RunnableConfig
     * @throws IllegalStateException if the saver is not defined
     */
    public Optional<StateSnapshot<State>> stateOf( RunnableConfig config ) {
        final var saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

        return saver.get(config)
                .map( checkpoint -> StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) );
    }

    /**
     * Get the last StateSnapshot of the given RunnableConfig.
     *
     * @param config - the RunnableConfig
     * @return the last StateSnapshot of the given RunnableConfig if any
     */
    public Optional<StateSnapshot<State>> lastStateOf( RunnableConfig config ) {
        return getStateHistory( config ).stream().findFirst();
    }

    /**
     * Update the state of the graph with the given values.
     * If asNode is given, it will be used to determine the next node to run.
     * If not given, the next node will be determined by the state graph.
     * 
     * @param config the RunnableConfig containg the graph state
     * @param values the values to be updated
     * @param asNode the node id to be used for the next node. can be null
     * @return the updated RunnableConfig
     * @throws Exception when something goes wrong
     */
    public RunnableConfig updateState( RunnableConfig config, Map<String,Object> values, String asNode ) throws Exception {

        final var saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

        // merge values with checkpoint values
        var branchCheckpoint = saver.get(config)
                            .map(Checkpoint::copyOf)
                            .map( cp -> cp.updateState(values, stateGraph.getChannels()) )
                            .orElseThrow( () -> (new IllegalStateException("Missing Checkpoint!")) );

        String nextNodeId = null;
        if( asNode != null ) {
            final var nextNodeCommand = nextNodeId( asNode, branchCheckpoint.getState(), config );

            nextNodeId = nextNodeCommand.gotoNode();
            branchCheckpoint =  branchCheckpoint.updateState( nextNodeCommand.update(), stateGraph.getChannels(), nextNodeId );

        }
        // update checkpoint in saver
        final var newConfig = saver.put( config, branchCheckpoint );

        final var runnableConfigBuilder =  RunnableConfig.builder(newConfig)
                .checkPointId( branchCheckpoint.getId() )
                .nextNode( nextNodeId );

        if( stateGraph.nodes.hasSubGraphs() ) {
            runnableConfigBuilder.putMetadata( RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, values ) ;
        }

        return runnableConfigBuilder.build();
    }

    /***
     * Update the state of the graph with the given values.
     *
     * @param config the RunnableConfig containg the graph state
     * @param values the values to be updated
     * @return the updated RunnableConfig
     * @throws Exception when something goes wrong
     */
    public RunnableConfig updateState( RunnableConfig config, Map<String,Object> values ) throws Exception {
        return updateState(config, values, null);
    }

    /**
     * UPDATE RUNNABLE CONFIG METADATA
     */
    private RunnableConfig updateRunnableConfigMetadata( RunnableConfig config, String currentNodeId ) {
        final var newMetadata = new HashMap<String,Object>(2);
        newMetadata.put(RunnableConfig.NODE_ID, currentNodeId);
        compileConfig.graphId()
                .ifPresent( graphId -> {
                    newMetadata.put(RunnableConfig.GRAPH_ID, graphId);
                    if( config.graphPath().isEmpty() ) { // to avoid add graphId in subgraph cases
                        newMetadata.put(RunnableConfig.GRAPH_PATH, config.graphPath().append(graphId) );
                    }
                });
        newMetadata.put(RunnableConfig.GRAPH_NODE_PATH, config.nodePath().replaceLast(currentNodeId));

        return config.updateMetadata( newMetadata );

    }

    private Command nextNodeId(EdgeValue<State> route , Map<String,Object> state, String nodeId, RunnableConfig config ) throws Exception {

        if( route == null ) {
            throw RunErrors.missingEdge.exception( config, nodeId);
        }
        if( route.id() != null ) {
            return new Command(route.id(), state);
        }
        if( route.value() != null ) {

            final var newConfig = updateRunnableConfigMetadata( config, nodeId );

            final State derefState = stateGraph.getStateFactory().apply(state);

            // var command = route.value().action().apply(derefState,config).get();
            final var command = stateGraph.edgeHooks.applyActionWithHooks(
                    route.value().action(),
                    nodeId,
                    derefState,
                    newConfig,
                    stateGraph.getStateFactory(),
                    stateGraph.getChannels() )
                    .get();

            final var newRoute = command.gotoNode();

            final String result = route.value().mappings().get(newRoute);
            if( result == null ) {
                throw RunErrors.missingNodeInEdgeMapping.exception(config, nodeId, newRoute);
            }

            final var currentState = AgentState.updateState(state, command.update(), stateGraph.getChannels());

            return new Command(result, currentState);
        }
        throw RunErrors.executionError.exception( config, "invalid edge value for nodeId: [%s] !".formatted(nodeId) );
    }

    /**
     * Determines the next node ID based on the current node ID and state.
     *
     * @param nodeId the current node ID
     * @param state the current state
     * @return the next node command
     * @throws Exception if there is an error determining the next node ID
     */
    private Command nextNodeId(String nodeId, Map<String,Object> state, RunnableConfig config) throws Exception {
        return nextNodeId(edges.get(nodeId), state, nodeId, config  );

    }

    private Command entryPoint(Map<String,Object> state, RunnableConfig config ) throws Exception {
        final var entryPoint = this.edges.get(START);
        return nextNodeId(entryPoint, state, START, config);
    }

    private boolean shouldInterruptBefore( String nodeId, String previousNodeId ) {
        requireNonNull( nodeId, "nodeId cannot be null" );
        if( previousNodeId == null ) { // FIX RESUME ERROR
            return false;
        }
        return compileConfig.interruptsBefore().contains(nodeId);
    }

    private boolean shouldInterruptAfter(String nodeId, String currentNodeId ) {
        if( nodeId == null || Objects.equals(nodeId, currentNodeId) ) { // FIX RESUME ERROR
            return false;
        }
        return ( compileConfig.interruptBeforeEdge() && Objects.equals(nodeId, INTERRUPT_AFTER )) ||
                compileConfig.interruptsAfter().contains(nodeId);
    }

    private Optional<Checkpoint> addCheckpoint( RunnableConfig config, String nodeId, Map<String,Object> state, String nextNodeId ) throws Exception {
        if( compileConfig.checkpointSaver().isPresent() ) {
            var cp =  Checkpoint.builder()
                                .nodeId( nodeId )
                                .state( cloneState(state, config) )
                                .nextNodeId( nextNodeId )
                                .build();
            compileConfig.checkpointSaver().get().put( config, cp );
            return Optional.of(cp);
        }
        return Optional.empty();

    }

    Map<String,Object> initialStateFromSchema() {
        return stateGraph.getStateFactory().initialDataFromSchema(stateGraph.getChannels());
    }

    Map<String,Object> initialState(Map<String,Object> inputs, RunnableConfig config) {

        return compileConfig.checkpointSaver()
                .flatMap( saver -> saver.get( config ) )
                .map( cp -> AgentState.updateState( cp.getState(), inputs, stateGraph.getChannels() ))
                .orElseGet( () -> AgentState.updateState( initialStateFromSchema(), inputs, stateGraph.getChannels() ));
    }

    State cloneState( Map<String,Object> data, RunnableConfig runnableConfig ) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        if(runnableConfig.isCloneStateDisabled()) {
            return stateGraph.getStateSerializer().stateOf(data);
        }
        return stateGraph.getStateSerializer().cloneObject(data);
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param input the input data
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator.Cancellable<NodeOutput<State>> stream( GraphInput input, RunnableConfig config ) {
        return AsyncGeneratorFlow.create( new Emitter<>( input, config ));
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     * @deprecated use {@link #stream(GraphInput, RunnableConfig)}  instead to have more control over the execution configuration.
     */
    @Deprecated(forRemoval = true)
    public AsyncGenerator.Cancellable<NodeOutput<State>> stream( Map<String,Object> inputs, RunnableConfig config ) {
        return stream(  ( inputs == null ) ? GraphInput.resume() : GraphInput.args(inputs), config );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @return an AsyncGenerator stream of NodeOutput
     * @deprecated use {@link #stream(GraphInput, RunnableConfig)}  instead to have more control over the execution configuration.
     */
    @Deprecated(forRemoval = true)
    public AsyncGenerator<NodeOutput<State>> stream(Map<String,Object> inputs ) {
        return this.stream( ( inputs == null ) ? GraphInput.resume() : GraphInput.args(inputs), RunnableConfig.empty() );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param input the input data
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator.Cancellable<NodeOutput<State>> streamSnapshots( GraphInput input, RunnableConfig config )  {
/*
        requireNonNull(config, "config cannot be null");
        return new AsyncNodeGeneratorWithEmbed<>(
                requireNonNull( input, "input cannot be null" ),
                requireNonNull( config, "config cannot be null").withStreamMode(StreamMode.SNAPSHOTS));

 */
        return AsyncGeneratorFlow.create(
                new Emitter<>(
                        requireNonNull( input, "input cannot be null" ),
                        requireNonNull( config, "config cannot be null")
                                .withStreamMode(StreamMode.SNAPSHOTS) ));

    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     * @deprecated use {@link #streamSnapshots(GraphInput, RunnableConfig)}  instead to have more control over the execution configuration and to prepare for future changes that may require a config parameter.
     */
    @Deprecated(forRemoval = true)
    public AsyncGenerator.Cancellable<NodeOutput<State>> streamSnapshots( Map<String,Object> inputs, RunnableConfig config )  {
        return streamSnapshots( ( inputs == null ) ? GraphInput.resume() : GraphInput.args(inputs), config );
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final {@link NodeOutput}.
     * <p>
     * The {@code NodeOutput} contains the final state of the graph and the ID of the last
     * node that was executed. This is useful for determining if the graph finished
     * normally (the node ID will be {@link StateGraph#END}) or was interrupted.
     *
     * @param input the input data for the graph execution.
     * @param config the configuration for this specific invocation.
     * @return an {@link Optional} containing the final {@link NodeOutput} if the
     *         graph execution produced any output, otherwise an empty Optional.
     * @since 1.6.1
     */
    public Optional<NodeOutput<State>> invokeFinal( GraphInput input, RunnableConfig config ) {
        return stream(input, config)
                .stream()
                .reduce((a, b) -> b);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param input the input data
     * @param config the invoke configuration
     * @return an Optional containing the final state if present, otherwise an empty Optional
     */
    public Optional<State> invoke(GraphInput input, RunnableConfig config ) {
        return invokeFinal( input, config ).map( NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an Optional containing the final state if present, otherwise an empty Optional
     * @deprecated use {@link #invoke(GraphInput, RunnableConfig)}  instead to have more control over the execution configuration.
     */
    @Deprecated(forRemoval = true)
    public Optional<State> invoke(Map<String,Object> inputs, RunnableConfig config ) {
        return invokeFinal( inputs == null ? GraphInput.resume() : GraphInput.args(inputs), config ).map( NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @return an Optional containing the final state if present, otherwise an empty Optional
     * @deprecated use {@link #invoke(GraphInput, RunnableConfig)}  instead to have more control over the execution configuration.
     */
    @Deprecated(forRemoval = true)
    public Optional<State> invoke( Map<String,Object> inputs )  {
        return invokeFinal( inputs == null ? GraphInput.resume() : GraphInput.args(inputs), RunnableConfig.empty() ).map( NodeOutput::state);
    }

    /**
     * Generates a drawable graph representation of the state graph.
     *
     * @param type the type of graph representation to generate
     * @param title the title of the graph
     * @param printConditionalEdges whether to print conditional edges
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph( GraphRepresentation.Type type, String title, boolean printConditionalEdges ) {

        final String content = reduce( type.generator.generate( title, printConditionalEdges) );

        return new GraphRepresentation( type, content );
    }

    /**
     * Generates a drawable graph representation of the state graph.
     *
     * @param type the type of graph representation to generate
     * @param title the title of the graph
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph( GraphRepresentation.Type type, String title ) {
        return getGraph( type, title, true );
    }

    /**
     * Generates a drawable graph representation of the state graph with default title.
     *
     * @param type the type of graph representation to generate
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph( GraphRepresentation.Type type ) {
        return getGraph(type, "Graph Diagram", true);
    }


    /**
     * Applies a reducer function to process the nodes and edges of this graph.
     *
     * <p>This method allows external processing of the graph structure by applying a
     * custom reduction function that receives all nodes and edges.
     *
     * @param <Output> the type of output produced by the reducer
     * @param reducer the reducer function to apply
     * @return the output produced by the reducer
     */
    @Override
    public <Output> Output reduce(Reducer<State, Output> reducer ) {
        return reducer.apply( processedData.nodes(), processedData.edges() );
    }

    class Emitter<Output extends NodeOutput<State>> implements Consumer<AsyncGeneratorFlow.Dispatcher<Output>> {

        static class Context {
            private Map<String,Object> currentState;
            private String currentNodeId;
            private String nextNodeId;
            private String resumeFrom;

            Context( Map<String,Object> initState ) {
                currentNodeId = START;
                nextNodeId = null;
                resumeFrom = null;
                currentState = initState;
            }

            Context( Checkpoint cp ) {
                currentNodeId = null;
                nextNodeId = cp.getNextNodeId();
                resumeFrom = cp.getNodeId();
                currentState = cp.getState();
            }

            void reset() {
                currentNodeId = null;
                nextNodeId = null;
                resumeFrom = null;
            }

            Map<String,Object> currentState() {
                return currentState;
            }

            void setCurrentState( Map<String,Object> value ) {
                currentState = value;
            }

            String nextNodeId() {
                return nextNodeId;
            }

            void setNextNodeId( String value ) {
                nextNodeId = value;
            }

            String currentNodeId() {
                return currentNodeId;
            }

            void setCurrentNodeId( String value ) {
                currentNodeId = value;
            }

            Optional<String> getResumeFromAndReset() {
                final var  result = ofNullable(resumeFrom);
                resumeFrom = null;
                return result;
            }
        }

        private final Context context;
        private RunnableConfig config;

        protected Emitter(GraphInput input, RunnableConfig config )  {
            final var configBuilder = RunnableConfig.builder(config)
                    .checkPointId(null); // Reset checkpoint id

            if( input instanceof GraphResume resumeRequest ) {
                log.trace( "RESUME REQUEST: {}", resumeRequest );

                final var saver = compileConfig.checkpointSaver()
                        .orElseThrow(() -> new IllegalStateException("Resume request without a configured checkpoint saver!"));
                final var startCheckpoint = saver.get( config )
                                                        .orElseThrow( () -> new IllegalStateException("Resume request without a valid checkpoint!") );

                final var optionalResumeUpdateData = config.metadata(RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, new TypeRef<Map<String,Object>>() {});

                context = new Context(startCheckpoint);

                final var startCheckpointNextNodeAction = nodes.get(startCheckpoint.getNextNodeId());
                if( startCheckpointNextNodeAction instanceof SubCompiledGraphNodeAction<State> action ) {

                    // RESUME FORM SUBGRAPH DETECTED
                    final var resumeUpdateData = optionalResumeUpdateData
                            .map( data -> mergeMap( data, resumeRequest.value() ))
                            .orElseGet(resumeRequest::value);

                    // RESUME FORM SUBGRAPH DETECTED
                    this.config = configBuilder
                            .addMetadata( action.resumeSubGraphId(), true)
                            .putMetadata( RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, resumeUpdateData )
                            .build();

                    context.setCurrentState( startCheckpoint.getState() );

                }
                else {
                    final var stateData = optionalResumeUpdateData.orElseGet(resumeRequest::value);

                    this.config = configBuilder.build();

                    // FIX ISSUE #302
                    context.setCurrentState( AgentState.updateState( startCheckpoint.getState(),
                            stateData,
                            stateGraph.getChannels() ));

                }

                log.trace( "RESUME FROM {}", startCheckpoint.getNodeId() );
            }
            else {

                log.trace( "START" );

                final var initState = initialState( ((GraphArgs)input).value(), config );
                // patch for backward support of AppendableValue
                State initializedState = stateGraph.getStateFactory().apply(initState);
                this.context = new Context( initializedState.data() );
                this.config = configBuilder.build();
            }
        }

        @SuppressWarnings("unchecked")
        protected Output buildNodeOutput( String nodeId, RunnableConfig config ) throws Exception {
            return  (Output)NodeOutput.of( nodeId, cloneState(context.currentState(), config) );
        }

        @SuppressWarnings("unchecked")
        protected Output buildStateSnapshot( Checkpoint checkpoint ) throws Exception {
            return (Output)StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) ;
        }

        @SuppressWarnings("unchecked")
        private Optional<GraphResult> embedGenerator( AsyncGeneratorFlow.Dispatcher<Output> $1,
                                                                      AsyncNodeActionWithConfig<State> action,
                                                                      String nodeId,
                                                                      State state,
                                                                      RunnableConfig runnableConfig,
                                                                      Map<String,Object> partialState )
        {
            final var generatorEntry = partialState.entrySet().stream()
                    .filter( e -> e.getValue() instanceof AsyncGenerator)
                    .findFirst();


            return generatorEntry.map( entry -> (AsyncGenerator<Output>) entry.getValue())
                    .map( generator ->
                        generator.reduce((a, b) -> {
                                    $1.dispatchAsync(AsyncGenerator.Data.of(b));
                                    return b;
                                })
                                .thenApply(reduceResult -> {

                                    final var returnFromEmbed = GraphResult.from(reduceResult.resultValue());

                                    if (returnFromEmbed.isInterruptionMetadata()) {
                                        return returnFromEmbed;
                                    }

                                    // FIX #336
                                    // Streaming completed: call AfterHook here
                                    final Map<String,Object> partialResultAfterHook;
                                    if( !stateGraph.nodeHooks.afterCalls.isEmpty() ) {
                                        partialResultAfterHook = stateGraph.nodeHooks.afterCalls.apply(
                                                context.currentNodeId(),
                                                stateGraph.getStateFactory().apply(context.currentState()),
                                                config,
                                                returnFromEmbed.asStateDataOrLastCheckpointStateData())
                                                .join();
                                    }
                                    else {
                                        partialResultAfterHook = returnFromEmbed.asStateDataOrLastCheckpointStateData();
                                    }

                                    config = config.removeMetadata(
                                            resumeSubGraphId( context.currentNodeId() ),
                                            RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA );

                                    // FIX #102
                                    // Assume that the whatever used appender channel doesn't accept duplicates
                                    // FIX #104: remove generator
                                    final Map<String, Object> partialStateWithoutGenerator = partialState.entrySet().stream()
                                            .filter(e -> e.getKey() != null &&
                                                    !Objects.equals(e.getKey(), generatorEntry.get().getKey()))
                                            .collect(LinkedHashMap::new,
                                                    (map, entry) ->
                                                            map.put(entry.getKey(), entry.getValue()),
                                                    Map::putAll);

                                    final var generatorStateMerged = AgentState.updateState(partialResultAfterHook,
                                            partialStateWithoutGenerator,
                                            stateGraph.getChannels());

                                    final var intermediateState = AgentState.updateState(context.currentState(),
                                            generatorStateMerged,
                                            stateGraph.getChannels());

                                    context.setCurrentState(intermediateState);

                                    return returnFromEmbed;

                                })
                                .join()

                    );

        }

        private AsyncGenerator.Data<Output> applyAction( AsyncGeneratorFlow.Dispatcher<Output> $1,
                                                         AsyncNodeActionWithConfig<State> action,
                                                         String nodeId,
                                                         State clonedState,
                                                         RunnableConfig runnableConfig ) throws ExecutionException, InterruptedException
        {
            final AgentStateFactory<State> stateFactory = ( data ) -> {
                context.setCurrentState( data );
                return stateGraph.getStateFactory().apply( data);
            };

            return stateGraph.nodeHooks.applyActionWithHooksHandlingInterruption(  action, nodeId, clonedState, compileConfig, runnableConfig, stateFactory, stateGraph.getChannels() )
                    .thenApply(TryFunction.Try(result -> {
                        if( result.hasPartialState() ) {
                            return result.partialState().thenApply( TryFunction.<Map<String,Object>, AsyncGenerator.Data<Output>, Exception>Try(partial -> {
                                final var embedResult = embedGenerator( $1, action, nodeId, clonedState, runnableConfig, partial);

                                if( embedResult.isEmpty() ) {
                                    context.setCurrentState(AgentState.updateState(context.currentState(), partial, stateGraph.getChannels()));
                                }

                                if (compileConfig.interruptBeforeEdge() && compileConfig.interruptsAfter().contains(context.currentNodeId())) {
                                    context.setNextNodeId(INTERRUPT_AFTER);
                                } else {
                                    var nextNodeCommand = nextNodeId(context.currentNodeId(), context.currentState(), runnableConfig);
                                    context.setNextNodeId(nextNodeCommand.gotoNode());
                                    context.setCurrentState( nextNodeCommand.update() );
                                }

                                if( embedResult.isPresent() ) {
                                    final var res = GraphResult.from(embedResult.get());

                                    if( res.isInterruptionMetadata() ) {
                                        return AsyncGenerator.Data.done(embedResult.get());
                                    }
                                }

                                return AsyncGenerator.Data.of( nodeOutput());
                            }));
                        }
                        return result.interruptionMetadata().thenApply(AsyncGenerator.Data::<Output>done);
                    }))
                    .thenCompose( result -> result )
                    .get();
        }



        private CompletableFuture<Output> nodeOutput() throws Exception {
            Optional<Checkpoint>  cp = addCheckpoint(config, context.currentNodeId(), context.currentState(), context.nextNodeId());
            return completedFuture(( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                    buildStateSnapshot(cp.get()) :
                    buildNodeOutput( context.currentNodeId(), config ))
                    ;
        }

        private Optional<BaseCheckpointSaver.Tag> releaseThread() throws Exception {
            if(compileConfig.releaseThread() && compileConfig.checkpointSaver().isPresent() ) {
                return Optional.of(compileConfig.checkpointSaver().get().release( config ));
            }
            return Optional.empty();
        }

        @Override
        public void accept(AsyncGeneratorFlow.Dispatcher<Output> $1 ) {

            try {

                final var maxIterations = compileConfig.recursionLimit();
                var iteration = 0;
                for(;;) {
                    // GUARD: CHECK MAX ITERATION REACHED
                    if( ++iteration > maxIterations ) {
                        // log.warn( "Maximum number of iterations ({}) reached!", maxIterations);
                        $1.dispatchAsync(
                                AsyncGenerator.Data.error( new IllegalStateException( "Maximum number of iterations (%d) reached!".formatted( maxIterations)) ));
                        break;
                    }

                    // GUARD: CHECK IF IT IS END
                    if( context.nextNodeId() == null && context.currentNodeId() == null  ) {
                        $1.dispatchAsync(
                            releaseThread()
                                .map(AsyncGenerator.Data::<Output>done)
                                .orElseGet( () -> AsyncGenerator.Data.done(context.currentState()) ));
                        break;
                    }

                    if( START.equals(context.currentNodeId()) ) {
                        var nextNodeCommand = entryPoint(context.currentState(), config) ;
                        context.setNextNodeId(nextNodeCommand.gotoNode());
                        context.setCurrentState( nextNodeCommand.update() );

                        var cp = addCheckpoint( config, START, context.currentState(), context.nextNodeId() );

                        var output =  ( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                                buildStateSnapshot(cp.get()) :
                                buildNodeOutput( context.currentNodeId(), config );

                        context.setCurrentNodeId(context.nextNodeId());

                        $1.dispatchAsync(
                            AsyncGenerator.Data.of( output ));
                        continue;
                    }

                    if( END.equals(context.nextNodeId()) ) {
                        context.reset();
                        $1.dispatchAsync(
                                AsyncGenerator.Data.of( buildNodeOutput( END, config ) ));
                        continue;
                    }

                    final var resumeFrom = context.getResumeFromAndReset();
                    if( resumeFrom.isPresent() ) {

                        if(compileConfig.interruptBeforeEdge() && Objects.equals( context.nextNodeId(), INTERRUPT_AFTER)) {
                            var nextNodeCommand = nextNodeId( resumeFrom.get(), context.currentState(), config);
                            context.setNextNodeId( nextNodeCommand.gotoNode() );
                            context.setCurrentState(  nextNodeCommand.update() );
                            context.setCurrentNodeId( null );
                        }
                    }

                    // CHECK ON PREVIOUS NODE
                    if( shouldInterruptAfter( context.currentNodeId(), context.nextNodeId() )) {
                        final var interruptionMetadata = InterruptionMetadata.builder(context.currentNodeId(), cloneState(context.currentState(), config)).build();
                        if( compileConfig.checkpointSaver().isPresent() ) {
                            compileConfig.checkpointSaver().get().registerInterruption(config, interruptionMetadata);
                        }
                        $1.dispatchAsync(
                                AsyncGenerator.Data.done( interruptionMetadata ));
                        break;
                    }

                    if( shouldInterruptBefore( context.nextNodeId(), context.currentNodeId() ) ) {
                        final var interruptionMetadata = InterruptionMetadata.builder(context.currentNodeId(), cloneState(context.currentState(), config)).build();
                        if( compileConfig.checkpointSaver().isPresent() ) {
                            compileConfig.checkpointSaver().get().registerInterruption(config, interruptionMetadata);
                        }
                        $1.dispatchAsync(
                                AsyncGenerator.Data.done(interruptionMetadata ));
                        break;
                    }

                    context.setCurrentNodeId( context.nextNodeId() );

                    config = updateRunnableConfigMetadata( config, context.currentNodeId() );

                    //
                    // EVALUATE ACTION
                    //
                    final var action = ofNullable(nodes.get( context.currentNodeId() ))
                            .orElseThrow( () -> RunErrors.missingNode.exception( config, context.currentNodeId()));

                    final var clonedState = cloneState(context.currentState(), config);

                    try {

                        final var actionResult = applyAction( $1, action, context.currentNodeId(), clonedState, config );

                        $1.dispatchAsync(actionResult);

                        if( actionResult.isDone() ) {
                            break;
                        }

                    }
                    catch( InterruptedException ex ) {
                        if( action instanceof ParallelNode.AsyncParallelNodeAction<?> parallelNodeAction ) {
                            log.info( "PARALLEL NODE {} INTERRUPTED!", context.currentNodeId() );
                        }
                        throw ex;
                    }

                }
            }
            catch( GraphRunnerException e ) {
                log.error( e.getMessage(), e );
                $1.dispatchAsync( AsyncGenerator.Data.error( e ) );
            }
            catch( Throwable e ) {
                log.error( e.getMessage(), e );
                $1.dispatchAsync( AsyncGenerator.Data.error( new GraphRunnerException( config, e) ));
            }

        }
    }

    /**
     * Async Generator for streaming outputs.
     *
     * @param <Output> the type of the output
     */
    @Deprecated( since = "1.9.0", forRemoval = true)
    class AsyncNodeGenerator<Output extends NodeOutput<State>> extends AsyncGenerator.BaseCancellable<Output> {

        static class Context {
            private Map<String,Object> currentState;
            private String currentNodeId;
            private String nextNodeId;
            private String resumeFrom;
            private GraphResult returnFromEmbed;

            Context( Map<String,Object> initState ) {
                currentNodeId = START;
                nextNodeId = null;
                resumeFrom = null;
                returnFromEmbed = GraphResult.empty();
                currentState = initState;
            }

            Context( Checkpoint cp ) {
                currentNodeId = null;
                nextNodeId = cp.getNextNodeId();
                resumeFrom = cp.getNodeId();
                currentState = cp.getState();
                returnFromEmbed = GraphResult.empty();
            }

            void reset() {
                currentNodeId = null;
                nextNodeId = null;
                resumeFrom = null;
                returnFromEmbed = GraphResult.empty();
            }

            Map<String,Object> currentState() {
                return currentState;
            }

            void setCurrentState( Map<String,Object> value ) {
                currentState = value;
            }

            String nextNodeId() {
                return nextNodeId;
            }

            void setNextNodeId( String value ) {
                nextNodeId = value;
            }

            String currentNodeId() {
                return currentNodeId;
            }

            void setCurrentNodeId( String value ) {
                currentNodeId = value;
            }

            Optional<String> getResumeFromAndReset() {
                final var  result = ofNullable(resumeFrom);
                resumeFrom = null;
                return result;
            }

            GraphResult getReturnFromEmbedAndReset() {
                var result = returnFromEmbed;
                returnFromEmbed = GraphResult.empty();
                return result;
            }

            void setReturnFromEmbedWithValue( GraphResult value ) {
                returnFromEmbed = requireNonNull(value, "value cannot be null");
            }

        }

        int iteration = 0;
        private final Context context;
        private RunnableConfig config;

        protected AsyncNodeGenerator(GraphInput input, RunnableConfig config )  {
            final var configBuilder = RunnableConfig.builder(config)
                    .checkPointId(null); // Reset checkpoint id

            if( input instanceof GraphResume resumeRequest ) {
                log.trace( "RESUME REQUEST" );

                final var saver = compileConfig.checkpointSaver()
                        .orElseThrow(() -> (new IllegalStateException("Resume request without a configured checkpoint saver!")));
                final var startCheckpoint = saver.get( config )
                                .orElseThrow( () -> new IllegalStateException("Resume request without a valid checkpoint!") );

                final var optionalResumeUpdateData = config.metadata(RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, new TypeRef<Map<String,Object>>() {});

                context = new Context(startCheckpoint);

                final var startCheckpointNextNodeAction = nodes.get(startCheckpoint.getNextNodeId());
                if( startCheckpointNextNodeAction instanceof SubCompiledGraphNodeAction<State> action ) {

                    // RESUME FORM SUBGRAPH DETECTED
                    final var resumeUpdateData = optionalResumeUpdateData
                            .map( data -> mergeMap( data, resumeRequest.value() ))
                            .orElseGet(resumeRequest::value);

                    // RESUME FORM SUBGRAPH DETECTED
                    this.config = configBuilder
                            .addMetadata( action.resumeSubGraphId(), true)
                            .putMetadata( RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA, resumeUpdateData )
                            .build();

                    context.setCurrentState( startCheckpoint.getState() );

                }
                else {
                    final var stateData = optionalResumeUpdateData.orElseGet(resumeRequest::value);

                    this.config = configBuilder
                    //        .removeMetadata( RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA )
                            .build();

                    // FIX ISSUE #302
                    context.setCurrentState( AgentState.updateState( startCheckpoint.getState(),
                            stateData,
                            stateGraph.getChannels() ));

                }

                log.trace( "RESUME FROM {}", startCheckpoint.getNodeId() );
            }
            else {

                log.trace( "START" );
                
                final var initState = initialState( ((GraphArgs)input).value(), config );
                // patch for backward support of AppendableValue
                State initializedState = stateGraph.getStateFactory().apply(initState);
                this.context = new Context( initializedState.data() );
                this.config = configBuilder
                                //.removeMetadata(RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA)
                                .build();
            }
        }

        @SuppressWarnings("unchecked")
        protected Output buildNodeOutput( String nodeId, RunnableConfig config ) throws Exception {
            return  (Output)NodeOutput.of( nodeId, cloneState(context.currentState(), config) );
        }

        @SuppressWarnings("unchecked")
        protected Output buildStateSnapshot( Checkpoint checkpoint ) throws Exception {
            return (Output)StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) ;
        }

        @SuppressWarnings("unchecked")
        private Optional<Data<Output>> embedGenerator(AsyncNodeActionWithConfig<State> action,
                                                      String nodeId,
                                                      State state,
                                                      RunnableConfig runnableConfig,
                                                      Map<String,Object> partialState )
        {
            return partialState.entrySet().stream()
                .filter( e -> e.getValue() instanceof AsyncGenerator)
                .findFirst()
                .map( generatorEntry -> {

                    final var generator = (AsyncGenerator<Output>) generatorEntry.getValue();

                    return Data.composeWith( generator, data -> {

                        final var result = GraphResult.from(data);
                        if (!result.isEmpty()) {

                            if( result.isInterruptionMetadata()  ) {
                                context.setReturnFromEmbedWithValue( result );
                                return;
                            }
                            if ( result.isStateDataOrCheckpointSaverTag() ) {
                                // FIX #102
                                // Assume that the whatever used appender channel doesn't accept duplicates
                                // FIX #104: remove generator
                                final Map<String,Object> partialStateWithoutGenerator = partialState.entrySet().stream()
                                        .filter( e -> e.getKey() != null &&
                                                !Objects.equals(e.getKey(),generatorEntry.getKey()))
                                        .collect( LinkedHashMap::new,
                                                ( map, entry ) ->
                                                        map.put( entry.getKey(), entry.getValue() ),
                                                Map::putAll );

                                final var intermediateState = AgentState.updateState( context.currentState(),
                                        partialStateWithoutGenerator,
                                        stateGraph.getChannels() );

                                context.setCurrentState(intermediateState);

                                context.setReturnFromEmbedWithValue( result );
                                return;
                            }
                            else {
                                throw new IllegalArgumentException("Embedded generator must return a Map");
                            }
                        }

                        context.setReturnFromEmbedWithValue( GraphResult.from(Map.of()) );
                    });
                });
        }

        private Data<Output> applyAction( AsyncNodeActionWithConfig<State> action,
                                          String nodeId,
                                          State clonedState,
                                          RunnableConfig runnableConfig ) throws ExecutionException, InterruptedException
        {
            final AgentStateFactory<State> stateFactory = ( data ) -> {
                context.setCurrentState( data );
                return stateGraph.getStateFactory().apply( data);
            };
            return stateGraph.nodeHooks.applyActionWithHooksHandlingInterruption(  action, nodeId, clonedState, compileConfig, runnableConfig, stateFactory, stateGraph.getChannels() )
                .thenApply(TryFunction.Try(result -> {
                    if( result.hasPartialState() ) {
                        return result.partialState().thenApply( TryFunction.<Map<String,Object>, Data<Output>, Exception>Try(partial -> {
                            Optional<Data<Output>> embed = embedGenerator( action, nodeId, clonedState, runnableConfig, partial);
                            if (embed.isPresent()) {
                                return embed.get();
                            }

                            context.setCurrentState( AgentState.updateState(context.currentState(), partial, stateGraph.getChannels()) );

                            if (compileConfig.interruptBeforeEdge() && compileConfig.interruptsAfter().contains(context.currentNodeId())) {
                                //nextNodeId = INTERRUPT_AFTER;
                                context.setNextNodeId(INTERRUPT_AFTER);
                            } else {
                                var nextNodeCommand = nextNodeId(context.currentNodeId(), context.currentState(), runnableConfig);
                                context.setNextNodeId(nextNodeCommand.gotoNode());
                                context.setCurrentState( nextNodeCommand.update() );
                            }

                            return Data.of(nodeOutput());
                        }));
                    }
                    return result.interruptionMetadata().thenApply(Data::<Output>done);
                }))
                .thenCompose( result -> result )
                .get();
        }

        private CompletableFuture<Output> nodeOutput() throws Exception {
            Optional<Checkpoint>  cp = addCheckpoint(config, context.currentNodeId(), context.currentState(), context.nextNodeId());
            return completedFuture(( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                    buildStateSnapshot(cp.get()) :
                    buildNodeOutput( context.currentNodeId(), config ))
                    ;
        }

        private Optional<BaseCheckpointSaver.Tag> releaseThread() throws Exception {
            if(compileConfig.releaseThread() && compileConfig.checkpointSaver().isPresent() ) {
                return Optional.of(compileConfig.checkpointSaver().get().release( config ));
            }
            return Optional.empty();
        }

        @Override
        public Data<Output> next() {

            final var maxIterations = compileConfig.recursionLimit();

            try {
                // GUARD: CHECK MAX ITERATION REACHED
                if( ++iteration > maxIterations ) {
                    // log.warn( "Maximum number of iterations ({}) reached!", maxIterations);
                    return Data.error( new IllegalStateException( "Maximum number of iterations (%d) reached!".formatted( maxIterations)) );
                }

                // GUARD: CHECK IF IT IS END
                if( context.nextNodeId() == null && context.currentNodeId() == null  ) {
                    return releaseThread()
                            .map(Data::<Output>done)
                            .orElseGet( () -> Data.done(context.currentState()) );
                }

                final var returnFromEmbed = context.getReturnFromEmbedAndReset();

                // IS IT A RESUME FROM EMBED ?
                if( !returnFromEmbed.isEmpty() ) {

                    if( returnFromEmbed.isInterruptionMetadata() ) {
                        return Data.done( returnFromEmbed.asInterruptionMetadata() );
                    }

                    // FIX #336
                    // Streaming completed: call AfterHook here
                    final CompletableFuture<Map<String,Object>> future;
                    if( !stateGraph.nodeHooks.afterCalls.isEmpty() ) {
                        future = stateGraph.nodeHooks.afterCalls.apply(
                                        context.currentNodeId(),
                                        stateGraph.getStateFactory().apply(context.currentState()),
                                        config,
                                        returnFromEmbed.asStateDataOrLastCheckpointStateData());
                    }
                    else {
                        future = completedFuture( returnFromEmbed.asStateDataOrLastCheckpointStateData() );
                    }

                    config = config.removeMetadata(
                            resumeSubGraphId( context.currentNodeId() ),
                            RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA );

                    return Data.of( future.thenCompose( TryFunction.Try((partialResult) -> {

                        final var updatedState = AgentState.updateState( context.currentState(),
                                partialResult,
                                stateGraph.getChannels() );

                        final var nextNodeCommand = nextNodeId(context.currentNodeId(), updatedState, config);
                        context.setNextNodeId(nextNodeCommand.gotoNode());
                        context.setCurrentState(nextNodeCommand.update());

                        return nodeOutput();
                    })));
                }

                if( START.equals(context.currentNodeId()) ) {
                    var nextNodeCommand = entryPoint(context.currentState(), config) ;
                    context.setNextNodeId(nextNodeCommand.gotoNode());
                    context.setCurrentState( nextNodeCommand.update() );

                    var cp = addCheckpoint( config, START, context.currentState(), context.nextNodeId() );

                    var output =  ( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                            buildStateSnapshot(cp.get()) :
                            buildNodeOutput( context.currentNodeId(), config );

                    context.setCurrentNodeId(context.nextNodeId());

                    return Data.of( output );
                }

                if( END.equals(context.nextNodeId()) ) {
                    context.reset();
                    return Data.of( buildNodeOutput( END, config ) );
                }

                final var resumeFrom = context.getResumeFromAndReset();
                if( resumeFrom.isPresent() ) {

                    if(compileConfig.interruptBeforeEdge() && Objects.equals( context.nextNodeId(), INTERRUPT_AFTER)) {
                        var nextNodeCommand = nextNodeId( resumeFrom.get(), context.currentState(), config);
                        context.setNextNodeId( nextNodeCommand.gotoNode() );
                        context.setCurrentState(  nextNodeCommand.update() );
                        context.setCurrentNodeId( null );
                    }
                }

                // check on previous node
                if( shouldInterruptAfter( context.currentNodeId(), context.nextNodeId() )) {
                    return Data.done( InterruptionMetadata.builder(context.currentNodeId(), cloneState(context.currentState(), config)).build() );
                }

                if( shouldInterruptBefore( context.nextNodeId(), context.currentNodeId() ) ) {
                    return Data.done(InterruptionMetadata.builder(context.currentNodeId(), cloneState(context.currentState(), config)).build() );
                }

                context.setCurrentNodeId( context.nextNodeId() );

                //final var newConfig = updateRunnableConfigMetadata( config, context.currentNodeId() );
                config = updateRunnableConfigMetadata( config, context.currentNodeId() );

                //
                // EVALUATE ACTION
                //
                final var action = nodes.get( context.currentNodeId() );

                if (action == null)
                    throw RunErrors.missingNode.exception( config, context.currentNodeId());

                final var clonedState = cloneState(context.currentState(), config);

                try {
                    return applyAction(action, context.currentNodeId(), clonedState, config);
                }
                catch( InterruptedException ex ) {
                    if( action instanceof ParallelNode.AsyncParallelNodeAction<?> parallelNodeAction ) {
                        log.info( "PARALLEL NODE {} INTERRUPTED!", context.currentNodeId() );
                    }
                    throw ex;
                }

            }
            catch( GraphRunnerException e ) {
                log.error( e.getMessage(), e );
                return Data.error( e );
            }
            catch( Throwable e ) {
                log.error( e.getMessage(), e );
                return Data.error( new GraphRunnerException( config, e) );
            }

        }
    }

    record ProcessedNodesEdgesAndConfig<State extends AgentState>(
            StateGraph.Nodes<State> nodes,
            StateGraph.Edges<State> edges,
            Set<String> interruptsBefore,
            Set<String> interruptsAfter) {

        ProcessedNodesEdgesAndConfig(StateGraph<State> stateGraph, CompileConfig config) {
            this(   stateGraph.nodes,
                    stateGraph.edges,
                    config.interruptsBefore(),
                    config.interruptsAfter() );
        }

        static <State extends AgentState> ProcessedNodesEdgesAndConfig<State> process(StateGraph<State> stateGraph, CompileConfig config ) throws GraphStateException {

            var subgraphNodes = stateGraph.nodes.onlySubStateGraphNodes();

            if( subgraphNodes.isEmpty() ) {
                return new ProcessedNodesEdgesAndConfig<>( stateGraph, config );
            }

            var interruptsBefore = config.interruptsBefore();
            var interruptsAfter = config.interruptsAfter();
            var nodes = new StateGraph.Nodes<>( stateGraph.nodes.exceptSubStateGraphNodes() );
            var edges = new StateGraph.Edges<>( stateGraph.edges.elements);

            for( var subgraphNode : subgraphNodes ) {

                var sgWorkflow = subgraphNode.subGraph();

                //
                // Process START Node
                //
                var sgEdgeStart = sgWorkflow.edges.edgeBySourceId(START).orElseThrow();

                if( sgEdgeStart.isParallel() ) {
                    throw new GraphStateException( "subgraph not support start with parallel branches yet!"  );
                }

                var sgEdgeStartTarget = sgEdgeStart.target();

                if( sgEdgeStartTarget.id() == null && sgEdgeStartTarget.value() == null ) {
                    throw new GraphStateException( "the target for node '%s' is null!".formatted( subgraphNode.id())  );
                }

                String sgEdgeStartRealTargetId;
                if ( sgEdgeStartTarget.id() != null ) {
                    sgEdgeStartRealTargetId = subgraphNode.formatId(sgEdgeStartTarget.id());
                } else {
                    // When subgraph start edge is a conditional edge, use an empty node to bridge parent and subgraph
                    sgEdgeStartRealTargetId = subgraphNode.formatId(START);

                    var mappings = sgEdgeStartTarget.value().mappings();
                    var updatedMappings = mappings.entrySet().stream()
                        .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> subgraphNode.formatId(entry.getValue())));

                    var sgStartEmptyNode = new Node<State>(
                        sgEdgeStartRealTargetId,
                        (c) -> AsyncNodeActionWithConfig.noop());

                    var updatedEdgeCondition = new EdgeCondition<>(
                        sgEdgeStartTarget.value().action(),
                        updatedMappings);

                    var sgStartEmptyNodeEdge = new Edge<>(
                        sgEdgeStartRealTargetId,
                        new EdgeValue<>(updatedEdgeCondition));

                    nodes.elements.add(sgStartEmptyNode);
                    edges.elements.add(sgStartEmptyNodeEdge);
                }

                // Process Interruption (Before) Subgraph(s)
                interruptsBefore = interruptsBefore.stream().map( interrupt ->
                        Objects.equals( subgraphNode.id(), interrupt ) ?
                                sgEdgeStartRealTargetId :
                                interrupt
                ).collect(Collectors.toUnmodifiableSet());

                var edgesWithSubgraphTargetId =  edges.edgesByTargetId( subgraphNode.id() );

                if( edgesWithSubgraphTargetId.isEmpty() ) {
                    throw new GraphStateException( "the node '%s' is not present as target in graph!".formatted( subgraphNode.id() ) );
                }

                for( var edgeWithSubgraphTargetId : edgesWithSubgraphTargetId  ) {

                    var newEdge = edgeWithSubgraphTargetId.withSourceAndTargetIdsUpdated( subgraphNode,
                            Function.identity(),
                            id -> new EdgeValue<>( (Objects.equals( id, subgraphNode.id() ) ?
                                    sgEdgeStartRealTargetId : id)));
                    edges.elements.remove(edgeWithSubgraphTargetId);
                    edges.elements.add( newEdge );

                }
                //
                // Process END Nodes
                //
                var sgEdgesEnd = sgWorkflow.edges.edgesByTargetId(END);

                var edgeWithSubgraphSourceId = edges.edgeBySourceId( subgraphNode.id() ).orElseThrow();

                if( edgeWithSubgraphSourceId.isParallel() ) {
                    throw new GraphStateException( "subgraph not support routes to parallel branches yet!" );
                }

                // Process Interruption (After) Subgraph(s)
                if( interruptsAfter.contains(subgraphNode.id()) ) {

                    var exceptionMessage = ( edgeWithSubgraphSourceId.target().id()==null ) ?
                            "'interruption after' on subgraph is not supported yet!" :
                            "'interruption after' on subgraph is not supported yet! consider to use 'interruption before' node: '%s'"
                                .formatted( edgeWithSubgraphSourceId.target().id() );
                    throw new GraphStateException( exceptionMessage );

                }

                var sgEdgeEndTarget = edgeWithSubgraphSourceId.target();
                if( sgEdgeEndTarget.id() == null && sgEdgeEndTarget.value() == null ) {
                    throw new GraphStateException( "the target for node '%s' is null!".formatted( subgraphNode.id())  );
                }

                String sgEdgeEndRealTargetId;
                if ( sgEdgeEndTarget.id() != null ) {
                    sgEdgeEndRealTargetId = sgEdgeEndTarget.id();
                } else {
                    // When subgraph end edge is a conditional edge, use an empty node to bridge parent and subgraph
                    sgEdgeEndRealTargetId = subgraphNode.formatId(END);

                    var sgEndEmptyNode = new Node<State>(
                        sgEdgeEndRealTargetId,
                        (c) -> AsyncNodeActionWithConfig.noop());

                    var sgEndEmptyNodeEdge = new Edge<>(sgEdgeEndRealTargetId, edgeWithSubgraphSourceId.target());

                    nodes.elements.add(sgEndEmptyNode);
                    edges.elements.add(sgEndEmptyNodeEdge);
                }


                sgEdgesEnd.stream()
                        .map( e -> e.withSourceAndTargetIdsUpdated( subgraphNode,
                                subgraphNode::formatId,
                                id  -> new EdgeValue<>(Objects.equals(id,END) ?
                                        sgEdgeEndRealTargetId : subgraphNode.formatId(id)))
                        )
                        .forEach( edges.elements::add);
                edges.elements.remove(edgeWithSubgraphSourceId);


                //
                // Process edges
                //
                sgWorkflow.edges.elements.stream()
                        .filter( e -> !Objects.equals( e.sourceId(),START) )
                        .filter( e -> !e.anyMatchByTargetId(END) )
                        .map( e ->
                                e.withSourceAndTargetIdsUpdated( subgraphNode,
                                        subgraphNode::formatId,
                                        id  -> new EdgeValue<>( subgraphNode.formatId(id))) )
                        .forEach(edges.elements::add);

                //
                // Process nodes
                //
                sgWorkflow.nodes.elements.stream()
                        .map( n -> n.withIdUpdated( subgraphNode::formatId) )
                        .forEach(nodes.elements::add);

            }

            return  new ProcessedNodesEdgesAndConfig<>(
                    nodes,
                    edges,
                    interruptsBefore,
                    interruptsAfter );

        }

    }

}
