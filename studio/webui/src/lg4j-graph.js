import React, { useCallback, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import dagre from '@dagrejs/dagre';
// @ts-ignore Parcel bundle-text imports are resolved by the bundler.
import * as reactFlowStyles from "bundle-text:@xyflow/react/dist/style.css";
// @ts-ignore React Flow runtime exports are resolved by the bundler.
import {
  ReactFlowProvider,
  Background,
  BaseEdge,
  ControlButton,
  Controls,
  getBezierPath,
  // @ts-ignore React Flow exposes Handle at runtime, but its package types flag it in checked JS.
  Handle,
  MarkerType,
  //MiniMap,
  NodeResizer,
  Position,
  ReactFlow,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';

import { debug } from './debug.js';

const _DBG = debug( { on: true, topic: 'LG4JGraph' } )

/**
 * @file React Flow based LangGraph4j graph viewer.
 * @typedef {import('react').ReactElement} ReactElement
 * @typedef {import('@xyflow/react').NodeChange<import('./types.js').GraphNode>} GraphNodeChange
 * @typedef {import('./types.js').GraphDsl} GraphDsl
 * @typedef {import('./types.js').GraphEdge} GraphEdge
 * @typedef {import('./types.js').GraphNode} GraphNode
 * @typedef {import('./types.js').NextNodeData} NextNodeData
 * @typedef {import('./types.js').Point} Point
 * @typedef {import('./types.js').Size} Size
 * @typedef {import('./types.js').StoredGraphLayout} StoredGraphLayout
 */

const h = React.createElement;
const ROOT_PARENT = '__ROOT__';
const DEFAULT_NODE_GAP = 50;
const DAGRE_RANK_GAP_OFFSET = 32;
const SUBGRAPH_PADDING_X = 40;
const SUBGRAPH_PADDING_TOP = 64;
const SUBGRAPH_PADDING_BOTTOM = 40;
const LAYOUT_STORAGE_PREFIX = 'lg4j-studio.graph-layout.';
const EDGE_LABEL_PROGRESS = 0.74;

/**
 * Builds a stable session storage key for a serialized graph document.
 *
 * @param {string} source - Serialized DSL document.
 * @returns {string} Session storage key scoped to the graph content.
 */
function graphLayoutStorageKey(source) {
  let hash = 0;
  for (let index = 0; index < source.length; index += 1) {
    hash = ((hash << 5) - hash + source.charCodeAt(index)) | 0;
  }
  return `${LAYOUT_STORAGE_PREFIX}${Math.abs(hash).toString(36)}`;
}

/**
 * Checks whether a value is a finite two-dimensional point.
 *
 * @param {unknown} value - Candidate point.
 * @returns {value is Point} True when the value can be used as a node position.
 */
function isPoint(value) {
  return Boolean(value) &&
    typeof value === 'object' &&
    Number.isFinite(/** @type {{ x?: unknown }} */ (value).x) &&
    Number.isFinite(/** @type {{ y?: unknown }} */ (value).y);
}

/**
 * Checks whether a value is a finite positive size.
 *
 * @param {unknown} value - Candidate size.
 * @returns {value is Size} True when the value can be used as a node size.
 */
function isSize(value) {
  return Boolean(value) &&
    typeof value === 'object' &&
    Number.isFinite(/** @type {{ width?: unknown }} */ (value).width) &&
    Number.isFinite(/** @type {{ height?: unknown }} */ (value).height) &&
    /** @type {{ width: number, height: number }} */ (value).width > 0 &&
    /** @type {{ width: number, height: number }} */ (value).height > 0;
}

/**
 * Reads a stored graph layout from session storage.
 *
 * @param {string} storageKey - Session storage key.
 * @returns {StoredGraphLayout | null} Parsed layout, or null when missing or invalid.
 */
function readStoredGraphLayout(storageKey) {
  try {
    const rawLayout = window.sessionStorage.getItem(storageKey);
    if (!rawLayout) {
      return null;
    }

    const parsed = /** @type {unknown} */ (JSON.parse(rawLayout));
    if (!parsed || typeof parsed !== 'object') {
      return null;
    }

    const layout = /** @type {{ positions?: unknown, sizes?: unknown, collapsedSubgraphs?: unknown }} */ (parsed);
    if (!layout.positions || typeof layout.positions !== 'object' || !layout.sizes || typeof layout.sizes !== 'object') {
      return null;
    }

    /** @type {Record<string, Point>} */
    const positions = {};
    for (const [id, position] of Object.entries(layout.positions)) {
      if (isPoint(position)) {
        positions[id] = { x: position.x, y: position.y };
      }
    }

    /** @type {Record<string, Size>} */
    const sizes = {};
    for (const [id, size] of Object.entries(layout.sizes)) {
      if (isSize(size)) {
        sizes[id] = { width: size.width, height: size.height };
      }
    }

    const collapsedSubgraphs = Array.isArray(layout.collapsedSubgraphs)
      ? layout.collapsedSubgraphs.filter((id) => typeof id === 'string')
      : [];

    return { positions, sizes, collapsedSubgraphs };
  }
  catch (caught) {
    console.warn('Unable to read saved graph layout from sessionStorage.', caught);
    return null;
  }
}

/**
 * Converts a record of points to a map.
 *
 * @param {Record<string, Point>} positions - Positions keyed by node id.
 * @returns {Map<string, Point>} Position map.
 */
function positionsToMap(positions) {
  return new Map(Object.entries(positions));
}

/**
 * Converts a record of sizes to a map.
 *
 * @param {Record<string, Size>} sizes - Sizes keyed by node id.
 * @returns {Map<string, Size>} Size map.
 */
function sizesToMap(sizes) {
  return new Map(Object.entries(sizes));
}

/**
 * Persists the current graph layout in session storage.
 *
 * @param {string} storageKey - Session storage key.
 * @param {GraphNode[]} nodes - Current React Flow nodes.
 * @param {Map<string, Point>} savedPositions - User-adjusted node positions.
 * @param {Map<string, Size>} savedSizes - User-adjusted subgraph sizes.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {void}
 */
function saveGraphLayout(storageKey, nodes, savedPositions, savedSizes, collapsedSubgraphs) {
  /** @type {Record<string, Point>} */
  const positions = {};
  for (const node of nodes) {
    const position = savedPositions.get(node.id) || node.position;
    if (isPoint(position)) {
      positions[node.id] = { x: position.x, y: position.y };
    }
  }

  /** @type {Record<string, Size>} */
  const sizes = {};
  for (const [id, size] of savedSizes.entries()) {
    if (isSize(size)) {
      sizes[id] = { width: size.width, height: size.height };
    }
  }

  try {
    window.sessionStorage.setItem(storageKey, JSON.stringify({
      positions,
      sizes,
      collapsedSubgraphs: [...collapsedSubgraphs]
    }));
  }
  catch (caught) {
    console.warn('Unable to save graph layout to sessionStorage.', caught);
  }
}

/**
 * Removes a saved graph layout from session storage.
 *
 * @param {string} storageKey - Session storage key.
 * @returns {void}
 */
function removeGraphLayout(storageKey) {
  try {
    window.sessionStorage.removeItem(storageKey);
  }
  catch (caught) {
    console.warn('Unable to remove saved graph layout from sessionStorage.', caught);
  }
}

/**
 * Parses the node-gap attribute value, falling back to the default gap.
 *
 * @param {string | null} value - Raw attribute value.
 * @returns {number} Non-negative node gap in pixels.
 */
function parseNodeGap(value) {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : DEFAULT_NODE_GAP;
}

/**
 * Renders a circular start or end node.
 *
 * @param {{ data: import('./types.js').GraphNodeData }} props - React Flow node props.
 * @returns {ReactElement} Circle node element.
 */
function CircleNode({ data }) {
  const kind = data?.kind === 'start' ? 'start' : 'end';
  return h('div', { className: `circle-node ${kind}` },
    h(Handle, { type: 'target', position: Position.Top }),
    h('span', null, data?.label?.replaceAll('_', '') || kind),
    h(Handle, { type: 'source', position: Position.Bottom })
  );
}

/**
 * Renders a resizable subgraph container node.
 *
 * @param {{ data: import('./types.js').GraphNodeData, selected: boolean }} props - React Flow node props.
 * @returns {ReactElement} Subgraph node element.
 */
function SubgraphNode({ data, selected }) {
  return h('div', { className: data.collapsed ? 'subgraph-node collapsed' : 'subgraph-node' },
    h(NodeResizer, {
      isVisible: selected,
      minWidth: 220,
      minHeight: data.collapsed ? 56 : 180,
      onResizeEnd: data.onResizeEnd
    }),
    h(Handle, { type: 'target', position: data.collapsed ? Position.Top : Position.Left }),
    h('div', { className: 'subgraph-header' },
      h('span', null, data?.label || 'subgraph'),
      h('button', {
        className: 'subgraph-toggle',
        title: data.collapsed ? 'Expand subgraph' : 'Collapse subgraph',
        onClick: (event) => {
          event.stopPropagation();
          data.onToggle?.();
        }
      }, data.collapsed ? '+' : '-')
    ),
    h(Handle, { type: 'source', position: data.collapsed ? Position.Bottom : Position.Right })
  );
}

const nodeTypes = {
  circle: CircleNode,
  subgraph: SubgraphNode
};

/**
 * Finds a point along an edge, biased toward the target node.
 *
 * @param {number} sourceX - Source x coordinate.
 * @param {number} sourceY - Source y coordinate.
 * @param {number} targetX - Target x coordinate.
 * @param {number} targetY - Target y coordinate.
 * @returns {Point} Label coordinates.
 */
function targetBiasedLabelPoint(sourceX, sourceY, targetX, targetY) {
  return {
    x: sourceX + ((targetX - sourceX) * EDGE_LABEL_PROGRESS),
    y: sourceY + ((targetY - sourceY) * EDGE_LABEL_PROGRESS)
  };
}

/**
 * Renders LangGraph4j semantic edges registered as React Flow custom edge types.
 *
 * @param {import('@xyflow/react').EdgeProps<GraphEdge>} props - React Flow edge props.
 * @returns {ReactElement} Edge element.
 */
function LangGraphEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition = Position.Bottom,
  targetPosition = Position.Top,
  label,
  labelStyle,
  labelShowBg,
  labelBgStyle,
  labelBgPadding,
  labelBgBorderRadius,
  style,
  markerEnd,
  markerStart,
  interactionWidth
}) {
  const [edgePath] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition
  });
  const labelPoint = targetBiasedLabelPoint(sourceX, sourceY, targetX, targetY);

  return h(BaseEdge, {
    id,
    path: edgePath,
    labelX: labelPoint.x,
    labelY: labelPoint.y,
    label,
    labelStyle,
    labelShowBg: false,
    labelBgStyle,
    labelBgPadding,
    labelBgBorderRadius,
    style,
    markerEnd,
    markerStart,
    interactionWidth
  });
}

