package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

@Deprecated(forRemoval = true)
public record LangGraphFlow(    List<LangGraphStudioServer.ArgumentMetadata> inputArgs,
                                String title,
                                CompileConfig compileConfig,
                                StateGraph<? extends AgentState> stateGraph ) {

    public LangGraphFlow {
        Objects.requireNonNull( stateGraph, "stateGraph is null" );
    }

    public LangGraphStudioServer.Instance toInstance() {
        return LangGraphStudioServer.Instance.builder()
                .title(title)
                .graph(stateGraph)
                .compileConfig(compileConfig)
                .addInputStringArgs(inputArgs)
                .build();
    }

    public static LangGraphFlow.Builder builder() {
        return new LangGraphFlow.Builder();
    }

    public static class Builder {
        private final List<LangGraphStudioServer.ArgumentMetadata> inputArgs = new ArrayList<>();
        private String title = null;
        private CompileConfig compileConfig;
        private StateGraph<? extends AgentState> stateGraph;


        /**
         * Sets the title for the server.
         *
         * @param title the title to be set
         * @return the Builder instance
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder addInputStringArg(String name, boolean required, Function<Object,Object> converter) {
            inputArgs.add(new LangGraphStudioServer.ArgumentMetadata(name, LangGraphStudioServer.ArgumentMetadata.ArgumentType.STRING, required, converter));
            return this;
        }

        /**
         * Adds an input string argument to the server configuration.
         *
         * @param name     the name of the argument
         * @param required whether the argument is required
         * @return the Builder instance
         */
        public Builder addInputStringArg(String name, boolean required) {
            return addInputStringArg(name, required, null);
        }

        /**
         * Adds an input string argument to the server configuration with required set to true.
         *
         * @param name the name of the argument
         * @return the Builder instance
         */
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
            inputArgs.add(new LangGraphStudioServer.ArgumentMetadata(name, LangGraphStudioServer.ArgumentMetadata.ArgumentType.IMAGE, required));
            return this;
        }

        /**
         * Adds an input image argument to the server configuration with required set to true.
         *
         * @param name the name of the argument
         * @return the Builder instance
         */
        public Builder addInputImageArg(String name) {
            return addInputImageArg(name, true);
        }

        /**
         * Sets the checkpoint saver for the server.
         *
         * @param compileConfig the graph compiler config to be used
         * @return the Builder instance
         */
        public Builder compileConfig(CompileConfig compileConfig ) {
            this.compileConfig = compileConfig;
            return this;
        }

        /**
         * Sets the state graph for the server.
         *
         * @param stateGraph the state graph to be used
         * @param <State>    the type of the state
         * @return the Builder instance
         */
        public <State extends AgentState> Builder stateGraph(StateGraph<State> stateGraph) {
            this.stateGraph = stateGraph;
            return this;
        }

        public LangGraphFlow build() {

            return new LangGraphFlow(inputArgs,
                    ofNullable(title).orElse("LangGraph Studio"),
                    ofNullable( compileConfig )
                            .orElseGet( () -> CompileConfig.builder()
                                    .checkpointSaver( new MemorySaver() )
                                    .build()),
                    stateGraph);
        }
    }
}
