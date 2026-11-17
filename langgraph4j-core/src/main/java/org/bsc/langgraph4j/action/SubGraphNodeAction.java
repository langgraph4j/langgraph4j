package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;

import static java.util.Objects.requireNonNull;

public interface SubGraphNodeAction<State extends AgentState> extends AsyncNodeActionWithConfig<State> {

    static String subGraphId( String nodeId ) {
        return  "subgraph_%s".formatted( requireNonNull( nodeId, "nodeId cannot be null!"));

    }

    static String resumeSubGraphId( String nodeId ) {
        return  "resume_%s".formatted( subGraphId(nodeId));

    }

    String nodeId();

    CompiledGraph<State> subGraph();

    default String subGraphId() {
        return subGraphId(nodeId());
    }

    default String resumeSubGraphId() {
        return  resumeSubGraphId(nodeId());
    }

}
