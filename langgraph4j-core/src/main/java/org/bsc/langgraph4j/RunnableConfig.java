package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.SubCompiledGraphNodeAction;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.bsc.langgraph4j.utils.TypeRef;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.action.SubCompiledGraphNodeAction.resumeSubGraphId;

/**
 * A final class representing configuration for a runnable task.
 * This class holds various parameters such as thread ID, checkpoint ID, next node,
 * and stream mode, providing methods to modify these parameters safely
 * without permanently altering the original configuration.
 */
public final class RunnableConfig implements HasMetadata {

    protected static class RemoveResumeSubgraphData<State extends AgentState> implements NodeHook.AfterCall<State> {

        @Override
        public CompletableFuture<Map<String, Object>> applyAfter(String nodeId,
                                                                 State state,
                                                                 RunnableConfig config,
                                                                 Map<String, Object> lastResult)
        {
            config.removeMetadata(
                    resumeSubGraphId( nodeId ),
                    RunnableConfig.SUBGRAPH_RESUME_UPDATE_DATA );

            return completedFuture(lastResult);
        }
    }

    private static class EmptyHolder {
        static final RunnableConfig value = new RunnableConfig();
    }

    /**
     * A builder pattern class for constructing {@link RunnableConfig} objects.
     * This class provides a fluent interface to set various properties of a
     * {@link RunnableConfig} object and then build the final configuration.
     */
    public static class Builder extends HasMetadata.Builder<Builder> {
        private String threadId;
        private String checkPointId;
        private String nextNode;
        private CompiledGraph.StreamMode streamMode = CompiledGraph.StreamMode.VALUES;

        /**
         * Constructs a new instance of the {@link Builder} with default configuration settings.
         * Initializes a new {@link RunnableConfig} object for configuration purposes.
         */
        Builder() {}

        /** Initializes a new instance of the {@code Builder} class with the specified {@link RunnableConfig}.
         *
         * @param config The configuration to be used for initialization.
         */
        Builder( RunnableConfig config ) {
            super( requireNonNull(config, "config cannot be null!").metadata );
            this.threadId       = config.threadId;
            this.checkPointId   = config.checkPointId;
            this.nextNode       = config.nextNode;
            this.streamMode     = config.streamMode;

        }

        /**
         * Sets the ID of the thread.
         *
         * @param threadId the ID of the thread to set
         * @return a reference to this {@code Builder} object so that method calls can be chained together
         */
        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }
        /**
         * Sets the checkpoint ID for the configuration.
         *
         * @param checkPointId - the ID of the checkpoint to be set
         * @return {@literal this} - a reference to the current `Builder` instance
         */
        public Builder checkPointId(String checkPointId) {
            this.checkPointId = checkPointId;
            return this;
        }
        /**
         * Sets the next node in the configuration and returns this builder for method chaining.
         *
         * @param nextNode The next node to be set.
         * @return This builder instance, allowing for method chaining.
         */
        public Builder nextNode(String nextNode) {
            this.nextNode = nextNode;
            return this;
        }
        /**
         * Sets the stream mode of the configuration.
         *
         * @param streamMode The {@link CompiledGraph.StreamMode} to set.
         * @return A reference to this builder for method chaining.
         */
        public Builder streamMode(CompiledGraph.StreamMode streamMode) {
            this.streamMode = streamMode;
            return this;
        }

        /**
         * Adds a custom {@link Executor} for a specific parallel node.
         * <p>
         * This allows you to control the execution of branches within a parallel node.
         * When a parallel node is executed, it will look for an executor in the
         * {@link RunnableConfig} metadata. If found, it will be used to run the
         * parallel branches concurrently.
         *
         * @param nodeId the ID of the parallel node.
         * @param executor  the {@link Executor} to use for the parallel node.
         * @return this {@code Builder} instance for method chaining.
         */
        public Builder addParallelNodeExecutor( String nodeId, Executor executor ) {
            return addMetadata( ParallelNode.formatNodeId(nodeId), requireNonNull(executor, "executor cannot be null!") );
        }

