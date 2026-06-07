package org.bsc.langgraph4j.exception;

import org.bsc.langgraph4j.GraphRunException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.ExceptionUtils;

import java.util.Map;
import java.util.Optional;

public class SubGraphInterruptionException extends GraphRunException {

    public static Optional<SubGraphInterruptionException> of(Throwable throwable) {
        return ExceptionUtils.findCauseByType( throwable, SubGraphInterruptionException.class );
    }

    final String parentNodeId;
    final String nodeId;
    final Map<String, Object> state;
    final InterruptionMetadata<? extends AgentState> interruptionMetadata;

    public SubGraphInterruptionException(RunnableConfig config, String parentNodeId, String nodeId, Map<String, Object> state) {
        super( config, "interruption in subgraph: %s on node: %s".formatted( parentNodeId, nodeId)  );
        this.parentNodeId = parentNodeId;
        this.nodeId = nodeId;
        this.state = state;
        interruptionMetadata = null;
    }

    public SubGraphInterruptionException(RunnableConfig config, InterruptionMetadata<? extends AgentState> interruptionMetadata) {
        super(config, "interruption in subgraph: %s on node: %s".formatted( "NONE", interruptionMetadata.nodeId()));
        this.parentNodeId = "NONE";
        this.nodeId = interruptionMetadata.nodeId();
        this.state = interruptionMetadata.state().data();
        this.interruptionMetadata = interruptionMetadata;
    }

    public InterruptionMetadata<? extends AgentState> interruptionMetadata() {
        return interruptionMetadata;
    }

    public String parentNodeId() {
        return parentNodeId;
    }

    public String nodeId() {
        return nodeId;
    }

    public Map<String, Object> state() {
        return state;
    }

}