const edgeTypes = {
  default: LangGraphEdge,
  conditional: LangGraphEdge,
  parallel: LangGraphEdge
};

/**
 * Control button that toggles session persistence for the current graph layout.
 *
 * @param {{ enabled: boolean, disabled: boolean, onToggle: () => void }} props - Button properties.
 * @returns {ReactElement} React Flow control button.
 */
function LayoutToggleButton({ enabled, disabled, onToggle }) {
  const title = enabled ? 'Remove saved layout' : 'Save layout';
  return h(ControlButton, {
    className: enabled ? 'layout-toggle-button saved' : 'layout-toggle-button',
    disabled,
    title,
    'aria-label': title,
    onClick: onToggle
  },
  h('svg', {
    className: 'layout-toggle-icon',
    viewBox: '0 0 24 24',
    fill: 'currentColor',
    'aria-hidden': true
  },
  h('path', { d: 'M9 3h6v5H9z' }),
  h('path', { d: 'M4 16h6v5H4z' }),
  h('path', { d: 'M14 16h6v5h-6z' }),
  h('path', { d: 'M11 9h2v4H11z' }),
  h('path', { d: 'M6 13h12v2H6z' }),
  h('path', { d: 'M11 15h2v2H11z' })
  ));
}

/**
 * Returns the display size used by React Flow and the layout engine.
 *
 * @param {GraphNode} node - Graph node to measure.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {Size} Width and height in pixels.
 */
