/**
 * @module types
 */


/**
 * Represents an event triggered when an edit occurs.
 *
 * @typedef {Object} UpdatedState
 * @property {string} node - node id
 * @property {string} checkpoint - checkpoint id.
 * @property {Record<string, any>} data - the modified state.
 * 
 */

/**
 * @typedef NextNodeData
 * @property {string} node - next node id 
 * @property {string|undefined} subgraphNode - subgraph id 
 */

/**
 * @typedef {Object} ResultData
 * @property {string} node - node id 
 * @property {string} next - next node id 
 * @property {string|undefined} subgraphNode - subgraph id 
 * @property {string} [checkpoint] - checkpoint id.
 * @property {Record<string,any>} state - state
 * @property {boolean} [cancelled] - true if the execution was cancelled.
 */

/**
 * Represents an event triggered when an edit occurs.
 *
 * @typedef {Object} EditEvent
 * @property {Record<string, any>} existing_src - The original source object before the edit.
 * @property {any} existing_value - The original value before the edit.
 * @property {string} name - The name of the field that was edited.
 * @property {string[]} namespace - The namespace path indicating where the edit occurred.
 * @property {any} new_value - The new value after the edit.
 * @property {Record<string, any>} updated_src - The updated source object after the edit.
 */

/**
 * @typedef {Object} ArgumentMetadata
 * @property {string} name
 * @property {'STRING' | 'IMAGE'} type
 * @property {boolean} required
 */

/**
 * @typedef {Object} Instance
 * @property {string} id
 * @property {string} title
 * @property {string} graph
 * @property {Array<ArgumentMetadata>} args
 * @property {Array<[ string, Array<any> ]>} threads
 */

/**
 * @typedef {Array<Instance>} InitData
 */

/**
 * Graph execution lifecycle state.
 *
 * @typedef {'start'|'stop'|'interrupted'|'error'} GraphState
 */

/**
 * Two-dimensional coordinate used by graph nodes.
 *
 * @typedef {Object} Point
 * @property {number} x
 * @property {number} y
 */

/**
 * Width and height pair used by graph layout calculations.
 *
 * @typedef {Object} Size
 * @property {number} width
 * @property {number} height
 */

/**
 * Data carried by a LangGraph4j graph node rendered through React Flow.
 *
 * @typedef {Object} GraphNodeData
 * @property {string} [kind] - Semantic node kind, for example start, end, or subgraph.
 * @property {string} [label] - Display label.
 * @property {Size} [layoutSize] - Calculated size for expanded subgraphs.
 * @property {boolean} [active] - True when the node represents the active execution step.
 * @property {boolean} [interrupted] - True when the node represents an interrupted execution step.
 * @property {boolean} [collapsed] - True when a subgraph node is collapsed.
 * @property {() => void} [onToggle] - Toggles a subgraph node between collapsed and expanded states.
 * @property {(event: unknown, params: Size) => void} [onResizeEnd] - Persists the resized subgraph dimensions.
 * @property {Record<string, any>} [extra] - Additional server-provided node metadata.
 */

/**
 * A graph node from the LangGraph4j DSL, compatible with React Flow nodes.
 *
 * @typedef {import('@xyflow/react').Node<GraphNodeData>} GraphNode
 */

/**
 * Data carried by a LangGraph4j graph edge rendered through React Flow.
 *
 * @typedef {Object} GraphEdgeData
 * @property {string} [condition] - Conditional edge label.
 * @property {string} [originalSource] - Subgraph id before boundary edge rewriting.
 * @property {string} [originalTarget] - Subgraph id before boundary edge rewriting.
 * @property {Record<string, any>} [extra] - Additional server-provided edge metadata.
 */

/**
 * A graph edge from the LangGraph4j DSL, compatible with React Flow edges.
 *
 * @typedef {import('@xyflow/react').Edge<GraphEdgeData>} GraphEdge
 */

/**
 * LangGraph4j graph document consumed by the graph viewer.
 *
 * @typedef {Object} GraphDsl
 * @property {'langgraph4j'} type
 * @property {GraphNode[]} nodes
 * @property {GraphEdge[]} edges
 * @property {Array<{ id: string }>} [subgraphs]
 */

/**
 * Persisted graph layout stored for the current browser session.
 *
 * @typedef {Object} StoredGraphLayout
 * @property {Record<string, Point>} positions - Node positions keyed by node id.
 * @property {Record<string, Size>} sizes - Resized subgraph dimensions keyed by node id.
 * @property {string[]} collapsedSubgraphs - Collapsed subgraph ids.
 */
