package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.slf4j.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

import org.junit.jupiter.api.Test;

public class Hooks {

    public record LoggingNodeHook<State extends AgentState>(Logger log)
            implements NodeHook.WrapCall<State> {

        @Override
        public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

            log.info("node action fo node '{}' start with state:{}", config.nodeId(), state);
            return action.apply(state, config)
                    .whenComplete( ( result, exception ) -> {
                        log.info("node action fo node '{}' end with request update:{}", config.nodeId(), CollectionsUtils.toString(result));
                    });
        }
    }

    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }

    }

    AsyncNodeActionWithConfig<State> simpleAction() {
        return node_async( ( state, config ) -> Map.of() );
    }

    @Test
    public void applyLoggingNodeHook() throws Exception {
        var log = LoggerFactory.getLogger("LG4J");

        var workflow = new StateGraph<>(MessagesState.SCHEMA, State::new)
                .addWrapCallNodeHook( new LoggingNodeHook<>(log) )
                .addNode("node_1", simpleAction() )
                .addNode("node_2", simpleAction() )
                .addNode("node_3", simpleAction() )
                .addNode("node_4", simpleAction() )
                .addEdge(START, "node_1")
                .addEdge("node_1", "node_2")
                .addEdge("node_2", "node_3")
                .addEdge("node_3", "node_4")
                .addEdge("node_4", END)
                .compile();

        var result = workflow.invoke( GraphInput.noArgs(), RunnableConfig.empty());
    }
}