function nodeSize(node, collapsedSubgraphs) {
  const isSubgraph = node.data?.kind === 'subgraph';
  const isBoundary = node.data?.kind === 'start' || node.data?.kind === 'end';
  const layoutSize = node.data?.layoutSize;
  return {
    width: isSubgraph && layoutSize ? layoutSize.width : isSubgraph ? 320 : isBoundary ? 54 : 140,
    height: isSubgraph && layoutSize ? layoutSize.height : isSubgraph ? (collapsedSubgraphs.has(node.id) ? 56 : 300) : isBoundary ? 54 : 48
  };
}

/**
 * Converts a DSL node into a React Flow node with runtime viewer state.
 *
 * @param {GraphNode} node - Node to normalize.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @param {(id: string) => void} toggleSubgraph - Callback that toggles a subgraph.
 * @param {Map<string, Point>} savedPositions - User-adjusted node positions.
 * @param {Map<string, Size>} savedSizes - User-adjusted subgraph sizes.
 * @param {string | undefined} activeNodeId - Currently active node id.
 * @param {() => void} onLayoutChanged - Callback invoked after mutable layout data changes.
 * @returns {GraphNode} Normalized React Flow node.
 */
function normalizeNode(node, collapsedSubgraphs, toggleSubgraph, savedPositions, savedSizes, activeNodeId, onLayoutChanged) {
  const size = nodeSize(node, collapsedSubgraphs);
  const savedSize = savedSizes.get(node.id);
  const renderedSize = node.data?.kind === 'subgraph' && savedSize && !collapsedSubgraphs.has(node.id)
    ? savedSize
    : size;
  const savedPosition = savedPositions.get(node.id);
  const active = activeNodeId === node.id;
  const activeBoundary = active && (node.data?.kind === 'start' || node.data?.kind === 'end');
  return {
    ...node,
    type: node.data?.kind === 'subgraph' ? 'subgraph' : node.data?.kind === 'start' || node.data?.kind === 'end' ? 'circle' : node.type,
    className: active ? [node.className, 'active-node', activeBoundary ? 'active-boundary-node' : null].filter(Boolean).join(' ') : node.className,
    draggable: true,
    extent: node.parentId ? 'parent' : node.extent,
    position: savedPosition || node.position,
    sourcePosition: Position.Bottom,
    targetPosition: Position.Top,
    style: {
      width: renderedSize.width,
      height: renderedSize.height,
      ...(node.style || {})
    },
    data: {
      ...node.data,
      active,
      collapsed: node.data?.kind === 'subgraph' && collapsedSubgraphs.has(node.id),
      onToggle: node.data?.kind === 'subgraph' ? () => toggleSubgraph(node.id) : undefined,
      onResizeEnd: node.data?.kind === 'subgraph' ? (_, params) => {
        savedSizes.set(node.id, {
          width: params.width,
          height: params.height
        });
        onLayoutChanged();
      } : undefined
    },
    zIndex: node.data?.kind === 'subgraph' ? -1 : undefined
  };
}

/**
 * Converts a DSL edge into a styled React Flow edge.
 *
 * @param {GraphEdge} edge - Edge to normalize.
 * @returns {GraphEdge} Normalized React Flow edge.
 */
