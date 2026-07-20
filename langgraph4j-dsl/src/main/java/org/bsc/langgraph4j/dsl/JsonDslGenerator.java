package org.bsc.langgraph4j.dsl;

import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphPath;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.SubGraphNode;
import org.bsc.langgraph4j.internal.edge.EdgeValue;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Generates a JSON graph DSL .
 */
public class JsonDslGenerator<State extends AgentState> implements GraphDefinition.Reducer<State, String> {

    public static String nodeIdFromNodePath(GraphPath nodePath ) {
        return nodePath.stream().collect(Collectors.joining("-"));
    }

    private static final String VERSION = "1.0";

    private final List<Map<String, Object>> nodes = new ArrayList<>();
    private final List<Map<String, Object>> edges = new ArrayList<>();
    private final List<Map<String, Object>> subgraphs = new ArrayList<>();
    private int edgeCounter = 0;

    @Override
    public String apply(GraphDefinition.Nodes<State> graphNodes, GraphDefinition.Edges<State> graphEdges) {
            nodes.clear();
            edges.clear();
            subgraphs.clear();
            edgeCounter = 0;

            appendGraph(graphNodes, graphEdges, GraphPath.empty(), 0);

            return Json.toJson(Map.of(
                    "type", "langgraph4j",
                    "version", VERSION,
                    "nodes", nodes,
                    "edges", edges,
                    "subgraphs", subgraphs
            ));
        };

    private void appendGraph(GraphDefinition.Nodes<State> graphNodes,
                             GraphDefinition.Edges<State> graphEdges,
                             @NonNull GraphPath nodePath,
                             int depth) {
        requireNonNull(nodePath, "nodePath cannot be null");

        appendSpecialNode( nodePath.append(START), START, "start", "input", depth, 0);
        appendSpecialNode( nodePath.append(END), END, "end", "output", depth, 1);

        int index = 2;
        for (var node : graphNodes.elements) {
            var currentNodeId = nodePath.append(node.id());

            if (node instanceof SubGraphNode<?> subGraphNode) {
                appendNode(currentNodeId,
                        node.id(),
                        "subgraph",
                        "group",
                        depth,
                        index++);
                appendSubgraph(currentNodeId);

                @SuppressWarnings("unchecked")
                var subGraph = (StateGraph<State>) subGraphNode.subGraph();

                subGraph.reduce( ( nodes, edges ) -> {
                    appendGraph( nodes, edges,
                                currentNodeId,
                            depth + 1);
                    return null;
                });
            }
            else {
                appendNode(currentNodeId, node.id(), nodeKind(node), "default", depth, index++);
            }
        }

        appendEdges(graphEdges, nodePath);
    }

    private void appendSpecialNode(GraphPath nodePath,
                                   String label,
                                   String kind,
                                   String type,
                                   int depth,
                                   int index) {
        appendNode(nodePath, label, kind, type, depth, index);
    }

    private void appendNode(GraphPath nodePath,
                            String label,
                            String kind,
                            String type,
                            int depth,
                            int index) {
        var node = new LinkedHashMap<String, Object>();
        node.put("id", nodeIdFromNodePath(nodePath));
        node.put("type", type);
        node.put("data", Map.of(
                "label", label,
                "kind", kind
        ));

        final var parent = nodePath.parent();
        if (!parent.isEmpty()) {
            node.put("parentId", nodeIdFromNodePath(parent));
            node.put("extent", "parent");
        }

        nodes.add(node);
    }

    private void appendSubgraph(GraphPath nodePath) {
        var subgraph = new LinkedHashMap<String, Object>();
        subgraph.put("id", nodeIdFromNodePath(nodePath));
        final var parent = nodePath.parent();
        if (!parent.isEmpty()) {
            subgraph.put("parentId", nodeIdFromNodePath(parent));
        }
        subgraphs.add(subgraph);
    }

    private String nodeKind(Node<State> node) {
        return node.isParallel() ? "parallel" : "node";
    }

    private void appendEdges(StateGraph.Edges<State> graphEdges, GraphPath nodePath) {
        for (var edge : graphEdges.elements) {
            if (edge.isParallel()) {
                for (var target : edge.targets()) {
                    appendEdge(nodePath, edge.sourceId(), target, "parallel", null);
                }
            }
            else {
                var target = edge.target();
                if (target.id() != null) {
                    appendEdge(nodePath, edge.sourceId(), target, "default", null);
                }
                else if (target.value() != null) {
                    for (var mapping : target.value().mappings().entrySet()) {
                        appendEdge(nodePath, edge.sourceId(), new EdgeValue<>(mapping.getValue()), "conditional", mapping.getKey());
                    }
                }
            }
        }
    }

    private void appendEdge(GraphPath nodePath,
                            String source,
                            EdgeValue<State> target,
                            String kind,
                            String condition) {
        var edge = new LinkedHashMap<String, Object>();
        edge.put("id", "e%d".formatted(++edgeCounter));
        edge.put("source", nodeIdFromNodePath(nodePath.append(source)));
        edge.put("target", nodeIdFromNodePath(nodePath.append(target.id())));
        edge.put("type", kind);

        var data = new LinkedHashMap<String, Object>();
        data.put("kind", kind);
        if (condition != null) {
            edge.put("label", condition);
            data.put("condition", condition);
        }
        edge.put("data", data);

        edges.add(edge);
    }

    private static final class Json {
        private Json() {
        }

        static String toJson(Object value) {
            var out = new StringBuilder();
            append(out, value);
            return out.toString();
        }

        private static void append(StringBuilder out, Object value) {
            if (value == null) {
                out.append("null");
            }
            else if (value instanceof String string) {
                appendString(out, string);
            }
            else if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            }
            else if (value instanceof Map<?, ?> map) {
                appendMap(out, map);
            }
            else if (value instanceof Iterable<?> iterable) {
                appendIterable(out, iterable);
            }
            else {
                appendString(out, value.toString());
            }
        }

        private static void appendMap(StringBuilder out, Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                appendString(out, String.valueOf(entry.getKey()));
                out.append(':');
                append(out, entry.getValue());
                first = false;
            }
            out.append('}');
        }

        private static void appendIterable(StringBuilder out, Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (var item : iterable) {
                if (!first) {
                    out.append(',');
                }
                append(out, item);
                first = false;
            }
            out.append(']');
        }

        private static void appendString(StringBuilder out, String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append("\\u%04x".formatted((int) c));
                        }
                        else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
