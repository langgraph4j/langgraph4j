import React, { useCallback, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
// @ts-ignore Parcel bundle-text imports are resolved by the bundler.
import * as reactFlowStyles from "bundle-text:@xyflow/react/dist/style.css";
// @ts-ignore React Flow runtime exports are resolved by the bundler.
import {
  ReactFlowProvider,
  Background,
  Controls,
  // @ts-ignore React Flow exposes Handle at runtime, but its package types flag it in checked JS.
  Handle,
  MarkerType,
  MiniMap,
  NodeResizer,
  Position,
  ReactFlow,
  useEdgesState,
  useNodesState,
  useReactFlow
} from '@xyflow/react';

/**
 * @file React Flow based LangGraph4j graph viewer.
 * @typedef {import('react').ReactElement} ReactElement
 * @typedef {import('@xyflow/react').NodeChange<import('./types.js').GraphNode>} GraphNodeChange
 * @typedef {import('./types.js').Bounds} Bounds
 * @typedef {import('./types.js').GraphDsl} GraphDsl
 * @typedef {import('./types.js').GraphEdge} GraphEdge
 * @typedef {import('./types.js').GraphNode} GraphNode
 * @typedef {import('./types.js').NextNodeData} NextNodeData
 * @typedef {import('./types.js').Point} Point
 * @typedef {import('./types.js').RankLayout} RankLayout
 * @typedef {import('./types.js').Size} Size
 * @typedef {import('./types.js').SubgraphSequence} SubgraphSequence
 */

const h = React.createElement;
const ROOT_PARENT = '__ROOT__';
const DEFAULT_NODE_GAP = 50;
const ROOT_PADDING_X = 120;
const ROOT_PADDING_TOP = 40;
const SUBGRAPH_PADDING_X = 40;
const SUBGRAPH_PADDING_TOP = 64;
const SUBGRAPH_PADDING_BOTTOM = 40;

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
 * @returns {GraphNode} Normalized React Flow node.
 */
function normalizeNode(node, collapsedSubgraphs, toggleSubgraph, savedPositions, savedSizes, activeNodeId) {
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
 * Returns the synthetic start node id for a layout group.
 *
 * @param {string} parentId - Layout group id.
 * @returns {string} Start node id.
 */
function startNodeId(parentId) {
  return parentId === ROOT_PARENT ? '__START__' : `${parentId}-__START__`;
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
 * Assigns vertical ranks to nodes in one layout group.
 *
 * @param {GraphNode[]} groupNodes - Nodes in the current group.
 * @param {GraphEdge[]} layoutEdges - Edges used to infer node order.
 * @param {string} groupKey - Current layout group id.
 * @returns {Map<string, number>} Rank by node id.
 */
function rankGroupNodes(groupNodes, layoutEdges, groupKey) {
  const ids = new Set(groupNodes.map((node) => node.id));
  /** @type {Map<string, string[]>} */
  const outgoing = new Map();
  for (const edge of layoutEdges) {
    if (ids.has(edge.source) && ids.has(edge.target)) {
      const next = outgoing.get(edge.source) || [];
      if (!next.includes(edge.target)) {
        next.push(edge.target);
        outgoing.set(edge.source, next);
      }
    }
  }

  const acyclicOutgoing = removeCycleEdges(outgoing, groupNodes, groupKey);
  /** @type {Map<string, number>} */
  const ranks = new Map();
  const queue = [startNodeId(groupKey)];
  ranks.set(startNodeId(groupKey), 0);
  while (queue.length > 0) {
    const current = queue.shift();
    if (!current) {
      break;
    }
    const nextRank = (ranks.get(current) || 0) + 1;
    for (const target of acyclicOutgoing.get(current) || []) {
      const targetRank = ranks.get(target);
      if (targetRank === undefined || nextRank > targetRank) {
        ranks.set(target, nextRank);
        queue.push(target);
      }
    }
  }

  let fallbackRank = ranks.size;
  for (const node of groupNodes) {
    if (!ranks.has(node.id)) {
      ranks.set(node.id, fallbackRank++);
    }
  }
  return ranks;
}

/**
 * Removes back-edges from the adjacency map so rank calculation stays acyclic.
 *
 * @param {Map<string, string[]>} outgoing - Directed adjacency list.
 * @param {GraphNode[]} groupNodes - Nodes in traversal order scope.
 * @param {string} groupKey - Current layout group id.
 * @returns {Map<string, string[]>} Acyclic adjacency list.
 */
function removeCycleEdges(outgoing, groupNodes, groupKey) {
  /** @type {Set<string>} */
  const visiting = new Set();
  /** @type {Set<string>} */
  const visited = new Set();
  /** @type {Set<string>} */
  const skippedEdges = new Set();
  const nodeIds = groupNodes.map((node) => node.id);
  const start = startNodeId(groupKey);
  const orderedIds = [
    ...(nodeIds.includes(start) ? [start] : []),
    ...nodeIds.filter((id) => id !== start).sort()
  ];

  /** @type {(id: string) => void} */
  const visit = (id) => {
    if (visited.has(id)) {
      return;
    }
    visiting.add(id);
    for (const target of outgoing.get(id) || []) {
      const edgeKey = `${id}\u0000${target}`;
      if (visiting.has(target)) {
        skippedEdges.add(edgeKey);
        continue;
      }
      visit(target);
    }
    visiting.delete(id);
    visited.add(id);
  };

  for (const id of orderedIds) {
    visit(id);
  }

  if (skippedEdges.size === 0) {
    return outgoing;
  }

  const acyclicOutgoing = new Map();
  for (const [source, targets] of outgoing.entries()) {
    const acyclicTargets = targets.filter((target) => !skippedEdges.has(`${source}\u0000${target}`));
    if (acyclicTargets.length > 0) {
      acyclicOutgoing.set(source, acyclicTargets);
    }
  }
  return acyclicOutgoing;
}

/**
 * Buckets nodes by rank for layout.
 *
 * @param {GraphNode[]} groupNodes - Nodes in the current group.
 * @param {GraphEdge[]} layoutEdges - Edges used to infer ranks.
 * @param {string} groupKey - Current layout group id.
 * @returns {Array<[number, GraphNode[]]>} Sorted rank buckets.
 */
function rankBuckets(groupNodes, layoutEdges, groupKey) {
  const ranks = rankGroupNodes(groupNodes, layoutEdges, groupKey);
  /** @type {Map<number, GraphNode[]>} */
  const byRank = new Map();
  for (const node of groupNodes) {
    const rank = ranks.get(node.id) || 0;
    const bucket = byRank.get(rank) || [];
    bucket.push(node);
    byRank.set(rank, bucket);
  }
  return [...byRank.entries()].sort(([left], [right]) => left - right);
}

/**
 * Returns the size used for automatic layout.
 *
 * @param {GraphNode} node - Node to measure.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {Size} Layout size in pixels.
 */
function nodeLayoutSize(node, collapsedSubgraphs) {
  return nodeSize(node, collapsedSubgraphs);
}

/**
 * Lays out one rank around a centered horizontal axis.
 *
 * @param {GraphNode[]} rankNodes - Nodes in the rank.
 * @param {number} y - Vertical position for the rank.
 * @param {number} nodeGap - Horizontal gap between nodes.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {RankLayout} Node placements and rank height.
 */
function layoutRank(rankNodes, y, nodeGap, collapsedSubgraphs) {
  const orderedNodes = [...rankNodes].sort((left, right) => left.id.localeCompare(right.id));
  const sizes = orderedNodes.map((node) => nodeLayoutSize(node, collapsedSubgraphs));
  const totalWidth = sizes.reduce((sum, size) => sum + size.width, 0) + Math.max(0, sizes.length - 1) * nodeGap;
  let x = -totalWidth / 2;
  let maxHeight = 0;
  return {
    placements: orderedNodes.map((node, index) => {
      const size = sizes[index];
      const position = { node, x, y };
      x += size.width + nodeGap;
      maxHeight = Math.max(maxHeight, size.height);
      return position;
    }),
    height: maxHeight
  };
}

/**
 * Lays out a root rank, placing subgraphs beside the main flow when needed.
 *
 * @param {GraphNode[]} rankNodes - Nodes in the root rank.
 * @param {number} y - Vertical position for the rank.
 * @param {number} nodeGap - Horizontal gap between nodes.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @param {SubgraphSequence} subgraphSequence - Mutable subgraph placement counter.
 * @returns {RankLayout} Node placements and rank height.
 */
function layoutRootRank(rankNodes, y, nodeGap, collapsedSubgraphs, subgraphSequence) {
  const orderedNodes = [...rankNodes].sort((left, right) => left.id.localeCompare(right.id));
  const mainNodes = orderedNodes.filter((node) => node.data?.kind !== 'subgraph');
  const subgraphNodes = orderedNodes.filter((node) => node.data?.kind === 'subgraph');
  const mainLayout = layoutRank(mainNodes, y, nodeGap, collapsedSubgraphs);
  const placements = [...mainLayout.placements];
  let maxHeight = mainLayout.height;
  const mainBounds = placements.reduce((bounds, placement) => {
    const size = nodeLayoutSize(placement.node, collapsedSubgraphs);
    return {
      minX: Math.min(bounds.minX, placement.x),
      maxX: Math.max(bounds.maxX, placement.x + size.width)
    };
  }, { minX: 0, maxX: 0 });
  let rightX = mainBounds.maxX + nodeGap;
  let leftX = mainBounds.minX - nodeGap;

  const placedIds = new Set(placements.map((placement) => placement.node.id));
  for (const node of subgraphNodes) {
    if (placedIds.has(node.id)) {
      continue;
    }
    const size = nodeLayoutSize(node, collapsedSubgraphs);
    const placeRight = subgraphSequence.count % 2 === 0;
    placements.push({
      node,
      x: placeRight ? rightX : leftX - size.width,
      y
    });
    if (placeRight) {
      rightX += size.width + nodeGap;
    }
    else {
      leftX -= size.width + nodeGap;
    }
    subgraphSequence.count += 1;
    maxHeight = Math.max(maxHeight, size.height);
  }

  return { placements, height: maxHeight };
}

/**
 * Calculates bounds around positioned nodes.
 *
 * @param {GraphNode[]} nodes - Positioned nodes.
 * @param {Set<string>} collapsedSubgraphs - Collapsed subgraph ids.
 * @returns {Bounds} Bounds around all nodes.
 */
function boundsOf(nodes, collapsedSubgraphs) {
  return nodes.reduce((bounds, node) => {
    const size = nodeLayoutSize(node, collapsedSubgraphs);
    return {
      minX: Math.min(bounds.minX, node.position.x),
      minY: Math.min(bounds.minY, node.position.y),
      maxX: Math.max(bounds.maxX, node.position.x + size.width),
      maxY: Math.max(bounds.maxY, node.position.y + size.height)
    };
  }, { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity });
}

/**
 * Calculates default node positions recursively for root and subgraph groups.
 *
 * @param {GraphNode[]} nodes - Visible nodes to place.
 * @param {GraphEdge[]} layoutEdges - Edges used to infer ranks.
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
   * @returns {Size | null} Required subgraph size, or null for the root group.
   */
  const layoutGroup = (groupKey) => {
    if (visitedGroups.has(groupKey)) {
      return null;
    }
    visitedGroups.add(groupKey);
    const groupNodes = (groups.get(groupKey) || []).map((node) => ({ ...node, data: { ...(node.data || {}) } }));
    for (const node of groupNodes) {
      if (node.data?.kind === 'subgraph' && !collapsedSubgraphs.has(node.id)) {
        const layoutSize = layoutGroup(node.id);
        if (layoutSize) {
          node.data.layoutSize = layoutSize;
        }
      }
    }

    let y = groupKey === ROOT_PARENT ? ROOT_PADDING_TOP : SUBGRAPH_PADDING_TOP;
    /** @type {SubgraphSequence} */
    const subgraphSequence = { count: 0 };
    for (const [, rankNodes] of rankBuckets(groupNodes, layoutEdges, groupKey)) {
      const rankLayout = groupKey === ROOT_PARENT
        ? layoutRootRank(rankNodes, y, nodeGap, collapsedSubgraphs, subgraphSequence)
        : layoutRank(rankNodes, y, nodeGap, collapsedSubgraphs);

      rankLayout.placements.forEach(({ node, x }) => {
        const savedPosition = savedPositions.get(node.id);
        nextNodes.push({
          ...node,
          position: savedPosition || {
            x: groupKey === ROOT_PARENT ? x : SUBGRAPH_PADDING_X + x,
            y
          }
        });
      });
      y += rankLayout.height + nodeGap;
    }

    const groupLayoutNodes = nextNodes.filter((node) => parentKey(node) === groupKey);
    if (groupLayoutNodes.length === 0) {
      return null;
    }

    const groupBounds = boundsOf(groupLayoutNodes, collapsedSubgraphs);
    if (groupKey !== ROOT_PARENT) {
      const shiftX = SUBGRAPH_PADDING_X - groupBounds.minX;
      if (shiftX !== 0) {
        for (const node of groupLayoutNodes) {
          if (!savedPositions.has(node.id)) {
            node.position = {
              x: node.position.x + shiftX,
              y: node.position.y
            };
          }
        }
      }
      const shiftedBounds = boundsOf(groupLayoutNodes, collapsedSubgraphs);
      return {
        width: Math.max(320, shiftedBounds.maxX + SUBGRAPH_PADDING_X),
        height: Math.max(180, shiftedBounds.maxY + SUBGRAPH_PADDING_BOTTOM)
      };
    }

    return null;
  };

  layoutGroup(ROOT_PARENT);
  for (const groupKey of groups.keys()) {
    layoutGroup(groupKey);
  }

  const rootNodes = nextNodes.filter((node) => !node.parentId && !savedPositions.has(node.id));
  if (rootNodes.length > 0) {
    const bounds = boundsOf(rootNodes, collapsedSubgraphs);
    const shiftX = ROOT_PADDING_X - bounds.minX;
    for (const node of rootNodes) {
      node.position = {
        x: node.position.x + shiftX,
        y: node.position.y
      };
    }
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
  const flowWrapperRef = React.useRef(/** @type {HTMLDivElement | null} */ (null));
  const savedPositionsRef = React.useRef(/** @type {Map<string, Point>} */ (new Map()));
  const savedSizesRef = React.useRef(/** @type {Map<string, Size>} */ (new Map()));

  const { fitView } = useReactFlow();
      
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
      return next;
    });
  }, []);

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
    const visibleNodes = nextDsl.nodes
      .filter((node) => !isHiddenByCollapsedParent(node, parentIndex, nextCollapsedSubgraphs));
    const layoutNodes = autoLayoutNodes(visibleNodes, nextDsl.edges, savedPositionsRef.current, nextCollapsedSubgraphs, nodeGap);
    setNodes(layoutNodes.map((node) => normalizeNode(node, nextCollapsedSubgraphs, toggleSubgraph, savedPositionsRef.current, savedSizesRef.current, activeNodeId)));
    setEdges(visibleEdges(graphEdges, parentIndex, nextCollapsedSubgraphs).map(normalizeEdge));
  }, [activeNodeId, nodeGap, setEdges, setNodes, toggleSubgraph]);

  /**
   * Tracks user-positioned nodes before delegating to React Flow.
   *
   * @param {GraphNodeChange[]} changes - React Flow node changes.
   * @returns {void}
   */
  /** @type {(changes: GraphNodeChange[]) => void} */
  const handleNodesChange = useCallback((changes) => {
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        savedPositionsRef.current.set(change.id, change.position);
      }
    }
    onNodesChange(changes);
  }, [onNodesChange]);

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
    console.log( 'Parsed DSL:', JSON.stringify(nextDsl, null, 2) );
    
    const nextCollapsedSubgraphs = new Set();
    setDsl(nextDsl);
    setCollapsedSubgraphs(nextCollapsedSubgraphs);
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

  const flow = useMemo(() => h('div', { className: 'flow-wrapper', ref: flowWrapperRef },
    h(ReactFlow, {
      nodes,
      edges,
      nodeTypes,
      onNodesChange: handleNodesChange,
      onEdgesChange,
      //onInit: (instance) => {},
      fitView: true,
      fitViewOptions: { padding: 0.16 },
      minZoom: 0.2,
      maxZoom: 1.5,
      style: { width: '100%', height: '100%' }
    },
    // h(MiniMap, null),
    h(Controls, null),
    h(Background, { gap: 18, size: 1 })
  )), [edges, handleNodesChange, nodes, onEdgesChange]);

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