function normalizeEdge(edge) {
  return {
    ...edge,
    animated: edge.type === 'conditional',
    markerEnd: { type: MarkerType.ArrowClosed },
    label: edge.label || edge.data?.condition,
    style: {
      strokeWidth: edge.type === 'parallel' ? 2 : 1.5,
      strokeDasharray: edge.type === 'conditional' ? '6 4' : undefined,
      ...(edge.style || {})
    }
  };
}

/**
 * Rewrites edges connected to expanded subgraph containers to their boundary nodes.
 *
 * @param {GraphDsl} dsl - Parsed LangGraph4j DSL document.
 * @returns {GraphEdge[]} Edges with subgraph endpoints mapped to start/end boundary nodes.
 */
function rewriteSubgraphBoundaryEdges(dsl) {
  const subgraphIds = new Set((dsl.subgraphs || []).map((subgraph) => subgraph.id));
  return dsl.edges.map((edge) => {
    const rewritten = { ...edge };
    if (subgraphIds.has(edge.target)) {
      rewritten.target = `${edge.target}-__START__`;
      rewritten.data = { ...(edge.data || {}), originalTarget: edge.target };
    }
    if (subgraphIds.has(edge.source)) {
      rewritten.source = `${edge.source}-__END__`;
      rewritten.data = { ...(rewritten.data || {}), originalSource: edge.source };
    }
    return rewritten;
  });
}

/**
 * Builds a lookup from node id to parent id.
 *
 * @param {GraphNode[]} nodes - Nodes to index.
 * @returns {Map<string, string | undefined>} Parent id by node id.
 */
function buildParentIndex(nodes) {
  return new Map(nodes.map((node) => [node.id, node.parentId]));
}

/**
 * Finds the nearest collapsed ancestor for a node.
 *
 * @param {string} nodeId - Node id to inspect.
 * @param {Map<string, string | undefined>} parentIndex - Parent id by node id.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {string | null} Collapsed ancestor id, or null when visible.
 */
function collapsedAncestor(nodeId, parentIndex, collapsedSubgraphs) {
  let current = parentIndex.get(nodeId);
  while (current) {
    if (collapsedSubgraphs.has(current)) {
      return current;
    }
    current = parentIndex.get(current);
  }
  return null;
}

/**
 * Checks whether a node is hidden inside a collapsed subgraph.
 *
 * @param {GraphNode} node - Node to inspect.
 * @param {Map<string, string | undefined>} parentIndex - Parent id by node id.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {boolean} True when a collapsed parent hides the node.
 */
function isHiddenByCollapsedParent(node, parentIndex, collapsedSubgraphs) {
  return collapsedAncestor(node.id, parentIndex, collapsedSubgraphs) !== null;
}

/**
 * Re-targets edges that cross collapsed subgraphs and removes duplicates.
 *
 * @param {GraphEdge[]} edges - Candidate visible edges.
 * @param {Map<string, string | undefined>} parentIndex - Parent id by node id.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {GraphEdge[]} Visible edge list.
 */
function visibleEdges(edges, parentIndex, collapsedSubgraphs) {
  return edges
    .map((edge) => {
      const sourceOwner = collapsedAncestor(edge.source, parentIndex, collapsedSubgraphs);
      const targetOwner = collapsedAncestor(edge.target, parentIndex, collapsedSubgraphs);
      return {
        ...edge,
        source: sourceOwner || edge.source,
        target: targetOwner || edge.target
      };
    })
    .filter((edge, index, allEdges) =>
      edge.source !== edge.target &&
      allEdges.findIndex((candidate) =>
        candidate.source === edge.source &&
        candidate.target === edge.target &&
        candidate.label === edge.label
      ) === index
    );
}

/**
 * Returns the layout group key for a node.
 *
 * @param {GraphNode} node - Node to classify.
 * @returns {string} Parent id or the root group id.
 */
function parentKey(node) {
  return node.parentId || ROOT_PARENT;
}

/**
 * Groups nodes by parent for recursive layout.
 *
 * @param {GraphNode[]} nodes - Nodes to group.
 * @returns {Map<string, GraphNode[]>} Nodes keyed by layout group.
 */
function collectLayoutGroups(nodes) {
  const groups = new Map();
  for (const node of nodes) {
    const key = parentKey(node);
    const group = groups.get(key) || [];
    group.push(node);
    groups.set(key, group);
  }
  return groups;
}

/**
 * Creates a Dagre graph configured for a single React Flow parent group.
 *
 * @param {string} groupKey - Group id being laid out.
 * @param {number} nodeGap - Gap between nodes in pixels.
 * @returns {dagre.graphlib.Graph} Configured Dagre graph.
 */
function createDagreGraph(groupKey, nodeGap) {
  const marginX = groupKey === ROOT_PARENT ? nodeGap : SUBGRAPH_PADDING_X;
  const marginY = groupKey === ROOT_PARENT ? nodeGap : SUBGRAPH_PADDING_TOP;
  return new dagre.graphlib.Graph()
    .setDefaultEdgeLabel(() => ({}))
    .setGraph({
      rankdir: 'TB',
      align: 'UL',
      nodesep: nodeGap,
      ranksep: nodeGap + DAGRE_RANK_GAP_OFFSET,
      marginx: marginX,
      marginy: marginY
    });
}

