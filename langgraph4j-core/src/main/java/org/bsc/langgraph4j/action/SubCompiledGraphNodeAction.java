package org.bsc.langgraph4j.action;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.subgraph.SubGraphOutputFactory;
import org.bsc.langgraph4j.utils.TypeRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Represents an action to perform a subgraph on a given state with a specific configuration.
 *
 * <p>This record encapsulates the behavior required to execute a compiled graph using a provided state.
 * It implements the {@link AsyncNodeActionWithConfig} interface, ensuring that the execution is handled asynchronously with the ability to configure settings.</p>
 *
 * @param <State> The type of state the subgraph operates on, which must extend {@link AgentState}.
 * @param subGraph sub graph instance
 * @see CompiledGraph
 * @see AsyncNodeActionWithConfig
 */
public record SubCompiledGraphNodeAction<State extends AgentState>(
        String nodeId,
        CompileConfig parentCompileConfig,
        CompiledGraph<State> subGraph
) implements AsyncNodeActionWithConfig<State> {

    public static String subGraphId( String nodeId ) {
        return  "subgraph_%s".formatted( requireNonNull( nodeId, "nodeId cannot be null!"));

    }

    public static String resumeSubGraphId( String nodeId ) {
        return  "resume_%s".formatted( subGraphId(nodeId));

    }
    public String subGraphId() {
        return subGraphId(nodeId);
    }

    public String resumeSubGraphId() {
        return  resumeSubGraphId(nodeId);
    }

    /**
     * Executes the given graph with the provided state and configuration.
     *
     * @param state  The current state of the system, containing input data and intermediate results.
     * @param config The configuration for the graph execution.
     * @return A {@link CompletableFuture} that will complete with a result of type {@code Map<String, Object>}.
     * If an exception occurs during execution, the future will be completed exceptionally.
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public CompletableFuture<NodeResult> applyWithResult(State state, RunnableConfig config) {
        final boolean resumeSubgraph = config.metadata( resumeSubGraphId(), new TypeRef<Boolean>() {} )
                .orElse( false );

        final var subGraphRunnableConfigBuilder = RunnableConfig.builder(config)
                .putMetadata(RunnableConfig.GRAPH_PATH, config.graphPath().append(nodeId));
        subGraph.compileConfig.graphId()
                .ifPresent( id ->
                        subGraphRunnableConfigBuilder.putMetadata(RunnableConfig.GRAPH_ID, id));
        var subGraphRunnableConfig = subGraphRunnableConfigBuilder.build();

        final var parentSaver   = parentCompileConfig.checkpointSaver();
        final var subGraphSaver = subGraph.compileConfig.checkpointSaver();

        if( subGraphSaver.isPresent() ) {
            if( parentSaver.isEmpty() ) {
                return failedFuture(new IllegalStateException("Missing CheckpointSaver in parent graph!"));
            }

            // Check saver are the same instance
            if( parentSaver.get() == subGraphSaver.get() ) {
                subGraphRunnableConfig = RunnableConfig.builder(subGraphRunnableConfig)
                        .threadId( config.threadId()
                                .map( threadId -> "%s_%s".formatted( threadId, subGraphId()))
                                .orElseGet(this::subGraphId))
                        .streamMode( config.streamMode() )
                        .build();
            }
        }

        try {

            final GraphInput input = ( resumeSubgraph ) ?
                    GraphInput.resume( state.data() ) :
                    GraphInput.args(state.data());

            var generator = subGraph.stream(input, subGraphRunnableConfig)
                    .map( n -> SubGraphOutputFactory.createFromNodeOutput( n, nodeId) );

            return completedFuture( NodeResult.withGenerator( generator, Map.of() ) );

        } catch (Exception e) {
            return failedFuture(e);
        }

    }
}