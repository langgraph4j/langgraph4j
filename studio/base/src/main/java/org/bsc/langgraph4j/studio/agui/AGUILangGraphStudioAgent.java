package org.bsc.langgraph4j.studio.agui;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.CustomEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.ResumeStatus;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentBase;
import org.bsc.langgraph4j.agui.sdk.AGUINodeOutput;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.dsl.JsonDslGenerator;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.ArgumentMetadata;
import org.bsc.langgraph4j.studio.InitGraphData;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class AGUILangGraphStudioAgent extends AGUIAgentBase {

    public static class Builder {
        StateGraph<? extends AgentState> stateGraph;
        String title;
        CompileConfig compileConfig;
        List<ArgumentMetadata> inputArgs = new ArrayList<>();
        String id;

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

        @SuppressWarnings("unchecked")
        public <I,O> Builder addInputStringArg(String name, boolean required, Function<I,O> converter) {
            inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.STRING, required, (Function<Object,Object>)converter));
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


        @SuppressWarnings("unchecked")
        public <I,O> Builder addInputImageArg(String name, boolean required, Function<I,O> converter) {
            inputArgs.add(new ArgumentMetadata(name, ArgumentMetadata.ArgumentType.IMAGE, required, (Function<Object,Object>)converter));
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
        this.inputArgs = builder.inputArgs;
        this.compileConfig = ofNullable(builder.compileConfig)
                                .orElseGet( () -> CompileConfig.builder()
                                .checkpointSaver( new MemorySaver())
                                .build());
    }

    public final InitGraphData toInitGraphData()  {

        final var representation = stateGraph.reduce( new JsonDslGenerator<>() );

        return new InitGraphData(id(), title, representation, inputArgs);

    }

    @Override
    protected CompiledGraph<? extends AgentState> newGraph() throws Exception {
        return stateGraph.compile(compileConfig);
    }

    @Override
    protected Collection<? extends Event> onNextEvents(RunAgentInput input, NodeOutput<? extends AgentState> output) {

        if( !(output instanceof AGUINodeOutput<?> ) ) {

            return List.of( new CustomEvent("output", output ) );

        }
        return super.onNextEvents(input, output);
    }

    @Override
    protected Collection<? extends Event> onCompleteEvents(RunAgentInput input, GraphResult result) {
        return switch( result.type() ) {
            case INTERRUPTION_METADATA -> {
                final var interruptionMetadata = result.asInterruptionMetadata();

                final var interrupt = new Interrupt(
                        interruptionMetadata.nodeId(),
                        interruptionMetadata.reason().orElse("Agent interrupted"),
                        interruptionMetadata.metadata("message")
                                .map(Object::toString)
                                .orElse("Agent interrupted"));
                yield List.of(
                        new RunFinishedEvent(
                                input.threadId(),
                                input.runId(),
                                new InterruptOutcome(List.of(interrupt)),
                                null,
                                System.currentTimeMillis(),
                                null)                    );
            }
            default -> super.onCompleteEvents(input, result);
        };
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

        if( !input.resume().isEmpty()) {
            if( input.resume().size() > 1 ) {
                throw new IllegalArgumentException("Resume input must contain only one message");
            }

            final var interrupt = input.resume().get(0);

            if( interrupt.status() ==  ResumeStatus.CANCELLED) {
                throw new UnsupportedOperationException("Resume cancelled not supported yet");
            }

            if( !(interrupt.payload() instanceof Map<?,?>)) {
                throw new IllegalArgumentException("Resume input payload must be a Map");
            }

            @SuppressWarnings("unchecked")
            final var payloadMap = (Map<String,String>) interrupt.payload();

            final String checkpointId = ofNullable(payloadMap.get("checkpoint"))
                                            .orElseThrow(() -> new IllegalArgumentException("'checkpoint' id not provided in resume payload"));
            final var data = ofNullable(payloadMap.get("data"))
                                            .orElseThrow(() -> new IllegalArgumentException("'data' not provided in resume payload"));

            final AgentState state;
            try {
                state = stateGraph.stateSerializer().readDataFromString(data);
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Failed to deserialize state data from resume payload", e);
            }

            final var config = RunnableConfig.builder()
                    .threadId(input.threadId())
                    .checkPointId(checkpointId)
                    .build();

            final var checkpointSaver = compileConfig.checkpointSaver()
                    .orElseThrow(() -> new IllegalStateException("Checkpoint saver not configured in compileConfig"));

            final var checkpoint = checkpointSaver.get(config)
                    .map( c -> c.updateState( state.data(), stateGraph.channels()) )
                    .orElseThrow(() -> new IllegalArgumentException("Checkpoint with id '%s' not found".formatted(checkpointId)));


            return GraphInput.resume(checkpoint);

        }
        else {

            final var inputMap = input.messages().stream()
                    .map(message ->
                            inputArgs.stream()
                                    .filter(arg -> arg.name().equals(message.id()) && arg.converter() != null).findAny()
                                    .map(arg -> Map.entry(message.id(), arg.converter().apply(message)))
                                    .orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            return GraphInput.args(inputMap);
        }
    }
}
