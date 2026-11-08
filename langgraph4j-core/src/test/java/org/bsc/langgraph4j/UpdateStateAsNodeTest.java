package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

class UpdateStateAsNodeTest {

    private CompiledGraph<AgentState> graph(MemorySaver saver, Map<String, Channel<?>> channels,
                                           AsyncCommandAction<AgentState> edge) throws GraphStateException {
        return new StateGraph<>(channels, AgentState::new)
                .addNode("before", node_async(state -> Map.of()))
                .addNode("after", node_async(state -> Map.of()))
                .addEdge(START, "before")
                .addConditionalEdges("before", edge, Map.of("next", "after"))
                .addEdge("after", END)
                .compile(CompileConfig.builder().checkpointSaver(saver).releaseThread(false).build());
    }

    private Checkpoint checkpoint(Map<String, Object> state) {
        return Checkpoint.builder()
                .nodeId(START)
                .nextNodeId("before")
                .state(state)
                .build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appliesConditionalEdgeUpdatesOnce(boolean selectCheckpoint) throws Exception {
        var saver = new MemorySaver();
        var graph = graph(saver, Map.of(
                        "count", Channels.base(Integer::sum, () -> 0),
                        "messages", Channels.<String>appenderWithDuplicate(ArrayList::new)),
                command_async((state, config) -> new Command("next",
                        Map.of("count", 4, "messages", List.of("edge")))));
        var config = RunnableConfig.builder().threadId("test").build();
        var original = checkpoint(Map.of("count", 1, "messages", List.of("original")));
        var selectedConfig = saver.put(config, original);

        var updatedConfig = graph.updateState(selectCheckpoint ? selectedConfig : config,
                Map.of("count", 2, "messages", List.of("manual")), "before");
        var updated = saver.get(updatedConfig).orElseThrow();

        assertEquals(7, updated.getState().get("count"));
        assertEquals(List.of("original", "manual", "edge"), updated.getState().get("messages"));
        assertEquals(START, updated.getNodeId());
        assertEquals("after", updated.getNextNodeId());
        assertEquals("after", updatedConfig.nextNode().orElseThrow());
        assertNotEquals(original.getId(), updated.getId());
        assertEquals(updated.getId(), updatedConfig.checkPointId().orElseThrow());
        assertEquals(Map.of("count", 1, "messages", List.of("original")), original.getState());
        assertEquals(selectCheckpoint ? 1 : 2, saver.list(config).size());

        var resumed = graph.invoke(GraphInput.resume(), updatedConfig).orElseThrow();
        assertEquals(updated.getState(), resumed.data());
    }

    @Test
    void doesNotReduceUnchangedChannelsAgain() throws Exception {
        var saver = new MemorySaver();
        var graph = graph(saver, Map.of("count", Channels.base(Integer::sum, () -> 0)),
                command_async((state, config) -> new Command("next", Map.of("status", "ready"))));
        var config = RunnableConfig.builder().threadId("test").build();
        saver.put(config, checkpoint(Map.of("count", 1)));

        var updatedConfig = graph.updateState(config, Map.of("count", 2), "before");

        assertEquals(Map.of("count", 3, "status", "ready"), saver.get(updatedConfig).orElseThrow().getState());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesConditionalEdgeRemovals(boolean configureChannel) throws Exception {
        var saver = new MemorySaver();
        Map<String, Channel<?>> channels = configureChannel ?
                Map.of("obsolete", Channels.base(() -> "default")) : Map.of();
        var graph = graph(saver, channels,
                command_async((state, config) -> new Command("next", Map.of("obsolete", AgentState.MARK_FOR_REMOVAL))));
        var config = RunnableConfig.builder().threadId("test").build();
        saver.put(config, checkpoint(Map.of("obsolete", "old", "untouched", "keep")));

        var updatedConfig = graph.updateState(config, Map.of(), "before");

        assertEquals(Map.of("untouched", "keep"), saver.get(updatedConfig).orElseThrow().getState());
    }

    @Test
    void preservesConditionalEdgeReset() throws Exception {
        var saver = new MemorySaver();
        var graph = graph(saver, Map.of("messages", Channels.<String>appenderWithDuplicate(ArrayList::new)),
                command_async((state, config) -> new Command("next", Map.of("messages", AgentState.MARK_FOR_RESET))));
        var config = RunnableConfig.builder().threadId("test").build();
        saver.put(config, checkpoint(Map.of("messages", List.of("old"))));

        var updatedConfig = graph.updateState(config, Map.of("messages", List.of("manual")), "before");

        assertEquals(List.of(), saver.get(updatedConfig).orElseThrow().getState().get("messages"));
    }
}
