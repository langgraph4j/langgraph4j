package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.internal.edge.Edge;
import org.bsc.langgraph4j.internal.edge.EdgeValue;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Represents a compiled graph of nodes and edges.
 * This class manage the StateGraph execution
 *
 * @param <State> the type of the state associated with the graph
 */
public class CompiledGraph<State extends AgentState> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CompiledGraph.class);

    /**
     * Enum representing various error messages related to graph runner.
     */
    enum RunnableErrors {
        missingNodeInEdgeMapping("cannot find edge mapping for id: '%s' in conditional edge with sourceId: '%s' "),
        missingNode("node with id: '%s' doesn't exist!"),
        missingEdge("edge with sourceId: '%s' doesn't exist!"),
        executionError("%s");

        private final String errorMessage;

        RunnableErrors(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        /**
         * Creates a new GraphRunnerException with the formatted error message.
         *
         * @param args the arguments to format the error message
         * @return a new GraphRunnerException
         */
        GraphRunnerException exception(String... args) {
            return new GraphRunnerException(format(errorMessage, (Object[]) args));
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

    private int maxIterations = 25;

    public final CompileConfig compileConfig;

    /**
     * Constructs a CompiledGraph with the given StateGraph.
     *
     * @param stateGraph the StateGraph to be used in this CompiledGraph
     */
    protected CompiledGraph(StateGraph<State> stateGraph, CompileConfig compileConfig ) throws GraphStateException {
        this.stateGraph = stateGraph;

        this.processedData = ProcessedNodesEdgesAndConfig.process( stateGraph, compileConfig );

        // CHECK INTERRUPTIONS
        for (String interruption : processedData.interruptsBefore() ) {
            if (!processedData.nodes().anyMatchById( interruption )) {
                throw StateGraph.Errors.interruptionNodeNotExist.exception(interruption);
            }
        }
        for (String interruption : processedData.interruptsBefore() ) {
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
            Objects.requireNonNull(factory, format("action factory for node id '%s' is null!", n.id()));
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
        BaseCheckpointSaver saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

        return saver.list(config).stream()
                .map( checkpoint -> StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) )
                .collect(toList());
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
        BaseCheckpointSaver saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

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

        BaseCheckpointSaver saver = compileConfig.checkpointSaver().orElseThrow( () -> (new IllegalStateException("Missing CheckpointSaver!")) );

        // merge values with checkpoint values
        Checkpoint branchCheckpoint = saver.get(config)
                            .map(Checkpoint::copyOf)
                            .map( cp -> cp.updateState(values, stateGraph.getChannels()) )
                            .orElseThrow( () -> (new IllegalStateException("Missing Checkpoint!")) );

        String nextNodeId = null;
        if( asNode != null ) {
            var nextNodeCommand = nextNodeId( asNode, branchCheckpoint.getState(), config );

            nextNodeId = nextNodeCommand.gotoNode();
            branchCheckpoint =  branchCheckpoint.updateState( nextNodeCommand.update(), stateGraph.getChannels() );

        }
        // update checkpoint in saver
        RunnableConfig newConfig = saver.put( config, branchCheckpoint );

        return RunnableConfig.builder(newConfig)
                                .checkPointId( branchCheckpoint.getId() )
                                .nextNode( nextNodeId )
                                .build();
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
     * Sets the maximum number of iterations for the graph execution.
     *
     * @param maxIterations the maximum number of iterations
     * @throws IllegalArgumentException if maxIterations is less than or equal to 0
     */
    public void setMaxIterations(int maxIterations) {
        if( maxIterations <= 0 ) {
            throw new IllegalArgumentException("maxIterations must be > 0!");
        }
        this.maxIterations = maxIterations;
    }

    private Command nextNodeId(EdgeValue<State> route , Map<String,Object> state, String nodeId, RunnableConfig config ) throws Exception {

        if( route == null ) {
            throw RunnableErrors.missingEdge.exception(nodeId);
        }
        if( route.id() != null ) {
            return new Command(route.id(), state);
        }
        if( route.value() != null ) {
            State derefState = stateGraph.getStateFactory().apply(state);

            var command = route.value().action().apply(derefState,config).get();

            var newRoute = command.gotoNode();

            String result = route.value().mappings().get(newRoute);
            if( result == null ) {
                throw RunnableErrors.missingNodeInEdgeMapping.exception(nodeId, newRoute);
            }

            var currentState = AgentState.updateState(state, command.update(), stateGraph.getChannels());

            return new Command(result, currentState);
        }
        throw RunnableErrors.executionError.exception( format("invalid edge value for nodeId: [%s] !", nodeId) );
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

    private Command getEntryPoint( Map<String,Object> state, RunnableConfig config ) throws Exception {
        var entryPoint = this.edges.get(START);
        return nextNodeId(entryPoint, state, "entryPoint", config);
    }

    private boolean shouldInterruptBefore( String nodeId, String previousNodeId ) {
        Objects.requireNonNull( nodeId, "nodeId cannot be null" );
        if( previousNodeId == null ) { // FIX RESUME ERROR
            return false;
        }
        return compileConfig.interruptsBefore().contains(nodeId);
    }

    private boolean shouldInterruptAfter(String nodeId, String previousNodeId ) {
        if( nodeId == null || Objects.equals(nodeId, previousNodeId) ) { // FIX RESUME ERROR
            return false;
        }
        return compileConfig.interruptsAfter().contains(nodeId);
    }

    private Optional<Checkpoint> addCheckpoint( RunnableConfig config, String nodeId, Map<String,Object> state, String nextNodeId ) throws Exception {
        if( compileConfig.checkpointSaver().isPresent() ) {
            var cp =  Checkpoint.builder()
                                .nodeId( nodeId )
                                .state( cloneState(state) )
                                .nextNodeId( nextNodeId )
                                .build();
            compileConfig.checkpointSaver().get().put( config, cp );
            return Optional.of(cp);
        }
        return Optional.empty();

    }

    Map<String,Object> getInitialStateFromSchema() {
        return stateGraph.getStateFactory().initialDataFromSchema(stateGraph.getChannels());
    }

    Map<String,Object> getInitialState(Map<String,Object> inputs, RunnableConfig config) {

        return compileConfig.checkpointSaver()
                .flatMap( saver -> saver.get( config ) )
                .map( cp -> AgentState.updateState( cp.getState(), inputs, stateGraph.getChannels() ))
                .orElseGet( () -> AgentState.updateState( getInitialStateFromSchema(), inputs, stateGraph.getChannels() ));
    }

    State cloneState( Map<String,Object> data ) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        return stateGraph.getStateSerializer().cloneObject(data);
    }


    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> stream( Map<String,Object> inputs, RunnableConfig config ) {
        Objects.requireNonNull(config, "config cannot be null");
        final AsyncNodeGenerator<NodeOutput<State>> generator = new AsyncNodeGenerator<>( inputs, config );

        return new AsyncGenerator.WithEmbed<>( generator );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> stream(Map<String,Object> inputs ) {
        return this.stream( inputs, RunnableConfig.builder().build() );
    }
    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an Optional containing the final state if present, otherwise an empty Optional
     */
    public Optional<State> invoke(Map<String,Object> inputs, RunnableConfig config ) {

       return stream(inputs, config).stream()
                                        .reduce((a, b) -> b)
                                        .map( NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @return an Optional containing the final state if present, otherwise an empty Optional
     */
    public Optional<State> invoke(Map<String,Object> inputs )  {
        return this.invoke( inputs, RunnableConfig.builder().build() );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> streamSnapshots( Map<String,Object> inputs, RunnableConfig config )  {
        Objects.requireNonNull(config, "config cannot be null");

        final AsyncNodeGenerator<NodeOutput<State>> generator = new AsyncNodeGenerator<>( inputs, config.withStreamMode(StreamMode.SNAPSHOTS) );
        return new AsyncGenerator.WithEmbed<>( generator );
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

        String content = type.generator.generate( processedData.nodes(), processedData.edges(), title, printConditionalEdges);

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

        String content = type.generator.generate( processedData.nodes(), processedData.edges(), title, true);

        return new GraphRepresentation( type, content );
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
     * Async Generator for streaming outputs.
     *
     * @param <Output> the type of the output
     */
    public class AsyncNodeGenerator<Output extends NodeOutput<State>> implements AsyncGenerator<Output> {

        Map<String,Object> currentState;
        String currentNodeId;
        String nextNodeId;
        int iteration = 0;
        RunnableConfig config;
        boolean resumedFromEmbed = false;

        protected AsyncNodeGenerator(Map<String,Object> inputs, RunnableConfig config )  {
            final boolean isResumeRequest =  (inputs == null);

            if( isResumeRequest ) {

                log.trace( "RESUME REQUEST" );

                var saver = compileConfig.checkpointSaver()
                        .orElseThrow(() -> (new IllegalStateException("inputs cannot be null (ie. resume request) if no checkpoint saver is configured")));
                var startCheckpoint = saver.get( config )
                        .orElseThrow( () -> (new IllegalStateException("Resume request without a saved checkpoint!")) );

                this.currentState = startCheckpoint.getState();

                // Reset checkpoint id
                this.config = config.withCheckPointId( null );


                this.nextNodeId = startCheckpoint.getNextNodeId();
                this.currentNodeId = null;
                log.trace( "RESUME FROM {}", startCheckpoint.getNodeId() );
            }
            else {

                log.trace( "START" );
                
                Map<String,Object> initState = getInitialState(inputs, config );
                // patch for backward support of AppendableValue
                State initializedState = stateGraph.getStateFactory().apply(initState);
                this.currentState = initializedState.data();
                this.nextNodeId = null;
                this.currentNodeId = START;
                this.config = config;
            }
        }

        @SuppressWarnings("unchecked")
        protected Output buildNodeOutput(String nodeId ) throws Exception {
            return  (Output)NodeOutput.of( nodeId, cloneState(currentState) );
        }

        @SuppressWarnings("unchecked")
        protected Output buildStateSnapshot( Checkpoint checkpoint ) throws Exception {
            return (Output)StateSnapshot.of( checkpoint, config, stateGraph.getStateFactory() ) ;
        }

        @SuppressWarnings("unchecked")
        private Optional<Data<Output>> getEmbedGenerator( Map<String,Object> partialState ) {
            return partialState.entrySet().stream()
                    .filter( e -> e.getValue() instanceof AsyncGenerator)
                    .findFirst()
                    .map( generatorEntry -> {
                        final var generator = (AsyncGenerator<Output>) generatorEntry.getValue();
                        return Data.composeWith( generator.map( n -> { n.setSubGraph(true); return n; } ), data -> {

                            if (data != null) {

                                if (data instanceof Map<?,?>) {
                                    // FIX #102
                                    // Assume that the whatever used appender channel doesn't accept duplicates
                                    // FIX #104: remove generator
                                    var partialStateWithoutGenerator = partialState.entrySet().stream()
                                            .filter( e -> !Objects.equals(e.getKey(),generatorEntry.getKey()))
                                            .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue));

                                    var intermediateState = AgentState.updateState( currentState, partialStateWithoutGenerator, stateGraph.getChannels() );

                                    currentState = AgentState.updateState( intermediateState, (Map<String,Object>)data, stateGraph.getChannels() );
                                }
                                else {
                                    throw new IllegalArgumentException("Embedded generator must return a Map");
                                }
                            }

                            var nextNodeCommand = nextNodeId(currentNodeId, currentState, config) ;
                            nextNodeId = nextNodeCommand.gotoNode();
                            currentState = nextNodeCommand.update();

                            resumedFromEmbed = true;
                        });
                    })
                    ;
        }

        private CompletableFuture<Data<Output>> evaluateAction(AsyncNodeActionWithConfig<State> action, State withState ) {

                return action.apply( withState, config ).thenApply( updateState -> {
                    try {

                        Optional<Data<Output>> embed = getEmbedGenerator( updateState );
                        if( embed.isPresent() ) {
                            return embed.get();
                        }

                        currentState = AgentState.updateState(currentState, updateState, stateGraph.getChannels());

                        var nextNodeCommand = nextNodeId(currentNodeId, currentState, config) ;
                        nextNodeId = nextNodeCommand.gotoNode();
                        currentState = nextNodeCommand.update();

                        return Data.of( getNodeOutput() );
                    }
                    catch (Exception e) {
                        throw new CompletionException(e);
                    }

                });
        }

        /**
         * evaluate Action without nested support
         */
        private CompletableFuture<Output> evaluateActionWithoutNested( AsyncNodeAction<State> action, State withState  ) {

            return action.apply( withState ).thenApply(  partialState -> {
                try {
                    currentState = AgentState.updateState(currentState, partialState, stateGraph.getChannels());

                    var nextNodeCommand = nextNodeId(currentNodeId, currentState, config) ;
                    nextNodeId = nextNodeCommand.gotoNode();
                    currentState = nextNodeCommand.update();


                    Optional<Checkpoint>  cp = addCheckpoint(config, currentNodeId, currentState, nextNodeId);
                    return ( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                        buildStateSnapshot(cp.get()) :
                        buildNodeOutput( currentNodeId )
                            ;

                }
                catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

        }

        private CompletableFuture<Output> getNodeOutput() throws Exception {
            Optional<Checkpoint>  cp = addCheckpoint(config, currentNodeId, currentState, nextNodeId);
            return completedFuture(( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                    buildStateSnapshot(cp.get()) :
                    buildNodeOutput( currentNodeId ))
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
            try {
                // GUARD: CHECK MAX ITERATION REACHED
                if( ++iteration > maxIterations ) {
                    // log.warn( "Maximum number of iterations ({}) reached!", maxIterations);
                    return Data.error( new IllegalStateException( format("Maximum number of iterations (%d) reached!", maxIterations)) );
                }

                // GUARD: CHECK IF IT IS END
                if( nextNodeId == null &&  currentNodeId == null  ) {
                    return releaseThread()
                            .map(Data::<Output>done)
                            .orElseGet( () -> Data.done(currentState) );
                }

                // IS IT A RESUME FROM EMBED ?
                if(resumedFromEmbed) {
                    final CompletableFuture<Output> future = getNodeOutput();
                    resumedFromEmbed = false;
                    return Data.of( future );
                }

                if( START.equals(currentNodeId) ) {
                    var nextNodeCommand = getEntryPoint(currentState, config) ;
                    nextNodeId = nextNodeCommand.gotoNode();
                    currentState = nextNodeCommand.update();

                    var cp = addCheckpoint( config, START, currentState, nextNodeId );

                    var output =  ( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                            buildStateSnapshot(cp.get()) :
                            buildNodeOutput( currentNodeId );

                    currentNodeId = nextNodeId;

                    return Data.of( output );
                }

                if( END.equals(nextNodeId) ) {
                    nextNodeId = null;
                    currentNodeId = null;
                    return Data.of( buildNodeOutput( END ) );
                }

                // check on previous node
                if( shouldInterruptAfter( currentNodeId, nextNodeId )) {
                    return Data.done(currentNodeId);
                }

                if( shouldInterruptBefore( nextNodeId, currentNodeId ) ) {
                    return Data.done(currentNodeId);
                }

                currentNodeId = nextNodeId;

                var action = nodes.get(currentNodeId);

                if (action == null)
                    throw RunnableErrors.missingNode.exception(currentNodeId);

                return evaluateAction(action, cloneState(currentState) ).get();
            }
            catch( Exception e ) {
                log.error( e.getMessage(), e );
                return Data.error(e);
            }

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

            if( sgEdgeStartTarget.id() == null ) {
                throw new GraphStateException( format("the target for node '%s' is null!", subgraphNode.id())  );
            }

            var sgEdgeStartRealTargetId = subgraphNode.formatId( sgEdgeStartTarget.id()  );

            // Process Interruption (Before) Subgraph(s)
            interruptsBefore = interruptsBefore.stream().map( interrupt ->
                Objects.equals( subgraphNode.id(), interrupt ) ?
                        sgEdgeStartRealTargetId :
                        interrupt
            ).collect(Collectors.toUnmodifiableSet());

            var edgesWithSubgraphTargetId =  edges.edgesByTargetId( subgraphNode.id() );

            if( edgesWithSubgraphTargetId.isEmpty() ) {
                throw new GraphStateException( format("the node '%s' is not present as target in graph!", subgraphNode.id())  );
            }

            for( var edgeWithSubgraphTargetId : edgesWithSubgraphTargetId  ) {

                var newEdge = edgeWithSubgraphTargetId.withSourceAndTargetIdsUpdated( subgraphNode,
                        Function.identity(),
                        id -> new EdgeValue<>( (Objects.equals( id, subgraphNode.id() ) ?
                                            subgraphNode.formatId( sgEdgeStartTarget.id()  ) : id)));
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
                                format("'interruption after' on subgraph is not supported yet! consider to use 'interruption before' node: '%s'",
                                        edgeWithSubgraphSourceId.target().id());
                throw new GraphStateException( exceptionMessage );

            }

            sgEdgesEnd.stream()
                    .map( e -> e.withSourceAndTargetIdsUpdated( subgraphNode,
                                    subgraphNode::formatId,
                                    id  -> (Objects.equals(id,END) ?
                                                    edgeWithSubgraphSourceId.target() :
                                                    new EdgeValue<>(subgraphNode.formatId(id)) ) )
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
