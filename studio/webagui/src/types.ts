import type { Edge, Node } from '@xyflow/react';

/** Represents an event triggered when an edit occurs. */
export interface UpdatedState {
	node: string;
	checkpoint: string;
	data: Record<string, any>;
}

export interface NextNodeData {
	node: string;
	subgraphNode: string | undefined;
}

export interface ResultData {
	node: string;
	next: string;
	subgraphNode: string | undefined;
	checkpoint?: string;
	state: Record<string, any>;
	cancelled?: boolean;
}

/** Represents an event triggered when an edit occurs. */
export interface EditEvent {
	existing_src: Record<string, any>;
	existing_value: any;
	name: string;
	namespace: string[];
	new_value: any;
	updated_src: Record<string, any>;
}

export interface ArgumentMetadata {
	name: string;
	type: 'STRING' | 'IMAGE';
	required: boolean;
}

export interface Instance {
	id: string;
	title: string;
	graph: string;
	args: ArgumentMetadata[];
	threads: Array<[string, any[]]>;
}

export type InitData = Instance[];

/** Graph execution lifecycle state. */
export type GraphState = 'start' | 'stop' | 'interrupted' | 'error';

/** Two-dimensional coordinate used by graph nodes. */
export interface Point {
	x: number;
	y: number;
}

/** Width and height pair used by graph layout calculations. */
export interface Size {
	width: number;
	height: number;
}

/** Data carried by a LangGraph4j graph node rendered through React Flow. */
export interface GraphNodeData extends Record<string, unknown> {
	/** Semantic node kind, for example start, end, or subgraph. */
	kind?: string;
	/** Display label. */
	label?: string;
	/** Calculated size for expanded subgraphs. */
	layoutSize?: Size;
	/** True when the node represents the active execution step. */
	active?: boolean;
	/** True when the node represents an interrupted execution step. */
	interrupted?: boolean;
	/** True when a subgraph node is collapsed. */
	collapsed?: boolean;
	/** Toggles a subgraph node between collapsed and expanded states. */
	onToggle?: () => void;
	/** Persists the resized subgraph dimensions. */
	onResizeEnd?: (event: unknown, params: Size) => void;
	/** Additional server-provided node metadata. */
	extra?: Record<string, any>;
}

/** A graph node from the LangGraph4j DSL, compatible with React Flow nodes. */
export type GraphNode = Node<GraphNodeData>;

/** Data carried by a LangGraph4j graph edge rendered through React Flow. */
export interface GraphEdgeData extends Record<string, unknown> {
	/** Conditional edge label. */
	condition?: string;
	/** Subgraph id before boundary edge rewriting. */
	originalSource?: string;
	/** Subgraph id before boundary edge rewriting. */
	originalTarget?: string;
	/** Additional server-provided edge metadata. */
	extra?: Record<string, any>;
}
/** A graph edge from the LangGraph4j DSL, compatible with React Flow edges. */
export type GraphEdge = Edge<GraphEdgeData>;

/** LangGraph4j graph document consumed by the graph viewer. */
export interface GraphDsl {
	type: 'langgraph4j';
	nodes: GraphNode[];
	edges: GraphEdge[];
	subgraphs?: Array<{ id: string }>;
}

/** Persisted graph layout stored for the current browser session. */
export interface StoredGraphLayout {
	/** Node positions keyed by node id. */
	positions: Record<string, Point>;
	/** Resized subgraph dimensions keyed by node id. */
	sizes: Record<string, Size>;
	/** Collapsed subgraph ids. */
	collapsedSubgraphs: string[];
}
