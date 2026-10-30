package org.bsc.langgraph4j.serializer;

import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.serializer.plain_text.gson.GsonStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

class TransientStateSerializerTest {

    static Stream<StateSerializer<AgentState>> serializers() {
        return Stream.of(
                new ObjectStreamStateSerializer<>(AgentState::new),
                new JacksonStateSerializer<>(AgentState::new) {},
                new GsonStateSerializer<>(AgentState::new) {});
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void cloningStateDoesNotRestoreAnAbsentTransientAttribute(StateSerializer<AgentState> serializer) throws Exception {
        serializer.declareTransientAttributes("transient");
        var value = new Object();
        assertSame(value, serializer.cloneObject(Map.of("transient", value)).data().get("transient"));

        var restored = serializer.cloneObject(Map.of("keep", "value"));

        assertEquals(Map.of("keep", "value"), restored.data());
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void transientAttributesCanBeReplacedAndExplicitlySetToNull(StateSerializer<AgentState> serializer) throws Exception {
        serializer.declareTransientAttributes("transient");
        var first = new Object();
        var replacement = new Object();
        assertSame(first, serializer.cloneObject(Map.of("transient", first)).data().get("transient"));
        assertSame(replacement, serializer.cloneObject(Map.of("transient", replacement)).data().get("transient"));
        var data = new HashMap<String, Object>();
        data.put("transient", null);

        var restored = serializer.cloneObject(data);

        assertTrue(restored.data().containsKey("transient"));
        assertNull(restored.data().get("transient"));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void removedTransientAttributeStaysRemovedInTheNextNode(StateSerializer<AgentState> serializer) throws Exception {
        serializer.declareTransientAttributes("transient");
        var graph = new StateGraph<>(serializer)
                .addNode("remove", node_async(state ->
                        Map.of("transient", AgentState.MARK_FOR_REMOVAL)))
                .addNode("observe", node_async(state ->
                        Map.of("visible", state.data().containsKey("transient"))))
                .addEdge(START, "remove")
                .addEdge("remove", "observe")
                .addEdge("observe", END)
                .compile();

        final var input = Map.of("transient", new Object(), "keep", "value");

        graph.stream( GraphInput.args(input), RunnableConfig.empty())
                .toCompletableFuture()
                .thenApply( GraphResult::from )
                .thenAccept( result -> {
                    final var stateData = result.asStateData();
                    assertEquals(false, stateData.get("visible"));
                    assertFalse(stateData.containsKey("transient"));
                    assertEquals("value", stateData.get("keep"));
                });
    }
}