/**
 * Returns a copied node with mutable data for layout annotations.
 *
 * @param {GraphNode} node - Node to copy.
 * @returns {GraphNode} Copied node.
 */
function copyLayoutNode(node) {
  return {
    ...node,
    data: { ...(node.data || {}) }
  };
}

/**
 * Adds same-parent edges to a Dagre graph.
 *
 * @param {dagre.graphlib.Graph} dagreGraph - Dagre graph to populate.
 * @param {Set<string>} groupNodeIds - Node ids that belong to the current group.
 * @param {GraphEdge[]} layoutEdges - Edges used by Dagre.
 * @returns {void}
 */
function addDagreEdges(dagreGraph, groupNodeIds, layoutEdges) {
  for (const edge of layoutEdges) {
    if (groupNodeIds.has(edge.source) && groupNodeIds.has(edge.target)) {
      dagreGraph.setEdge(edge.source, edge.target);
    }
  }
}

/**
 * Applies Dagre-calculated positions to the group nodes.
 *
 * @param {dagre.graphlib.Graph} dagreGraph - Dagre graph after layout.
 * @param {GraphNode[]} groupNodes - Nodes in the current group.
 * @param {Map<string, Point>} savedPositions - User-adjusted node positions.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {GraphNode[]} Positioned nodes.
 */
function positionDagreNodes(dagreGraph, groupNodes, savedPositions, collapsedSubgraphs) {
  return groupNodes.map((node) => {
    const size = nodeSize(node, collapsedSubgraphs);
    const dagreNode = dagreGraph.node(node.id);
    const position = savedPositions.get(node.id) || {
      x: dagreNode.x - size.width / 2,
      y: dagreNode.y - size.height / 2
    };
    return {
      ...node,
      position
    };
  });
}

/**
 * Calculates default node positions recursively for root and subgraph groups with Dagre.
 *
 * @param {GraphNode[]} nodes - Visible nodes to place.
 * @param {GraphEdge[]} layoutEdges - Edges used by Dagre.
 * @param {Map<string, Point>} savedPositions - User-adjusted node positions.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @param {number} nodeGap - Gap between nodes in pixels.
 * @returns {GraphNode[]} Positioned nodes sorted by parent depth.
 */
function autoLayoutNodes(nodes, layoutEdges, savedPositions, collapsedSubgraphs, nodeGap) {
  const groups = collectLayoutGroups(nodes);
  /** @type {GraphNode[]} */
  const nextNodes = [];
  /** @type {Set<string>} */
  const visitedGroups = new Set();

  /**
   * Lays out a group and returns the size required by its parent subgraph.
   *
   * @param {string} groupKey - Group id to layout.
   * @returns {Size | null} Required subgraph size, or null for an empty group.
   */
  const layoutGroup = (groupKey) => {
    if (visitedGroups.has(groupKey)) {
      return null;
    }
    visitedGroups.add(groupKey);

    const groupNodes = (groups.get(groupKey) || []).map(copyLayoutNode);
    if (groupNodes.length === 0) {
      return null;
    }

    for (const node of groupNodes) {
      if (node.data?.kind === 'subgraph' && !collapsedSubgraphs.has(node.id)) {
        const layoutSize = layoutGroup(node.id);
        if (layoutSize) {
          node.data.layoutSize = layoutSize;
        }
      }
    }

    const dagreGraph = createDagreGraph(groupKey, nodeGap);
    const groupNodeIds = new Set(groupNodes.map((node) => node.id));
    for (const node of groupNodes) {
      dagreGraph.setNode(node.id, nodeSize(node, collapsedSubgraphs));
    }
    addDagreEdges(dagreGraph, groupNodeIds, layoutEdges);
    dagre.layout(dagreGraph);

    const positionedNodes = positionDagreNodes(dagreGraph, groupNodes, savedPositions, collapsedSubgraphs);
    nextNodes.push(...positionedNodes);

    const graphSize = dagreGraph.graph();
    return {
      width: Math.max(320, graphSize.width + (groupKey === ROOT_PARENT ? 0 : SUBGRAPH_PADDING_X)),
      height: Math.max(180, graphSize.height + (groupKey === ROOT_PARENT ? 0 : SUBGRAPH_PADDING_BOTTOM))
    };
  };

  layoutGroup(ROOT_PARENT);
  for (const groupKey of groups.keys()) {
    layoutGroup(groupKey);
  }

  /** @type {(node: GraphNode) => number} */
  const depthOf = (node) => {
    let depth = 0;
    let parentId = node.parentId;
    while (parentId) {
      depth += 1;
      parentId = nodes.find((candidate) => candidate.id === parentId)?.parentId;
    }
    return depth;
  };

  return nextNodes.sort((left, right) => depthOf(left) - depthOf(right));
}


/**
 * React component that renders a parsed DSL document with React Flow.
 *
 * @param {{ source?: string, activeNodeId?: string, nodeGap: number }} props - Viewer properties.
 * @returns {ReactElement} React Flow graph component.
 */
