package org.bsc.langgraph4j.hook;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphPath;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeResult;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

public abstract class WrapCallHookSubgraphAware<S extends AgentState> implements NodeHook.WrapCall<S> {
        protected record Step(String nodeId, String lastPathElement ) {}

        private final Deque<Step> subgraphStack = new ArrayDeque<>();

        private String lastElement(GraphPath path ) {
            return path.lastElement().orElse("__root__");
        }

        protected Optional<Step> isSubgraphEnded(RunnableConfig config) {
            var isSubgraphEnded = !subgraphStack.isEmpty() &&
                    Objects.equals( subgraphStack.peek().lastPathElement(), lastElement(config.graphPath()));
            if( isSubgraphEnded ) {
                return Optional.of(subgraphStack.pop());
            }
            return Optional.empty();
        }

    protected Optional<Step> isSubgraphRequested(String nodeId, RunnableConfig config, NodeResult nodeResult ) {

            if( nodeResult.hasGenerator() ) {
                var item = new Step(nodeId, lastElement(config.graphPath()));
                subgraphStack.push( item );
                return Optional.of(item);
            }
            return Optional.empty();
        }

}