        /**
         * Constructs and returns the configured {@code RunnableConfig} object.
         *
         * @return the configured {@code RunnableConfig} object
         */
        public RunnableConfig build() {
            return new RunnableConfig(this);
        }
    }

    /**
     * Creates a new instance of the {@link Builder} class.
     *
     * @return A new {@code Builder} object.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code Builder} instance with the specified {@link RunnableConfig}.
     *
     * @param config The configuration for the {@code Builder}.
     * @return A new {@code Builder} instance.
     */
    public static Builder builder( RunnableConfig config ) { return new Builder(config); }

    public static RunnableConfig empty() {
        return EmptyHolder.value;
    }

    /**
     * key that contains boolean value to inform that graph is executing in studio environment
     * Warning: it is a RESERVED METADATA KEY don't use it
     */
    public static final String STUDIO_METADATA_KEY = "LG4j_STUDIO_MDK";
    public static final String NODE_ID = "LG4j_NODE_ID";
    public static final String GRAPH_PATH = "LG4j_GRAPH_PATH";
    public static final String GRAPH_ID = "LG4j_GRAPH_ID";
    public static final String SUBGRAPH_RESUME_UPDATE_DATA = "LG4j_SUBGRAPH_UPDATE_DATA";

    private final String threadId;
    private final String checkPointId;
    private final String nextNode;
    private final CompiledGraph.StreamMode streamMode;
    private final Map<String,Object> metadata;

    /**
     * Creates a new instance of {@code RunnableConfig} as a copy of the provided {@code config}.
     *
     * @param builder The configuration builder.
     */
    private RunnableConfig( Builder builder ) {
        this.threadId       = builder.threadId;
        this.checkPointId   = builder.checkPointId;
        this.nextNode       = builder.nextNode;
        this.streamMode     = builder.streamMode;
        this.metadata       = new HashMap<>(builder.metadata());
    }
    private RunnableConfig() {
        this.threadId = null;
        this.checkPointId = null;
        this.nextNode = null;
        this.streamMode = CompiledGraph.StreamMode.VALUES;
        this.metadata = null;
    }

    /**
     * Returns the stream mode of the compiled graph.
     *
     * @return {@code StreamMode} representing the current stream mode.
     */
    public CompiledGraph.StreamMode streamMode() {
        return streamMode;
    }
    /**
     * Returns the thread ID as an {@link Optional}.
     *
     * @return the thread ID wrapped in an {@code Optional}, or an empty {@code Optional} if no thread ID is set.
     */
    public Optional<String> threadId() {
        return ofNullable(threadId);
    }
    /**
     * Returns the current {@code checkPointId} wrapped in an {@link Optional}.
     *
     * @return an {@link Optional} containing the {@code checkPointId}, or {@link Optional#empty()} if it is null.
     */
    public Optional<String> checkPointId() {
        return ofNullable(checkPointId);
    }
    /**
     * Returns an {@code Optional} describing the next node in the sequence,
     * or an empty {@code Optional} if there is no such node.
     *
     * @return an {@code Optional} describing the next node, or an empty {@code Optional}
     */
    public Optional<String> nextNode() {
        return ofNullable(nextNode);
    }

    /**
     * Create a new RunnableConfig with the same attributes as this one
     * but with a different {@link CompiledGraph.StreamMode}.
     * @param streamMode the new stream mode
     * @return a new RunnableConfig with the updated stream mode
     */
    public RunnableConfig withStreamMode(CompiledGraph.StreamMode streamMode) {
        if( this.streamMode == streamMode ) {
            return this;
        }

        return RunnableConfig.builder(this)
                .streamMode(streamMode)
                .build();
    }

    /**
     * Updates the checkpoint ID of the configuration.
     *
     * @param checkPointId The new checkpoint ID to set.
     * @return A new instance of {@code RunnableConfig} with the updated checkpoint ID, 
     *         or the current instance if no change is needed.
     */
    public RunnableConfig withCheckPointId( String checkPointId ) {
        if( Objects.equals(this.checkPointId, checkPointId ) ) {
            return this;
        }
        return RunnableConfig.builder(this)
                .checkPointId(checkPointId)
                .build();

    }

    /**
     * Updates the metadata of the configuration with the provided new metadata.
     *
     * @param newMetadata The map containing the new metadata to be merged.
     * @return A new {@code RunnableConfig} instance with updated metadata, or the current instance if no changes are needed.
     */
    public RunnableConfig updateMetadata( Map<String,Object> newMetadata ) {

        if( newMetadata == null || newMetadata.isEmpty() ) return this;

        return new RunnableConfig.Builder( this)
                .putMetadata( newMetadata )
                .build();
    }

    private void removeMetadata( String ...keys ) {
        if( metadata != null && !metadata.isEmpty() ) {
            for (String key : keys) {
                metadata.remove(key);
            }
        }
    }

    @Override
    public Set<String> metadataKeys() {
        return ofNullable(metadata).map( Map::keySet ).orElseGet(Set::of);
    }

    /**
     * return metadata value for key
     *
     * @param key given metadata key
     * @return metadata value for key if any
     */
    @Override
    public Optional<Object> metadata(String key) {
        if( key == null ) {
            return Optional.empty();
        }
        return ofNullable( metadata ).map( m -> m.get(key) );
    }

    /**
     * Checks if the graph is running within a studio environment.
     *
     * @return {@code true} if the {@link #STUDIO_METADATA_KEY} metadata key is present and its value is {@code true}, {@code false} otherwise.
     */
    public boolean isRunningInStudio() {
        return (Boolean)metadata(STUDIO_METADATA_KEY).orElse(false);
    }

    public String nodeId() {
        return (String)metadata(NODE_ID).orElseThrow();
    }

    public Optional<String> graphId() {
        return metadata(GRAPH_ID).map(Object::toString);
    }

    public GraphPath graphPath() {
        return metadata(GRAPH_PATH, new TypeRef<GraphPath>() {})
                .orElseGet(GraphPath::empty);
    }

    public boolean isResumeSubgraph() {
        return metadata( SubCompiledGraphNodeAction.resumeSubGraphId( nodeId() )).isPresent();
    }




    @Override
    public String toString() {
        return  "RunnableConfig{ threadId=%s, checkPointId=%s, nextNode=%s, streamMode=%s } metadata: %s".formatted(
                threadId,
                checkPointId,
                nextNode,
                streamMode,
                CollectionsUtils.toString(metadata)
                );
    }

}