function GraphFlow({ source, activeNodeId, nodeGap }) {
  const [dsl, setDsl] = useState(/** @type {GraphDsl | null} */ (null));
  const [collapsedSubgraphs, setCollapsedSubgraphs] = useState(/** @type {Set<string>} */ (new Set()));
  const [nodes, setNodes, onNodesChange] = useNodesState(/** @type {GraphNode[]} */ ([]));
  const [edges, setEdges, onEdgesChange] = useEdgesState(/** @type {GraphEdge[]} */ ([]));
  const [interactive, setInteractive] = useState(true);
  const [layoutSaved, setLayoutSaved] = useState(false);
  const [layoutStorageKey, setLayoutStorageKey] = useState(/** @type {string | null} */ (null));
  const flowWrapperRef = React.useRef(/** @type {HTMLDivElement | null} */ (null));
  const savedPositionsRef = React.useRef(/** @type {Map<string, Point>} */ (new Map()));
  const savedSizesRef = React.useRef(/** @type {Map<string, Size>} */ (new Map()));
  const nodesRef = React.useRef(/** @type {GraphNode[]} */ ([]));
  const collapsedSubgraphsRef = React.useRef(/** @type {Set<string>} */ (new Set()));
  const layoutSavedRef = React.useRef(false);
  const layoutStorageKeyRef = React.useRef(/** @type {string | null} */ (null));

  const { fitView } = useReactFlow();

  React.useEffect(() => {
    nodesRef.current = nodes;
  }, [nodes]);

  React.useEffect(() => {
    collapsedSubgraphsRef.current = collapsedSubgraphs;
  }, [collapsedSubgraphs]);

  React.useEffect(() => {
    layoutSavedRef.current = layoutSaved;
  }, [layoutSaved]);

  React.useEffect(() => {
    layoutStorageKeyRef.current = layoutStorageKey;
  }, [layoutStorageKey]);

  /**
   * Persists the latest layout when layout persistence is enabled.
   *
   * @returns {void}
   */
  const persistSavedLayout = useCallback(() => {
    const storageKey = layoutStorageKeyRef.current;
    if (!layoutSavedRef.current || !storageKey || nodesRef.current.length === 0) {
      return;
    }
    saveGraphLayout(
      storageKey,
      nodesRef.current,
      savedPositionsRef.current,
      savedSizesRef.current,
      collapsedSubgraphsRef.current
    );
  }, []);
      
  /**
   * Toggles a subgraph collapsed state.
   *
   * @param {string} id - Subgraph id.
   * @returns {void}
   */
  /** @type {(id: string) => void} */
  const toggleSubgraph = useCallback((id) => {
    setCollapsedSubgraphs((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      }
      else {
        next.add(id);
      }
      collapsedSubgraphsRef.current = next;
      return next;
    });
    requestAnimationFrame(persistSavedLayout);
  }, [persistSavedLayout]);

  /**
   * Applies a parsed DSL document to React Flow state.
   *
   * @param {GraphDsl} nextDsl - DSL document to render.
   * @param {Set<string>} nextCollapsedSubgraphs - Collapsed subgraph ids.
   * @returns {void}
   */
  /** @type {(nextDsl: GraphDsl, nextCollapsedSubgraphs: Set<string>) => void} */
  const applyDsl = useCallback((nextDsl, nextCollapsedSubgraphs) => {
    const parentIndex = buildParentIndex(nextDsl.nodes);
    const graphEdges = rewriteSubgraphBoundaryEdges(nextDsl);
    const layoutEdges = visibleEdges(nextDsl.edges, parentIndex, nextCollapsedSubgraphs);
    const visibleNodes = nextDsl.nodes
      .filter((node) => !isHiddenByCollapsedParent(node, parentIndex, nextCollapsedSubgraphs));
    const layoutNodes = autoLayoutNodes(visibleNodes, layoutEdges, savedPositionsRef.current, nextCollapsedSubgraphs, nodeGap);
    const normalizedNodes = layoutNodes.map((node) => normalizeNode(
      node,
      nextCollapsedSubgraphs,
      toggleSubgraph,
      savedPositionsRef.current,
      savedSizesRef.current,
      activeNodeId,
      persistSavedLayout
    ));
    nodesRef.current = normalizedNodes;
    setNodes(normalizedNodes);
    setEdges(visibleEdges(graphEdges, parentIndex, nextCollapsedSubgraphs).map(normalizeEdge));
  }, [activeNodeId, nodeGap, persistSavedLayout, setEdges, setNodes, toggleSubgraph]);

  /**
   * Tracks user-positioned nodes before delegating to React Flow.
   *
   * @param {GraphNodeChange[]} changes - React Flow node changes.
   * @returns {void}
   */
  /** @type {(changes: GraphNodeChange[]) => void} */
  const handleNodesChange = useCallback((changes) => {
    if (!interactive) {
      return;
    }
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        savedPositionsRef.current.set(change.id, change.position);
      }
    }
    onNodesChange(changes);
    requestAnimationFrame(persistSavedLayout);
  }, [onNodesChange, interactive, persistSavedLayout]);

  /**
   * Parses and renders a LangGraph4j DSL JSON string.
   *
   * @param {string} value - Serialized DSL document.
   * @returns {void}
   */
  /** @type {(value: string) => void} */
  const renderDsl = useCallback((value) => {
    const nextDsl = /** @type {GraphDsl} */ (JSON.parse(value));
    if (nextDsl.type !== 'langgraph4j' || !Array.isArray(nextDsl.nodes) || !Array.isArray(nextDsl.edges)) {
      throw new Error('JSON is not a Langgraph4j DSL document.');
    }
    //console.log( 'Parsed DSL:', JSON.stringify(nextDsl, null, 2) );

    const nextLayoutStorageKey = graphLayoutStorageKey(value);
    const storedLayout = readStoredGraphLayout(nextLayoutStorageKey);
    savedPositionsRef.current = storedLayout ? positionsToMap(storedLayout.positions) : new Map();
    savedSizesRef.current = storedLayout ? sizesToMap(storedLayout.sizes) : new Map();
    
    const nextCollapsedSubgraphs = new Set(storedLayout?.collapsedSubgraphs || []);
    collapsedSubgraphsRef.current = nextCollapsedSubgraphs;
    layoutStorageKeyRef.current = nextLayoutStorageKey;
    layoutSavedRef.current = Boolean(storedLayout);
    setDsl(nextDsl);
    setCollapsedSubgraphs(nextCollapsedSubgraphs);
    setLayoutStorageKey(nextLayoutStorageKey);
    setLayoutSaved(Boolean(storedLayout));
    applyDsl(nextDsl, nextCollapsedSubgraphs);
  }, [applyDsl]);

  React.useEffect(() => {
    if (dsl) {
      applyDsl(dsl, collapsedSubgraphs);
    }
  }, [applyDsl, collapsedSubgraphs, dsl]);

  React.useEffect(() => {
    if (!source) {
      return;
    }
    try {
      renderDsl(source);
    }
    catch (caught) {
      console.error(caught);
    }
  }, [renderDsl, source]);

  React.useEffect(() => {
    if (nodes.length === 0) {
      return;
    }
    requestAnimationFrame(() => fitView({ padding: 0.16, duration: 200 }));
  }, [fitView, nodes.length]);

  React.useEffect(() => {
    requestAnimationFrame(persistSavedLayout);
  }, [collapsedSubgraphs, nodes, persistSavedLayout]);

  React.useEffect(() => {
    if (!flowWrapperRef.current) {
      return undefined;
    }

    const resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry || entry.contentRect.width === 0 || entry.contentRect.height === 0 || nodes.length === 0) {
        return;
      }
      requestAnimationFrame(() => fitView({ padding: 0.16, duration: 120 }));
    });

    resizeObserver.observe(flowWrapperRef.current);
    return () => resizeObserver.disconnect();
  }, [fitView, nodes.length]);

  /**
   * Toggles the persisted layout for the currently rendered graph.
   *
   * @returns {void}
   */
  const handleLayoutToggle = useCallback(() => {
    const storageKey = layoutStorageKeyRef.current;
    if (!storageKey) {
      return;
    }

    if (layoutSavedRef.current) {
      removeGraphLayout(storageKey);
      layoutSavedRef.current = false;
      setLayoutSaved(false);
      return;
    }

    saveGraphLayout(storageKey, nodesRef.current, savedPositionsRef.current, savedSizesRef.current, collapsedSubgraphsRef.current);
    layoutSavedRef.current = true;
    setLayoutSaved(true);
  }, []);

  const flow = useMemo(() => h('div', { className: 'flow-wrapper', ref: flowWrapperRef },
    h(ReactFlow, {
      nodes,
      edges,
      nodeTypes,
      edgeTypes,
      onNodesChange: handleNodesChange,
      onEdgesChange,
      //onInit: (instance) => {},
      fitView: true,
      fitViewOptions: { padding: 0.16 },
      minZoom: 0.2,
      maxZoom: 1.5,
      style: { width: '100%', height: '100%' },
      // interaction properties: false
      nodesDraggable: interactive,
      elementsSelectable: interactive,
      nodesConnectable: interactive,

    },
    // h(MiniMap, null),
    h(Controls, {
      
      onInteractiveChange: (prev) => { 
        _DBG('Interactive status changed:', prev);
        setInteractive(!interactive);
      }
    },
    h(LayoutToggleButton, {
      enabled: layoutSaved,
      disabled: !layoutStorageKey || nodes.length === 0,
      onToggle: handleLayoutToggle
    })),
    h(Background, { gap: 18, size: 1 })
  )), [edges, handleLayoutToggle, handleNodesChange, interactive, layoutSaved, layoutStorageKey, nodes, onEdgesChange]);

  return flow;
}

