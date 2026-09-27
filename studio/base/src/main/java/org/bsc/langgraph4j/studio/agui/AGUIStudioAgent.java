package org.bsc.langgraph4j.studio.agui;

import com.agui.community.core.agent.RunAgentInput;
import com.fasterxml.jackson.core.type.TypeReference;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentBase;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.ArgumentMetadata;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.utils.CollectionsUtils.entryOf;

public class AGUIStudioAgent extends AGUIAgentBase {

    public static class Builder {
        StateGraph<? extends AgentState> stateGraph;
        String title;
        CompileConfig compileConfig;
        List<ArgumentMetadata> args;
        String id;
        BaseCheckpointSaver checkpointSaver;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder stateGraph(StateGraph<? extends AgentState> stateGraph) {
            this.stateGraph = stateGraph;
            return this;
        }
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        public Builder compileConfig(CompileConfig compileConfig) {
            this.compileConfig = compileConfig;
            return this;
        }
        public Builder args(List<ArgumentMetadata> args) {
            this.args = args;
            return this;
        }

        public AGUIStudioAgent build() {
            return new AGUIStudioAgent(this);
        }
    }
    final StateGraph<? extends AgentState> stateGraph;
    final String title;
    final CompileConfig compileConfig;
    final List<ArgumentMetadata> args;

    protected AGUIStudioAgent(Builder builder) {
        super(requireNonNull(builder.id, "id cannot be null"));
        this.stateGraph = requireNonNull(builder.stateGraph, "stateGraph cannot be null");
        this.title = ofNullable(builder.title).orElse("Studio Agent [%s]".formatted(id()));
        this.compileConfig = builder.compileConfig;
        this.args = ofNullable(builder.args).orElse(List.of());
    }

    @Override
    protected CompiledGraph<? extends AgentState> newGraph() throws Exception {
        final var config = ofNullable(this.compileConfig)
                                .orElseGet( () -> CompileConfig.builder()
                                        .checkpointSaver(new MemorySaver())
                                        .build());
        return stateGraph.compile(config);
    }

    @Override
    protected RunnableConfig runnableConfig(RunAgentInput input) {

        return RunnableConfig.builder()
                .threadId(input.threadId())
                .streamMode(CompiledGraph.StreamMode.SNAPSHOTS)
                .build();
    }

    @Override
    protected GraphInput graphInput(RunAgentInput input) {

        @SuppressWarnings("unchecked")
        final Map<String, Object> candidateDataMap = (Map<String, Object>) input.state();

        final var dataMap = candidateDataMap.entrySet().stream()
                .map( entry -> {
                    var newValue = args.stream()
                            .filter(arg -> arg.name().equals(entry.getKey()) && arg.converter() != null).findAny()
                            .map(arg -> arg.converter().apply(entry.getValue()));
                    return newValue.map( v -> entryOf(entry.getKey(), v ))
                            .orElse(entry);
                })
                .collect( Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue) );

        return GraphInput.args(dataMap);

    }
}
