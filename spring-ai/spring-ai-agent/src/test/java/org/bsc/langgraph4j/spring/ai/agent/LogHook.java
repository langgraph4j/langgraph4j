package org.bsc.langgraph4j.spring.ai.agent;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutorEx;
import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;

public record LogHook(Consumer<String> consumer)  implements NodeHook.BeforeCall<AgentExecutorEx.State>, NodeHook.AfterCall<AgentExecutorEx.State> {

    public NodeHook.BeforeCall<AgentExecutorEx.State> asBeforeCall() {
        return this;
    }
    public NodeHook.AfterCall<AgentExecutorEx.State> asAfterCall() {
        return this;
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyBefore(String nodeId, AgentExecutorEx.State state, RunnableConfig config) {
        consumer.accept( """
                    #### %s:%s `STARTED`
                    ```
                    %s
                    ```
                    """.formatted(
                        config.graphId()
                                .map( id -> "%s:%s".formatted(id, config.graphPath()))
                                .orElse( Objects.toString(config.graphPath())),
                        nodeId,
                        state.messages().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining("\n\n"))) );
        return completedFuture( Map.of() );
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyAfter(String nodeId, AgentExecutorEx.State state, RunnableConfig config, Map<String, Object> partialResult) {

        consumer.accept("""
                #### %s:%s `FINISHED`
                ```
                %s
                ```
                """.formatted(
                config.graphId()
                        .map( id -> "%s:%s".formatted(id, config.graphPath()))
                        .orElse( Objects.toString(config.graphPath())),
                    nodeId,
                    CollectionsUtils.toString( partialResult )));

        return completedFuture(partialResult);
    }
}
