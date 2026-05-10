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

function normalizeNode(node, collapsedSubgraphs, toggleSubgraph, savedPositions, savedSizes) {
  const size = nodeSize(node, collapsedSubgraphs);
  const savedSize = savedSizes.get(node.id);
  const renderedSize = node.data?.kind === 'subgraph' && savedSize && !collapsedSubgraphs.has(node.id)
    ? savedSize
    : size;
  const savedPosition = savedPositions.get(node.id);
  return {
    ...node,
    type: node.data?.kind === 'subgraph' ? 'subgraph' : node.data?.kind === 'start' || node.data?.kind === 'end' ? 'circle' : node.type,
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

function App({ sampleUrl }) {
  const [source, setSource] = useState('');
  const [status, setStatus] = useState('Load a sample graph or paste DSL JSON.');
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dsl, setDsl] = useState(null);
  const [collapsedSubgraphs, setCollapsedSubgraphs] = useState(new Set());
  const savedPositionsRef = React.useRef(new Map());
  const savedSizesRef = React.useRef(new Map());
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const graphReady = nodes.length > 0;

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
    setNodes(layoutNodes.map((node) => normalizeNode(node, nextCollapsedSubgraphs, toggleSubgraph, savedPositionsRef.current, savedSizesRef.current)));
    setEdges(visibleEdges(graphEdges, parentIndex, nextCollapsedSubgraphs).map(normalizeEdge));
    setStatus(`${nextDsl.nodes.length} nodes, ${nextDsl.edges.length} edges, ${nextDsl.subgraphs?.length || 0} subgraphs`);
    setError(false);
  }, [setEdges, setNodes, toggleSubgraph]);

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

  const loadSample = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(sampleUrl);
      if (!response.ok) {
        throw new Error(`Sample graph request failed: ${response.status}`);
      }
      const text = await response.text();
      setSource(JSON.stringify(JSON.parse(text), null, 2));
      renderDsl(text);
    }
    catch (caught) {
      setStatus(caught.message);
      setError(true);
    }
    finally {
      setLoading(false);
    }
  }, [renderDsl, sampleUrl]);

  const renderPasted = useCallback(() => {
    try {
      renderDsl(source);
    }
    catch (caught) {
      setStatus(caught.message);
      setError(true);
    }
  }, [renderDsl, source]);

  const flow = useMemo(() => h(ReactFlow, {
    nodes,
    edges,
    nodeTypes,
    onNodesChange: handleNodesChange,
    onEdgesChange,
    fitView: graphReady,
    minZoom: 0.2,
    maxZoom: 1.5
  },
    h(MiniMap, null),
    h(Controls, null),
    h(Background, { gap: 18, size: 1 })
  ), [edges, graphReady, handleNodesChange, nodes, onEdgesChange]);

  return h('main', { className: 'app' },
    h('section', { className: 'sidebar' },
      h('div', { className: 'header' },
        h('h1', null, 'Langgraph4j DSL Visualizer'),
        h('p', null, 'Load a Langgraph4j JSON DSL document and inspect it as an interactive React Flow graph.')
      ),
      h('div', { className: 'actions' },
        h('button', { className: 'button primary', onClick: loadSample, disabled: loading }, loading ? 'Loading...' : 'Load sample'),
        h('button', { className: 'button', onClick: renderPasted, disabled: loading || source.trim().length === 0 }, 'Render JSON')
      ),
      h('textarea', {
        className: 'editor',
        value: source,
        onChange: (event) => setSource(event.target.value),
        spellCheck: 'false',
        placeholder: 'Paste Langgraph4j DSL JSON here...'
      }),
      h('div', { className: error ? 'status error' : 'status' }, status)
    ),
    h('section', { className: 'graph' }, flow)
  );
}

function componentStyles() {
  return `
    @import url("https://unpkg.com/@xyflow/react@12/dist/style.css");

    :host {
      display: block;
      min-height: 100vh;
      color-scheme: light;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f6f7f9;
      color: #1f2933;
    }

    * {
      box-sizing: border-box;
    }

    button, textarea {
      font: inherit;
    }

    .app {
      min-height: 100vh;
      display: grid;
      grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
    }

    .sidebar {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px;
      border-right: 1px solid #d8dde5;
      background: #ffffff;
    }

    .header h1 {
      margin: 0;
      font-size: 20px;
      line-height: 1.2;
      font-weight: 700;
    }

    .header p {
      margin: 6px 0 0;
      color: #5c6675;
      font-size: 13px;
      line-height: 1.45;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .button {
      border: 1px solid #c9d1dc;
      border-radius: 6px;
      background: #ffffff;
      color: #1f2933;
      min-height: 36px;
      padding: 0 12px;
      cursor: pointer;
    }

    .button.primary {
      border-color: #2563eb;
      background: #2563eb;
      color: #ffffff;
    }

    .button:disabled {
      cursor: progress;
      opacity: 0.65;
    }

    .editor {
      flex: 1;
      min-height: 260px;
      resize: none;
      border: 1px solid #c9d1dc;
      border-radius: 6px;
      padding: 12px;
      color: #17212f;
      background: #fbfcfd;
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
      font-size: 12px;
      line-height: 1.5;
    }

    .status {
      min-height: 22px;
      color: #52606d;
      font-size: 13px;
    }

    .status.error {
      color: #b42318;
    }

    .graph {
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

    @media (max-width: 820px) {
      .app {
        grid-template-columns: 1fr;
        grid-template-rows: minmax(360px, 46vh) minmax(420px, 54vh);
      }

      .sidebar {
        border-right: 0;
        border-bottom: 1px solid #d8dde5;
      }

      .graph {
        min-height: 420px;
      }
    }
  `;
}

export class LG4JDSLViewElement extends HTMLElement {
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
    this.root.render(h(App, { sampleUrl: this.getAttribute('api-url') || '/api/graph' }));
  }

  disconnectedCallback() {
    this.root?.unmount();
    this.root = null;
  }
}

customElements.define('lg4j-dsl-view', LG4JDSLViewElement);
