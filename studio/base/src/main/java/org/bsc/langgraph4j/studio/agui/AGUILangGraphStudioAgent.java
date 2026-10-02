package org.bsc.langgraph4j.studio.agui;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentBase;
import org.bsc.langgraph4j.agui.sdk.AGUINodeOutput;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.dsl.JsonDslGenerator;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.ArgumentMetadata;
import org.bsc.langgraph4j.studio.InitGraphData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class AGUILangGraphStudioAgent extends AGUIAgentBase {

    public static class Builder {
        StateGraph<? extends AgentState> stateGraph;
        String title;
        CompileConfig compileConfig;
        List<ArgumentMetadata> inputArgs = new ArrayList<>();
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

        public Builder addInputStringArgs(List<ArgumentMetadata> args ) {
            inputArgs.addAll( requireNonNull( args, "args cannot be null" ) );
            return this;
        }

        public Builder addInputStringArg(String name, boolean required, Function<Object,Object> converter) {
            inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.STRING, required, converter));
            return this;
        }

        public Builder addInputStringArg(String name, boolean required) {
            return addInputStringArg(name, required, null);
        }

        public Builder addInputStringArg(String name) {
            return addInputStringArg(name, true);
        }

        /**
         * Adds an input image argument to the server configuration.
         *
         * @param name     the name of the argument
         * @param required whether the argument is required
         * @return the Builder instance
         */
        public Builder addInputImageArg(String name, boolean required) {
            inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.IMAGE, required, null));
            return this;
        }

        /**
         * Adds an input image argument to the server configuration with required set to true.
         *
         * @param name the name of the argument
         * @return the Builder instance
         */
        public Builder addInputImageArg(String name) {
            return addInputImageArg(name, true, null);
        }


        public Builder addInputImageArg(String name, boolean required, Function<Object,Object> converter) {
            inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.IMAGE, required, converter));
            return this;
        }

        public AGUILangGraphStudioAgent build() {
            return new AGUILangGraphStudioAgent(this);
        }
    }


    public static Builder builder() {
        return new Builder();
    }

    final StateGraph<? extends AgentState> stateGraph;
    final String title;
    final CompileConfig compileConfig;
    final List<ArgumentMetadata> inputArgs;

    protected AGUILangGraphStudioAgent(Builder builder) {
        super(requireNonNull(builder.id, "id cannot be null"));
        this.stateGraph = requireNonNull(builder.stateGraph, "stateGraph cannot be null");
        this.title = ofNullable(builder.title).orElse("Studio Agent [%s]".formatted(id()));
        this.compileConfig = builder.compileConfig;
        this.inputArgs = builder.inputArgs;
    }

    public final InitGraphData toInitGraphData()  {

            final var representation = stateGraph.reduce( new JsonDslGenerator<>() );

            return new InitGraphData(id(), title, representation, inputArgs);

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
    protected Collection<? extends Event> onNextEvents(RunAgentInput input, NodeOutput<? extends AgentState> output) {

        if( !(output instanceof AGUINodeOutput<?> ) ) {

            return List.of( new CustomEvent("output", output ) );

        }
        return super.onNextEvents(input, output);
    }

    @Override
    protected RunnableConfig runnableConfig(RunAgentInput input) {

        return RunnableConfig.builder()
                .threadId(input.threadId())
                .streamMode(CompiledGraph.StreamMode.SNAPSHOTS)
                .addMetadata(RunnableConfig.STUDIO_METADATA_KEY, true)
                .build();
    }

    @Override
    protected GraphInput graphInput(RunAgentInput input) {

        /*
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
        */

        return GraphInput.noArgs();
    }
}
