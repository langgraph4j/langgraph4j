package org.bsc.langgraph4j.hook;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

public abstract class WrapCallHookSubgraphAware<S extends AgentState> implements NodeHook.WrapCall<S> {
        protected record Step(String nodeId, String lastPathElement ) {}

        private final Deque<String> subgraphStack = new ArrayDeque<>();

        protected Optional<String> isSubgraphEnded(RunnableConfig config) {
            if( !subgraphStack.isEmpty() ) { // subgraph ended
                return Optional.of( subgraphStack.pop() ) ;
            }
            return Optional.empty();
        }

        protected Optional<String> isSubgraphRequested(String nodeId, RunnableConfig config, Map<String,Object> result ) {

            var isSubgraphRequested =  result.values().stream()
                                        .anyMatch( v -> v instanceof AsyncGenerator<?>);
            if( isSubgraphRequested ) {
                subgraphStack.push( nodeId );
                return Optional.of( nodeId );
            }
            return Optional.empty();
        }

}
