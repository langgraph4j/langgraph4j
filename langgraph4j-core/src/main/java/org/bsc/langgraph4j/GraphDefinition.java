package org.bsc.langgraph4j;

import org.bsc.langgraph4j.internal.edge.Edge;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.internal.node.SubStateGraphNode;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Defines the contract for graph structures that manage state and control flow.
 *
 * @param <State> the type of state managed by the graph, extending {@link AgentState}
 * @author LangGraph4j
 */
public sealed interface GraphDefinition<State extends AgentState> permits StateGraph, CompiledGraph {

    String END = "__END__";
    String START = "__START__";

    /**
     * Functional interface for processing nodes and edges to produce an output.
     *
     * @param <State> the type of state managed by the graph
     * @param <Output> the type of output produced by the reducer
     */
    interface Reducer<State extends AgentState, Output> extends BiFunction<Nodes<State>,Edges<State>,Output> {
    }

    /**
     * Container for nodes in a graph.
     *
     * <p>This class manages a collection of nodes and provides utility methods for
     * querying and filtering nodes based on various criteria.
     *
     * @param <State> the type of state managed by the graph
     */
    class Nodes<State extends AgentState> {
        public final Set<Node<State>> elements;

        /**
         * Constructs a Nodes container with the given collection of nodes.
         *
         * @param elements the collection of nodes to initialize with
         */
        public Nodes( Collection<Node<State>> elements ) {
            this.elements = new LinkedHashSet<>(elements);
        }

        /**
         * Constructs an empty Nodes container.
         */
        public Nodes( ) {
            this.elements = new LinkedHashSet<>();
        }

        /**
         * Checks if any node in this container has the specified id.
         *
         * @param id the node id to search for
         * @return {@code true} if a node with the given id exists, {@code false} otherwise
         */
        public boolean anyMatchById(String id ) {
            return elements.stream()
                    .anyMatch( n -> Objects.equals( n.id(), id) );
        }

        /**
         * Returns a list of all SubStateGraphNodes in this container.
         *
         * @return a list containing only SubStateGraphNode instances
         */
        public List<SubStateGraphNode<State>> onlySubStateGraphNodes() {
            return elements.stream()
                    .filter(n -> n instanceof SubStateGraphNode<State>)
                    .map(n -> (SubStateGraphNode<State>) n)
                    .toList();
        }

        /**
         * Returns a list of all nodes in this container except SubStateGraphNodes.
         *
         * @return a list of non-SubStateGraphNode instances
         */
        public List<Node<State>> exceptSubStateGraphNodes() {
            return elements.stream()
                    .filter(n ->  !(n instanceof SubStateGraphNode<State>) )
                    .toList();
        }

        /**
         * Checks whether the node with the specified id is a subgraph node.
         *
         * @param nodeId the node id to check
         * @return {@code true} if a matching node exists and is a {@link SubGraphNode},
         *         {@code false} otherwise
         * @throws NullPointerException if {@code nodeId} is null
         */
        public boolean isSubgraphNode( String nodeId ) {
            requireNonNull( nodeId, "nodeId cannot be null");
            return elements.stream()
                    .filter( n -> Objects.equals(n.id(),nodeId))
                    .anyMatch(n -> n instanceof SubGraphNode<?> );
        }
    }

    /**
     * Container for edges in a graph.
     *
     * <p>This class manages a collection of edges and provides utility methods for
     * querying edges based on their source or target nodes.
     *
     * @param <State> the type of state managed by the graph
     */
    class Edges<State extends AgentState> {

        public final List<Edge<State>> elements;

        /**
         * Constructs an Edges container with the given collection of edges.
         *
         * @param elements the collection of edges to initialize with
         */
        public Edges( Collection<Edge<State>> elements ) {
            this.elements = new LinkedList<>(elements);
        }

        /**
         * Constructs an empty Edges container.
         */
        public Edges( ) {
            this.elements = new LinkedList<>();
        }

        /**
         * Retrieves the first edge that originates from the specified source node id.
         *
         * @param sourceId the source node id to search for
         * @return an Optional containing the first matching edge, or empty if no match is found
         */
        public Optional<Edge<State>> edgeBySourceId(String sourceId ) {
            return elements.stream()
                    .filter( e -> Objects.equals( e.sourceId(), sourceId ))
                    .findFirst();
        }

        /**
         * Retrieves all edges that target the specified target node id.
         *
         * @param targetId the target node id to search for
         * @return a list of all edges targeting the specified node
         */
        public List<Edge<State>> edgesByTargetId(String targetId ) {
            return elements.stream()
                    .filter( e -> e.anyMatchByTargetId(targetId)).toList();
        }

    }
    /**
     * Applies a reducer function to process the nodes and edges of this graph.
     *
     * <p>This method allows external processing of the graph structure by applying a
     * custom reduction function that receives all nodes and edges.
     *
     * @param <Output> the type of output produced by the reducer
     * @param reducer the reducer function to apply
     * @return the output produced by the reducer
     */
    <Output> Output reduce( Reducer<State,Output> reducer );
}