/**
 * Returns the CSS used inside the graph viewer shadow root.
 *
 * @returns {string} Component stylesheet.
 */
function componentStyles() {
  return `
    ${reactFlowStyles}

    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 100%;
      color-scheme: light;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f6f7f9;
      color: #1f2933;
    }

    * {
      box-sizing: border-box;
    }

    .mount {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .layout-toggle-button {
      color: #4b5563;
    }

    .layout-toggle-button.saved {
      color: #047857;
      background: #ecfdf5;
    }

    .layout-toggle-button:disabled {
      color: #9ca3af;
      cursor: not-allowed;
    }

    .layout-toggle-icon {
      width: 16px;
      height: 16px;
      display: block;
    }

    .app {
      width: 100%;
      height: 100%;
      min-height: 0;
      display: block;
    }

    .graph {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
      background: #eef2f7;
    }

    .flow-wrapper {
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
    }

    .react-flow__node-default {
      border-radius: 6px;
      border: 1px solid #b8c2d1;
      background: #ffffff;
      color: #1f2933;
      font-size: var(--lg4j-workbench-font-size, 12px);
      min-width: 120px;
      text-align: center;
    }

    .circle-node {
      width: 54px;
      height: 54px;
      display: grid;
      place-items: center;
      border-radius: 999px;
      border: 2px solid #64748b;
      background: #ffffff;
      color: #17212f;
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
      text-transform: uppercase;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
    }

    .circle-node.start {
      border-color: #16a34a;
      background: #ecfdf3;
    }

    .circle-node.end {
      border-color: #dc2626;
      background: #fef2f2;
    }

    .subgraph-node {
      width: 100%;
      height: 100%;
      min-width: 220px;
      min-height: 72px;
      border: 1px dashed #64748b;
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.64);
      color: #1f2933;
      overflow: hidden;
    }

    .subgraph-header {
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 0 10px;
      border-bottom: 1px solid rgba(100, 116, 139, 0.28);
      background: rgba(248, 250, 252, 0.9);
      font-size: var(--lg4j-workbench-font-size, 12px);
      font-weight: 700;
    }

    .subgraph-toggle {
      width: 26px;
      height: 26px;
      display: grid;
      place-items: center;
      border: 1px solid #c9d1dc;
      border-radius: 6px;
      background: #ffffff;
      color: #1f2933;
      cursor: pointer;
    }

    .subgraph-node.collapsed {
      min-height: 56px;
    }

    .react-flow__node.active-node {
      filter: drop-shadow(0 0 10px rgba(37, 99, 235, 0.35));
    }

    .react-flow__node.active-node::after {
      content: "";
      position: absolute;
      top: -8px;
      right: -8px;
      width: 18px;
      height: 18px;
      border: 3px solid #bfdbfe;
      border-top-color: #2563eb;
      border-radius: 999px;
      background: #ffffff;
      animation: lg4j-spin 0.8s linear infinite;
      z-index: 2;
    }

    .react-flow__node.active-boundary-node::after {
      content: none;
    }

    .react-flow__node.active-node.react-flow__node-default {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
    }

    .react-flow__node.active-node .circle-node,
    .react-flow__node.active-node .subgraph-node {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
    }

    @keyframes lg4j-spin {
      to {
        transform: rotate(360deg);
      }
    }

  `;
}

