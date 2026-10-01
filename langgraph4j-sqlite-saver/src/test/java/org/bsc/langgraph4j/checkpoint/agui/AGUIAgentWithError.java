package org.bsc.langgraph4j.checkpoint.agui;

import com.agui.community.core.agent.RunAgentInput;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agui.sdk.AGUIHook;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.CheckpointListSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonCheckpointListSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;

public class AGUIAgentWithError extends AGUIAbstractAgent implements LG4JTestUtil {

    public AGUIAgentWithError() {
        super("agent-error");
    }

    private AsyncNodeActionWithConfig<State> actionWithError() {
        return ( state, config) -> {
            return failedFuture(new GraphRunnerException( config, "Simulated error in node: %s".formatted(config.nodeId())));
        };
    }

    @Override
    protected CompiledGraph<? extends AgentState> newGraph() throws Exception {
        final var stateSerializer = StateSerializerEnum.JSON.stateSerializer;

        final var saver = buildSaver(stateSerializer);

        final var agent1 = CustomNodeAction.of("agent_1");
        final var agent2 = CustomNodeAction.of("agent_2");

        final var graph = new StateGraph<>(State.SCHEMA, stateSerializer)
                .addWrapCallNodeHook( AGUIHook.stepEvents())
                .addNode("agent_1", agent1)
                .addNode("agent_2", agent2)
                .addNode( "agent_error", actionWithError())
                .addEdge(START, "agent_1")
                .addConditionalEdges("agent_1", command_async((state, config ) -> {

                    final var nextNode = state.<String>value("route")
                                                .orElse("agent_error");
                        return new Command(nextNode);
                }), EdgeMappings.builder()
                        .to("agent_error")
                        .to("agent_2")
                        .build())
                .addEdge("agent_error", "agent_2")
                .addEdge("agent_2", END);

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        return graph.compile(compileConfig);

    }

    @Override
    protected GraphInput graphInput(RunAgentInput input)  {
        if( input.resume().isEmpty() ) {
            return GraphInput.noArgs();
        }
        final var resumeList = input.resume();
        if( resumeList.size() > 1 ) {
            throw new IllegalStateException("Unexpected resume size > 0: %d".formatted(resumeList.size()));
        }
        final var resume = resumeList.get(0);

        if( resume.interruptId().equals("checkpoints")) {

            final var checkpointListSerializer = CheckpointListSerializer.of(StateSerializerEnum.JSON.stateSerializer);

            final List<Checkpoint> checkpoints;
            try {
                checkpoints =checkpointListSerializer.readDataFromString(Objects.toString(resume.payload()));
            }
            catch( Exception ex ) {
                throw new IllegalArgumentException("Unexpected error reading checkpoints from resume payload: %s".formatted(resume.payload()), ex);
            }
            if( checkpoints.isEmpty() ) {
                throw new IllegalStateException("Unexpected empty checkpoint list: %s".formatted(resume.payload()));
            }
            if( checkpoints.size() > 1 ) {
                throw new IllegalStateException("Unexpected checkpoint list size > 1: %d".formatted(checkpoints.size()));
            }
            return GraphInput.resume(checkpoints.get(0));
        }

        return GraphInput.resume();
    }
}
