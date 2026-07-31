import React, { useCallback, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import * as reactFlowStyles from "bundle-text:@xyflow/react/dist/style.css";
import {
  ReactFlowProvider,
  Background,
  Controls,
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

const h = React.createElement;
const ROOT_PARENT = '__ROOT__';
const DEFAULT_NODE_GAP = 50;
const ROOT_PADDING_X = 120;
const ROOT_PADDING_TOP = 40;
const SUBGRAPH_PADDING_X = 40;
const SUBGRAPH_PADDING_TOP = 64;
const SUBGRAPH_PADDING_BOTTOM = 40;

function parseNodeGap(value) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : DEFAULT_NODE_GAP;
}

function CircleNode({ data }) {
  const kind = data?.kind === 'start' ? 'start' : 'end';
  return h('div', { className: `circle-node ${kind}` },
    h(Handle, { type: 'target', position: Position.Top }),
    h('span', null, data?.label?.replaceAll('_', '') || kind),
    h(Handle, { type: 'source', position: Position.Bottom })
  );
}

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
          data.onToggle();
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

function nodeSize(node, collapsedSubgraphs) {
  const isSubgraph = node.data?.kind === 'subgraph';
  const isBoundary = node.data?.kind === 'start' || node.data?.kind === 'end';
  const layoutSize = node.data?.layoutSize;
  return {
    width: isSubgraph && layoutSize ? layoutSize.width : isSubgraph ? 320 : isBoundary ? 54 : 140,
    height: isSubgraph && layoutSize ? layoutSize.height : isSubgraph ? (collapsedSubgraphs.has(node.id) ? 56 : 300) : isBoundary ? 54 : 48
  };
}

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

function buildParentIndex(nodes) {
  return new Map(nodes.map((node) => [node.id, node.parentId]));
}

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

function isHiddenByCollapsedParent(node, parentIndex, collapsedSubgraphs) {
  return collapsedAncestor(node.id, parentIndex, collapsedSubgraphs) !== null;
}

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

function parentKey(node) {
  return node.parentId || ROOT_PARENT;
}

function startNodeId(parentId) {
  return parentId === ROOT_PARENT ? '__START__' : `${parentId}-__START__`;
}

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

function rankGroupNodes(groupNodes, layoutEdges, groupKey) {
  const ids = new Set(groupNodes.map((node) => node.id));
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
  const ranks = new Map();
  const queue = [startNodeId(groupKey)];
  ranks.set(startNodeId(groupKey), 0);
  while (queue.length > 0) {
    const current = queue.shift();
    const nextRank = (ranks.get(current) || 0) + 1;
    for (const target of acyclicOutgoing.get(current) || []) {
      if (!ranks.has(target) || nextRank > ranks.get(target)) {
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

function removeCycleEdges(outgoing, groupNodes, groupKey) {
  const visiting = new Set();
  const visited = new Set();
  const skippedEdges = new Set();
  const nodeIds = groupNodes.map((node) => node.id);
  const start = startNodeId(groupKey);
  const orderedIds = [
    ...(nodeIds.includes(start) ? [start] : []),
    ...nodeIds.filter((id) => id !== start).sort()
  ];

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

function rankBuckets(groupNodes, layoutEdges, groupKey) {
  const ranks = rankGroupNodes(groupNodes, layoutEdges, groupKey);
  const byRank = new Map();
  for (const node of groupNodes) {
    const rank = ranks.get(node.id) || 0;
    const bucket = byRank.get(rank) || [];
    bucket.push(node);
    byRank.set(rank, bucket);
  }
  return [...byRank.entries()].sort(([left], [right]) => left - right);
}

function nodeLayoutSize(node, collapsedSubgraphs) {
  return nodeSize(node, collapsedSubgraphs);
}

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

function autoLayoutNodes(nodes, layoutEdges, savedPositions, collapsedSubgraphs, nodeGap) {
  const groups = collectLayoutGroups(nodes);
  const nextNodes = [];
  const visitedGroups = new Set();

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


function GraphFlow({ source, activeNodeId, nodeGap }) {
  const [dsl, setDsl] = useState(null);
  const [collapsedSubgraphs, setCollapsedSubgraphs] = useState(new Set());
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const flowWrapperRef = React.useRef(null);
  const savedPositionsRef = React.useRef(new Map());
  const savedSizesRef = React.useRef(new Map());

  const { fitView } = useReactFlow();
      
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

  const applyDsl = useCallback((nextDsl, nextCollapsedSubgraphs) => {
    const parentIndex = buildParentIndex(nextDsl.nodes);
    const graphEdges = rewriteSubgraphBoundaryEdges(nextDsl);
    const visibleNodes = nextDsl.nodes
      .filter((node) => !isHiddenByCollapsedParent(node, parentIndex, nextCollapsedSubgraphs));
    const layoutNodes = autoLayoutNodes(visibleNodes, nextDsl.edges, savedPositionsRef.current, nextCollapsedSubgraphs, nodeGap);
    setNodes(layoutNodes.map((node) => normalizeNode(node, nextCollapsedSubgraphs, toggleSubgraph, savedPositionsRef.current, savedSizesRef.current, activeNodeId)));
    setEdges(visibleEdges(graphEdges, parentIndex, nextCollapsedSubgraphs).map(normalizeEdge));
  }, [activeNodeId, nodeGap, setEdges, setNodes, toggleSubgraph]);

  const handleNodesChange = useCallback((changes) => {
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        savedPositionsRef.current.set(change.id, change.position);
      }
    }
    onNodesChange(changes);
  }, [onNodesChange]);

  const renderDsl = useCallback((value) => {
    const nextDsl = JSON.parse(value);
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

export class LG4JDSLViewElement extends HTMLElement {

  static get observedAttributes() {
    return ['node-gap'];
  }

  constructor() {
    super();
    
    const shadow = this.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = componentStyles();
    this.mount = document.createElement('div');
    this.mount.className = 'mount';
    shadow.append(style, this.mount);

    this.render = this.render.bind(this);
    this.onActive = this.onActive.bind(this);
  }

  attributeChangedCallback() {
    this.update();
  }

  connectedCallback() {

    // mount root
    if( !this.root ) {
      this.root = createRoot(this.mount);
    }

    this.addEventListener('graph', this.render);
    this.addEventListener('graph-active', this.onActive);

  }

  disconnectedCallback() {

    this.removeEventListener('graph', this.render);
    this.removeEventListener('graph-active', this.onActive);

    // unmount root
    this.root?.unmount();
    this.root = null;
  }

  render(event) {
    this.source = event.detail;
    this.update();
  }

  onActive(event) {
    const { detail: { node, subgraphNode } } = event;
    this.activeNodeId = subgraphNode ?? node
    this.update();
  }

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