/**
 * Custom element that hosts the React Flow graph viewer.
 *
 * @class
 */
export class LG4JDSLViewElement extends HTMLElement {

  /**
   * Attributes observed by the custom element.
   *
   * @returns {string[]} Observed attribute names.
   */
  static get observedAttributes() {
    return ['node-gap'];
  }

  /**
   * Creates the shadow root, style element, and React mount point.
   */
  constructor() {
    super();
    
    const shadow = this.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = componentStyles();
    /** @type {HTMLDivElement} React mount point inside the shadow root. */
    this.mount = document.createElement('div');
    this.mount.className = 'mount';
    shadow.append(style, this.mount);

    /** @type {import('react-dom/client').Root | null} React root for the viewer. */
    this.root = null;
    /** @type {string | undefined} Last serialized graph DSL received from events. */
    this.source = undefined;
    /** @type {string | undefined} Active node id highlighted in the graph. */
    this.activeNodeId = undefined;

    this.render = this.render.bind(this);
    this.onActive = this.onActive.bind(this);
  }

  /**
   * Reacts to observed attribute changes by re-rendering the graph.
   *
   * @returns {void}
   */
  attributeChangedCallback() {
    this.update();
  }

  /**
   * Mounts the React root and registers graph event listeners.
   *
   * @returns {void}
   */
  connectedCallback() {

    // mount root
    if( !this.root ) {
      this.root = createRoot(this.mount);
    }

    this.addEventListener('graph', /** @type {EventListener} */ (this.render));
    this.addEventListener('graph-active', /** @type {EventListener} */ (this.onActive));

  }

  /**
   * Removes event listeners and unmounts the React root.
   *
   * @returns {void}
   */
  disconnectedCallback() {

    this.removeEventListener('graph', /** @type {EventListener} */ (this.render));
    this.removeEventListener('graph-active', /** @type {EventListener} */ (this.onActive));

    // unmount root
    this.root?.unmount();
    this.root = null;
  }

  /**
   * Handles graph content events.
   *
   * @param {CustomEvent<string>} event - Event containing serialized DSL content.
   * @returns {void}
   */
  render(event) {
    this.source = event.detail;
    this.update();
  }

  /**
   * Handles active node events from the executor.
   *
   * @param {CustomEvent<NextNodeData>} event - Event containing active node ids.
   * @returns {void}
   */
  onActive(event) {
    _DBG('Active node changed:', event.detail);
    const { detail: { node, subgraphNode } } = event;
    this.activeNodeId = subgraphNode ?? node
    this.update();
  }

  /**
   * Renders the current graph source into the React root.
   *
   * @returns {void}
   */
  update() {

    this.root?.render( 
      h('main', { className: 'app' },
        h('section', { className: 'graph' }, 
             h( ReactFlowProvider, null,
             h( GraphFlow, { source: this.source, activeNodeId: this.activeNodeId, nodeGap: parseNodeGap(this.getAttribute('node-gap')) } )
          )
        )
      )
    );
  }
}

customElements.define('lg4j-graph', LG4JDSLViewElement);
