package org.bsc.langgraph4j.checkpoint.agui;

import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.agui.sdk.AGUIAgentBase;
import org.bsc.langgraph4j.agui.sdk.AGUIHook;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.SQLiteSaverV2;
import org.bsc.langgraph4j.state.AgentState;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

public class AGUIAgentHITL extends AGUIAbstractAgent implements LG4JTestUtil {

    public AGUIAgentHITL() {
        super("agent-hitl");
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
                .addEdge(START, "agent_1")
                .addEdge("agent_1", "agent_2")
                .addEdge("agent_2", END);

        final var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("agent_2")
                .build();

        return graph.compile(compileConfig);
    }

    @Override
    protected GraphInput graphInput(RunAgentInput input) {
        if( input.resume().isEmpty() ) {
            return GraphInput.noArgs();
        }
        return GraphInput.resume();
    }

    @Override
    protected Collection<? extends Event> onCompleteEvents(RunAgentInput input, GraphResult result) {
        return switch( result.type() ) {
            case INTERRUPTION_METADATA -> {
                final var interruptionMetadata = result.asInterruptionMetadata();
                final var interrupt = new Interrupt(
                        "interruption",
                        interruptionMetadata.reason().orElse(""),
                        "",
                        null,
                        null,
                        null,
                        Map.of() // metadata
                        );
                yield List.of(new RunFinishedEvent(
                        input.threadId(),
                        input.runId(),
                        new InterruptOutcome( List.of( interrupt ) ),
                        null,
                        System.currentTimeMillis(),
                        null));
            }
            default -> super.onCompleteEvents(input, result);
        };

    }
}
