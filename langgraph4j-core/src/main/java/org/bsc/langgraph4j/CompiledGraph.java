package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.cancellation.AbortController;
import org.bsc.langgraph4j.cancellation.GraphCancellationException;
import org.bsc.langgraph4j.cancellation.StreamWithController;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.internal.edge.Edge;
import org.bsc.langgraph4j.internal.edge.EdgeValue;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.internal.node.SubCompiledGraphNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.bsc.langgraph4j.utils.TypeRef;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
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

    private static final String INTERRUPT_AFTER = "__INTERRUPTED__";

    /**
     * Enum representing various error messages related to graph runner.
     */
    public enum RunnableErrors {
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
            requireNonNull(factory, format("action factory for node id '%s' is null!", n.id()));
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
     * Creates an AsyncGenerator stream with cancellation support.
     *
     * @param inputs the input map
     * @param abortController the abort controller for cancellation
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> streamCancellable(
            Map<String,Object> inputs,
            AbortController abortController) {

        RunnableConfig config = RunnableConfig.builder()
                .abortController(abortController)
                .build();

        return stream(inputs, config);
    }

    /**
     * Creates an AsyncGenerator stream with a new cancellation controller.
     *
     * @param inputs the input map
     * @return an AsyncGenerator stream of NodeOutput and the AbortController
     */
    public StreamWithController<State> streamCancellable(Map<String,Object> inputs) {
        AbortController abortController = AbortController.create();
        AsyncGenerator<NodeOutput<State>> stream = streamCancellable(inputs, abortController);
        return new StreamWithController<>(stream, abortController);
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
        requireNonNull( nodeId, "nodeId cannot be null" );
        if( previousNodeId == null ) { // FIX RESUME ERROR
            return false;
        }
        return compileConfig.interruptsBefore().contains(nodeId);
    }

    private boolean shouldInterruptAfter(String nodeId, String previousNodeId ) {
        if( nodeId == null || Objects.equals(nodeId, previousNodeId) ) { // FIX RESUME ERROR
            return false;
        }
        return ( compileConfig.interruptBeforeEdge() && Objects.equals(nodeId, INTERRUPT_AFTER )) ||
                compileConfig.interruptsAfter().contains(nodeId);
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
     * @param input the input data
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> stream( GraphInput input, RunnableConfig config ) {
        requireNonNull(config, "config cannot be null");
        requireNonNull( input, "input cannot be null" );

        final var generator = new AsyncNodeGenerator<>( input, config );

        return new AsyncGenerator.WithEmbed<>( generator );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> stream( Map<String,Object> inputs, RunnableConfig config ) {
        return stream(  ( inputs == null ) ? new GraphResume() : new GraphArgs(inputs), config );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> stream(Map<String,Object> inputs ) {
        return this.stream( GraphInput.args(inputs), RunnableConfig.builder().build() );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param input the input data
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> streamSnapshots( GraphInput input, RunnableConfig config )  {
        requireNonNull(config, "config cannot be null");

        final AsyncNodeGenerator<NodeOutput<State>> generator = new AsyncNodeGenerator<>( input, config.withStreamMode(StreamMode.SNAPSHOTS) );
        return new AsyncGenerator.WithEmbed<>( generator );
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput<State>> streamSnapshots( Map<String,Object> inputs, RunnableConfig config )  {
        return streamSnapshots( ( inputs == null ) ? new GraphResume() : new GraphArgs(inputs), config );
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
        return stream(input, config).stream()
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
     */
    public Optional<State> invoke(Map<String,Object> inputs, RunnableConfig config ) {
        return invokeFinal( GraphInput.args(inputs), config ).map( NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @return an Optional containing the final state if present, otherwise an empty Optional
     */
    public Optional<State> invoke(Map<String,Object> inputs )  {
        return invokeFinal( GraphInput.args(inputs), RunnableConfig.builder().build() ).map( NodeOutput::state);
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

        static class Context {
            record ReturnFromEmbed( Object value ) {
                <T> Optional<T> value( TypeRef<T> ref ) {
                    return ofNullable(value)
                            .flatMap(ref::cast);
                }

            }
            private String currentNodeId;
            private String nextNodeId;
            private String resumeFrom;
            private ReturnFromEmbed returnFromEmbed;

            Context() {
                currentNodeId = START;
                nextNodeId = null;
                resumeFrom = null;
                returnFromEmbed = null;
            }

            Context( Checkpoint cp ) {
                currentNodeId = null;
                nextNodeId = cp.getNextNodeId();
                resumeFrom = cp.getNodeId();
            }

            void reset() {
                currentNodeId = null;
                nextNodeId = null;
                resumeFrom = null;
                returnFromEmbed = null;
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

            Optional<ReturnFromEmbed> getReturnFromEmbedAndReset() {
                var result = ofNullable(returnFromEmbed);
                returnFromEmbed = null;
                return result;
            }

            void setReturnFromEmbedWithValue( Object value ) {
                returnFromEmbed = new ReturnFromEmbed(value);
            }

        }

        Map<String,Object> currentState;
        final Context context;
        int iteration = 0;
        final RunnableConfig config;

        protected AsyncNodeGenerator(GraphInput input, RunnableConfig config )  {
            final boolean isResumeRequest =  (input instanceof GraphResume);

            if( isResumeRequest ) {

                log.trace( "RESUME REQUEST" );

                var saver = compileConfig.checkpointSaver()
                        .orElseThrow(() -> (new IllegalStateException("Resume request without a configured checkpoint saver!")));
                var startCheckpoint = saver.get( config )
                        .orElseThrow( () -> (new IllegalStateException("Resume request without a valid checkpoint!")) );

                this.currentState = startCheckpoint.getState();

                var startCheckpointNextNodeAction = nodes.get(startCheckpoint.getNextNodeId());
                if( startCheckpointNextNodeAction instanceof SubCompiledGraphNodeAction<State> action ) {

                    // RESUME FORM SUBGRAPH DETECTED

                    this.config = RunnableConfig.builder(config)
                                 .checkPointId(null) // Reset checkpoint id
                                .addMetadata( action.resumeSubGraphId(), true) // add metadata for sub graph
                                .build();
                }
                else {
                    // Reset checkpoint id
                    this.config = config.withCheckPointId( null );

                }

                context = new Context(startCheckpoint);
                //this.nextNodeId = startCheckpoint.getNextNodeId();
                //this.currentNodeId = null;
                log.trace( "RESUME FROM {}", startCheckpoint.getNodeId() );
            }
            else {

                log.trace( "START" );
                
                Map<String,Object> initState = getInitialState( ((GraphArgs)input).value(), config );
                // patch for backward support of AppendableValue
                State initializedState = stateGraph.getStateFactory().apply(initState);
                this.currentState = initializedState.data();
                this.context = new Context();
                //this.nextNodeId = null;
                //this.currentNodeId = START;
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
        private Optional<Data<Output>> getEmbedGenerator( AsyncNodeActionWithConfig<State> action,
                                                          Map<String,Object> partialState )
        {
            return partialState.entrySet().stream()
                .filter( e -> e.getValue() instanceof AsyncGenerator)
                .findFirst()
                .map( generatorEntry -> {

                    final var generator = (AsyncGenerator<Output>) generatorEntry.getValue();

                    return Data.composeWith( generator, data -> {

                            if (data != null) {

                                if( data instanceof InterruptionMetadata<?>  ) {
                                    context.setReturnFromEmbedWithValue( data );
                                    return;
                                }
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

                            var nextNodeCommand = nextNodeId(context.currentNodeId(), currentState, config) ;
                            context.setNextNodeId(nextNodeCommand.gotoNode());
                            //nextNodeId = nextNodeCommand.gotoNode();
                            currentState = nextNodeCommand.update();

                            context.setReturnFromEmbedWithValue(null);
                        });
                    })
                    ;
        }

        private CompletableFuture<Data<Output>> evaluateAction( AsyncNodeActionWithConfig<State> action ) {
            try {
                // Check cancellation before starting action
                config.getAbortController().throwIfCancelled();

                return action.apply(cloneState(currentState), config)
                        .handle((updateState, throwable) -> {

                            // Handle cancellation exception
                            if (throwable instanceof GraphCancellationException) {
                                log.debug("Node {} was cancelled: {}", context.currentNodeId(), throwable.getMessage());

                                // Return cancellation as interruption metadata
                                try {
                                    return Data.done(InterruptionMetadata.builder(
                                            context.currentNodeId(),
                                            cloneState(currentState)
                                    ).build());
                                } catch (Exception e) {
                                    return Data.error(e);
                                }
                            }

                            // Handle other exceptions
                            if (throwable != null) {
                                return Data.error(throwable);
                            }

                            // Check cancellation after action completes
                            try {
                                config.getAbortController().throwIfCancelled();
                            } catch (GraphCancellationException e) {
                                log.debug("Node {} cancelled after completion", context.currentNodeId());
                                try {
                                    return Data.done(InterruptionMetadata.builder(
                                            context.currentNodeId(),
                                            cloneState(currentState)
                                    ).build());
                                } catch (Exception ex) {
                                    return Data.error(ex);
                                }
                            }

                            // Process normal result
                            try {
                                Optional<Data<Output>> embed = getEmbedGenerator(action, updateState);
                                if (embed.isPresent()) {
                                    return embed.get();
                                }

                                currentState = AgentState.updateState(currentState, updateState, stateGraph.getChannels());

                                if (compileConfig.interruptBeforeEdge() && compileConfig.interruptsAfter().contains(context.currentNodeId())) {
                                    context.setNextNodeId(INTERRUPT_AFTER);
                                } else {
                                    var nextNodeCommand = nextNodeId(context.currentNodeId(), currentState, config);
                                    context.setNextNodeId(nextNodeCommand.gotoNode());
                                    currentState = nextNodeCommand.update();
                                }

                                return Data.of(getNodeOutput());

                            } catch (GraphCancellationException e) {
                                log.debug("Cancelled during state update processing");
                                try {
                                    return Data.done(InterruptionMetadata.builder(
                                            context.currentNodeId(),
                                            cloneState(currentState)
                                    ).build());
                                } catch (Exception ex) {
                                    return Data.error(ex);
                                }
                            } catch (Exception e) {
                                return Data.error(e);
                            }
                        });

            } catch (GraphCancellationException e) {
                log.debug("Action cancelled before execution: {}", e.getMessage());
                try {
                    return CompletableFuture.completedFuture(Data.done(InterruptionMetadata.builder(
                            context.currentNodeId(),
                            cloneState(currentState)
                    ).build()));
                } catch (Exception ex) {
                    return CompletableFuture.failedFuture(ex);
                }
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        private CompletableFuture<Output> getNodeOutput() throws Exception {
            Optional<Checkpoint>  cp = addCheckpoint(config, context.currentNodeId(), currentState, context.nextNodeId());
            return completedFuture(( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                    buildStateSnapshot(cp.get()) :
                    buildNodeOutput( context.currentNodeId() ))
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
                // Check cancellation at start of each iteration
                config.getAbortController().throwIfCancelled();

                // GUARD: CHECK MAX ITERATION REACHED
                if( ++iteration > maxIterations ) {
                    // log.warn( "Maximum number of iterations ({}) reached!", maxIterations);
                    return Data.error( new IllegalStateException( format("Maximum number of iterations (%d) reached!", maxIterations)) );
                }

                // GUARD: CHECK IF IT IS END
                if( context.nextNodeId() == null && context.currentNodeId() == null  ) {
                    return releaseThread()
                            .map(Data::<Output>done)
                            .orElseGet( () -> Data.done(currentState) );
                }

                final var returnFromEmbed = context.getReturnFromEmbedAndReset();

                // IS IT A RESUME FROM EMBED ?
                if( returnFromEmbed.isPresent() ) {

                    var interruption = returnFromEmbed.get().value(new TypeRef<InterruptionMetadata<State>>(){} );

                    if( interruption.isPresent() ) {
                        return Data.done( interruption.get() );
                    }

                    return Data.of( getNodeOutput() );
                }

                if( START.equals(context.currentNodeId()) ) {
                    var nextNodeCommand = getEntryPoint(currentState, config) ;
                    //nextNodeId = nextNodeCommand.gotoNode();
                    context.setNextNodeId(nextNodeCommand.gotoNode());
                    currentState = nextNodeCommand.update();

                    var cp = addCheckpoint( config, START, currentState, context.nextNodeId() );

                    var output =  ( cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS) ?
                            buildStateSnapshot(cp.get()) :
                            buildNodeOutput( context.currentNodeId() );

                    context.setCurrentNodeId(context.nextNodeId());
                    //currentNodeId = nextNodeId;

                    return Data.of( output );
                }

                if( END.equals(context.nextNodeId()) ) {
                    context.reset();
                    //nextNodeId = null;
                    //currentNodeId = null;
                    return Data.of( buildNodeOutput( END ) );
                }

                final var resumeFrom = context.getResumeFromAndReset();
                if( resumeFrom.isPresent() ) {

                    if(compileConfig.interruptBeforeEdge() && Objects.equals( context.nextNodeId(), INTERRUPT_AFTER)) {
                        var nextNodeCommand = nextNodeId( resumeFrom.get(), currentState, config);
                        //nextNodeId = nextNodeCommand.gotoNode();
                        context.setNextNodeId( nextNodeCommand.gotoNode() );

                        currentState = nextNodeCommand.update();
                        context.setCurrentNodeId( null );

                    }

                }

                // check on previous node
                if( shouldInterruptAfter( context.currentNodeId(), context.nextNodeId() )) {
                    return Data.done( InterruptionMetadata.builder(context.currentNodeId(), cloneState(currentState)).build() );
                }

                if( shouldInterruptBefore( context.nextNodeId(), context.currentNodeId() ) ) {
                    return Data.done(InterruptionMetadata.builder(context.currentNodeId(), cloneState(currentState)).build() );
                }

                context.setCurrentNodeId( context.nextNodeId() );
                //currentNodeId = nextNodeId;

                var action = nodes.get( context.currentNodeId());

                if (action == null)
                    throw RunnableErrors.missingNode.exception(context.currentNodeId());

                if( action instanceof InterruptableAction<?>) {
                    @SuppressWarnings("unchecked")
                    final var interruption = (InterruptableAction<State>) action;
                    final var interruptMetadata = interruption.interrupt(context.currentNodeId(), cloneState(currentState));
                    if( interruptMetadata.isPresent() ) {
                        return Data.done( interruptMetadata.get() );
                    }
                }

                return evaluateAction( action ).get();
            } catch (GraphCancellationException e) {
                log.debug("Graph iteration cancelled: {}", e.getMessage());
                try {
                    return Data.done(InterruptionMetadata.builder(
                            context.currentNodeId() != null ? context.currentNodeId() : "unknown",
                            cloneState(currentState)
                    ).build());
                } catch (Exception ex) {
                    return Data.error(ex);
                }
            } catch( Exception e ) {
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
