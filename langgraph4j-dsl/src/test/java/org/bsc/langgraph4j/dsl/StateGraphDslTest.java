package org.bsc.langgraph4j.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.junit.jupiter.api.Assertions.*;

public class StateGraphDslTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final GraphDefinition.Reducer<AgentState,String> jsonDslGenerator = new JsonDslGenerator<>();

    CompletableFuture<Map<String, Object>> dummyNodeAction(AgentState state) {
        return CompletableFuture.completedFuture(Map.of());
    }

    CompletableFuture<String> dummyCondition(AgentState state) {
        return CompletableFuture.completedFuture("");
    }

    @Test
    public void testCompiledGraphToJson() throws Exception {

        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("agent", this::dummyNodeAction)
                .addNode("action", this::dummyNodeAction)
                .addEdge(START, "agent")
                .addConditionalEdges(
                        "agent",
                        this::dummyCondition,
                        EdgeMappings.builder()
                                .to("action", "continue")
                                .toEND("end")
                                .build())
                .addEdge("action", END);

        final var jsonString = workflow.compile().reduce( jsonDslGenerator );

        final JsonNode json = OBJECT_MAPPER.readTree(jsonString);

        assertEquals("langgraph4j", json.get("type").asText());
        assertEquals("1.0", json.get("version").asText());
        assertEquals(4, json.get("nodes").size());
        assertEquals(4, json.get("edges").size());

        final JsonNode conditionalEdge = firstEdgeByLabel(json, "continue");
        assertEquals("agent", conditionalEdge.get("source").asText());
        assertEquals("action", conditionalEdge.get("target").asText());
        assertEquals("conditional", conditionalEdge.get("type").asText());
        assertEquals("continue", conditionalEdge.get("data").get("condition").asText());
    }

    @Test
    public void testLangGraphDslSchemaResource() throws Exception {

        final JsonNode schema;
        try (var input = StateGraphDslTest.class.getResourceAsStream("/langgraph4j-dsl.schema.json")) {
            assertNotNull(input);
            schema = OBJECT_MAPPER.readTree(input);
        }

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema").asText());
        assertTrue(arrayContains(schema.get("required"), "type"));
        assertTrue(arrayContains(schema.get("required"), "version"));
        assertTrue(arrayContains(schema.get("required"), "nodes"));
        assertTrue(arrayContains(schema.get("required"), "edges"));
        assertTrue(arrayContains(schema.get("required"), "subgraphs"));

        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("agent", this::dummyNodeAction)
                .addNode("action", this::dummyNodeAction)
                .addEdge(START, "agent")
                .addConditionalEdges(
                        "agent",
                        this::dummyCondition,
                        EdgeMappings.builder()
                                .to("action", "continue")
                                .toEND("end")
                                .build())
                .addEdge("action", END);

        final JsonNode json = OBJECT_MAPPER.readTree(workflow.compile().reduce( jsonDslGenerator ));
        final JsonNode nodeTypeEnum = schema.at("/$defs/node/properties/type/enum");
        final JsonNode nodeKindEnum = schema.at("/$defs/nodeData/properties/kind/enum");
        final JsonNode edgeTypeEnum = schema.at("/$defs/edge/properties/type/enum");
        final JsonNode edgeKindEnum = schema.at("/$defs/edgeData/properties/kind/enum");

        for (JsonNode node : json.get("nodes")) {
            assertTrue(arrayContains(nodeTypeEnum, node.get("type").asText()));
            assertTrue(arrayContains(nodeKindEnum, node.get("data").get("kind").asText()));
        }

        for (JsonNode edge : json.get("edges")) {
            assertTrue(arrayContains(edgeTypeEnum, edge.get("type").asText()));
            assertTrue(arrayContains(edgeKindEnum, edge.get("data").get("kind").asText()));
            assertTrue(edge.has("source"));
            assertTrue(edge.has("target"));
        }
    }

    @Test
    public void testCompiledGraphToJsonWithSubgraph() throws Exception {
        var mockedAction = AsyncNodeAction.node_async((ignored) -> Map.of());

        var subGraph = new StateGraph<>(AgentState::new)
                .addNode("bar1", mockedAction)
                .addNode("bar2", mockedAction)
                .addEdge(START, "bar1")
                .addEdge("bar1", "bar2")
                .addEdge("bar2", END)
                .compile();

        var stateGraph = new StateGraph<>(AgentState::new)
                .addNode("main1", mockedAction)
                .addNode("subgraph1", subGraph)
                .addNode("main2", mockedAction)
                .addEdge(START, "main1")
                .addEdge("main1", "subgraph1")
                .addEdge("subgraph1", "main2")
                .addEdge("main2", END);

        final var jsonString = stateGraph.compile().reduce( jsonDslGenerator );

        final JsonNode json = OBJECT_MAPPER.readTree(jsonString);

        assertEquals(1, json.get("subgraphs").size());
        assertEquals("subgraph1", json.get("subgraphs").get(0).get("id").asText());

        JsonNode groupNode = firstNodeById(json, "subgraph1");
        assertEquals("group", groupNode.get("type").asText());
        assertEquals("subgraph", groupNode.get("data").get("kind").asText());

        JsonNode childNode = firstNodeById(json, "subgraph1-bar1");
        assertEquals("subgraph1", childNode.get("parentId").asText());
        assertEquals("parent", childNode.get("extent").asText());

        JsonNode edgeToSubgraph = firstEdge(json, "main1", "subgraph1");
        assertEquals("default", edgeToSubgraph.get("type").asText());
    }

    private JsonNode firstNodeById(JsonNode json, String id) {
        for (JsonNode node : json.get("nodes")) {
            if (id.equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("node not found: " + id);
    }

    private JsonNode firstEdgeByLabel(JsonNode json, String label) {
        for (JsonNode edge : json.get("edges")) {
            if (edge.has("label") && label.equals(edge.get("label").asText())) {
                return edge;
            }
        }
        throw new AssertionError("edge not found with label: " + label);
    }

    private JsonNode firstEdge(JsonNode json, String source, String target) {
        for (JsonNode edge : json.get("edges")) {
            if (source.equals(edge.get("source").asText()) && target.equals(edge.get("target").asText())) {
                return edge;
            }
        }
        throw new AssertionError("edge not found: " + source + " -> " + target);
    }

    private boolean arrayContains(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }
}
