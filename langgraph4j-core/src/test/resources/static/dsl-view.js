import React, { useCallback, useMemo, useState } from 'https://esm.sh/react@19';
import { createRoot } from 'https://esm.sh/react-dom@19/client';
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  NodeResizer,
  Position,
  ReactFlow,
  useEdgesState,
  useNodesState
} from 'https://esm.sh/@xyflow/react@12?deps=react@19,react-dom@19';

const h = React.createElement;
const ROOT_PARENT = '__ROOT__';
const ROOT_X_GAP = 190;
const ROOT_Y_GAP = 132;
const CHILD_X_GAP = 170;
const CHILD_Y_GAP = 104;
const SUBGRAPH_PADDING_TOP = 64;

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
  return {
    width: isSubgraph ? 320 : isBoundary ? 54 : 140,
    height: isSubgraph ? (collapsedSubgraphs.has(node.id) ? 56 : 300) : isBoundary ? 54 : 48
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
  return {
    ...node,
    type: node.data?.kind === 'subgraph' ? 'subgraph' : node.data?.kind === 'start' || node.data?.kind === 'end' ? 'circle' : node.type,
    className: active ? [node.className, 'active-node'].filter(Boolean).join(' ') : node.className,
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
      next.push(edge.target);
      outgoing.set(edge.source, next);
    }
  }

  const ranks = new Map();
  const queue = [startNodeId(groupKey)];
  ranks.set(startNodeId(groupKey), 0);
  while (queue.length > 0) {
    const current = queue.shift();
    const nextRank = (ranks.get(current) || 0) + 1;
    for (const target of outgoing.get(current) || []) {
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

function autoLayoutNodes(nodes, layoutEdges, savedPositions) {
  const groups = collectLayoutGroups(nodes);
  const nextNodes = [];
  for (const [groupKey, groupNodes] of groups.entries()) {
    const ranks = rankGroupNodes(groupNodes, layoutEdges, groupKey);
    const byRank = new Map();
    for (const node of groupNodes) {
      const rank = ranks.get(node.id) || 0;
      const bucket = byRank.get(rank) || [];
      bucket.push(node);
      byRank.set(rank, bucket);
    }

    for (const [rank, rankNodes] of byRank.entries()) {
      rankNodes.sort((left, right) => left.id.localeCompare(right.id));
      const xGap = groupKey === ROOT_PARENT ? ROOT_X_GAP : CHILD_X_GAP;
      const yGap = groupKey === ROOT_PARENT ? ROOT_Y_GAP : CHILD_Y_GAP;
      const yBase = groupKey === ROOT_PARENT ? 40 : SUBGRAPH_PADDING_TOP;
      const totalWidth = (rankNodes.length - 1) * xGap;
      rankNodes.forEach((node, index) => {
        const savedPosition = savedPositions.get(node.id);
        nextNodes.push({
          ...node,
          position: savedPosition || {
            x: groupKey === ROOT_PARENT ? 120 + index * xGap - totalWidth / 2 : 40 + index * xGap,
            y: yBase + rank * yGap
          }
        });
      });
    }
  }
  return nextNodes;
}

function App({ source, activeNodeId }) {
  const [dsl, setDsl] = useState(null);
  const [collapsedSubgraphs, setCollapsedSubgraphs] = useState(new Set());
  const savedPositionsRef = React.useRef(new Map());
  const savedSizesRef = React.useRef(new Map());
  const flowRef = React.useRef(null);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const fitView = useCallback(() => {
    // Wait two frames so React Flow has committed and measured async-loaded nodes before fitting.
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        flowRef.current?.fitView({
          padding: 0.16,
          duration: 200
        });
      });
    });
  }, []);

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
    const layoutNodes = autoLayoutNodes(visibleNodes, nextDsl.edges, savedPositionsRef.current);
    setNodes(layoutNodes.map((node) => normalizeNode(node, nextCollapsedSubgraphs, toggleSubgraph, savedPositionsRef.current, savedSizesRef.current, activeNodeId)));
    setEdges(visibleEdges(graphEdges, parentIndex, nextCollapsedSubgraphs).map(normalizeEdge));
  }, [activeNodeId, setEdges, setNodes, toggleSubgraph]);

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
      fitView();
    }
    catch (caught) {
      console.error(caught);
    }
  }, [fitView, renderDsl, source]);

  const flow = useMemo(() => h(ReactFlow, {
    nodes,
    edges,
    nodeTypes,
    onNodesChange: handleNodesChange,
    onEdgesChange,
    onInit: (instance) => {
      flowRef.current = instance;
    },
    fitView: true,
    fitViewOptions: { padding: 0.16 },
    minZoom: 0.2,
    maxZoom: 1.5
  },
    h(MiniMap, null),
    h(Controls, null),
    h(Background, { gap: 18, size: 1 })
  ), [edges, handleNodesChange, nodes, onEdgesChange]);

  return h('main', { className: 'app' },
    h('section', { className: 'graph' }, flow)
  );
}

function componentStyles() {
  return `
    @import url("https://unpkg.com/@xyflow/react@12/dist/style.css");

    :host {
      display: block;
      width: 100%;
      height: 100vh;
      min-height: 100vh;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f6f7f9;
      color: #1f2933;
    }

    * {
      box-sizing: border-box;
    }

    .app {
      width: 100%;
      height: 100vh;
      min-height: 100vh;
      display: block;
    }

    .graph {
      width: 100%;
      height: 100vh;
      min-width: 0;
      min-height: 100vh;
      background: #eef2f7;
    }

    .react-flow__node-default {
      border-radius: 6px;
      border: 1px solid #b8c2d1;
      background: #ffffff;
      color: #1f2933;
      font-size: 12px;
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
      font-size: 11px;
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
      font-size: 12px;
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
  constructor() {
    super();
    this.render = this.render.bind(this);
    this.onActive = this.onActive.bind(this);
  }

  connectedCallback() {
    if (this.root) {
      return;
    }

    const shadow = this.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = componentStyles();
    const mount = document.createElement('div');
    shadow.append(style, mount);

    this.root = createRoot(mount);
    this.addEventListener('graph', this.render);
    this.addEventListener('graph-active', this.onActive);
  }

  disconnectedCallback() {
    this.removeEventListener('graph', this.render);
    this.removeEventListener('graph-active', this.onActive);
    this.root?.unmount();
    this.root = null;
  }

  render(event) {
    this.source = event.detail;
    this.update();
  }

  onActive(event) {
    const detail = event.detail;
    this.activeNodeId = typeof detail === 'string' ? detail : detail?.node;
    this.update();
  }

  update() {
    this.root?.render(h(App, {
      source: this.source,
      activeNodeId: this.activeNodeId
    }));
  }
}

customElements.define('lg4j-dsl-view', LG4JDSLViewElement);
