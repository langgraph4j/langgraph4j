import { useCallback } from 'react';
import {
    Background,
    ReactFlow,
    addEdge,
    ConnectionLineType,
    Panel,
    useNodesState,
    useEdgesState,
} from '@xyflow/react';
import dagre from '@dagrejs/dagre';
import './index.css';

import { initialNodes, initialEdges } from './initialElements';

const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));

const nodeWidth = 172;
const nodeHeight = 36;

const getLayoutedElements = (nodes, edges, direction = 'TB') => {
    const isHorizontal = direction === 'LR';
    dagreGraph.setGraph({ rankdir: direction });

    nodes.forEach((node) => {
        dagreGraph.setNode(node.id, { width: nodeWidth, height: nodeHeight });
    });

    edges.forEach((edge) => {
        dagreGraph.setEdge(edge.source, edge.target);
    });

    dagre.layout(dagreGraph);

    const newNodes = nodes.map((node) => {
        const nodeWithPosition = dagreGraph.node(node.id);
        const newNode = {
            ...node,
            targetPosition: isHorizontal ? 'left' : 'top',
            sourcePosition: isHorizontal ? 'right' : 'bottom',
            // We are shifting the dagre node position (anchor=center center) to the top left
            // so it matches the React Flow node anchor point (top left).
            position: {
                x: nodeWithPosition.x - nodeWidth / 2,
                y: nodeWithPosition.y - nodeHeight / 2,
            },
        };

        return newNode;
    });

    return { nodes: newNodes, edges };
};

const { nodes: layoutedNodes, edges: layoutedEdges } = getLayoutedElements(
    initialNodes,
    initialEdges,
);

const Flow = () => {
    const [nodes, setNodes, onNodesChange] = useNodesState(layoutedNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(layoutedEdges);

    const onConnect = useCallback(
        (params) =>
            setEdges((eds) =>
                addEdge({ ...params, type: ConnectionLineType.SmoothStep, animated: true }, eds),
            ),
        [],
    );
    const onLayout = useCallback(
        (direction) => {
            const { nodes: layoutedNodes, edges: layoutedEdges } = getLayoutedElements(
                nodes,
                edges,
                direction,
            );

            setNodes([...layoutedNodes]);
            setEdges([...layoutedEdges]);
        },
        [nodes, edges],
    );

    return (
        <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            connectionLineType={ConnectionLineType.SmoothStep}
            fitView
            colorMode="system"
        >
            <Panel position="top-right">
                <button className="xy-theme__button" onClick={() => onLayout('TB')}>
                    vertical layout
                </button>
                <button className="xy-theme__button" onClick={() => onLayout('LR')}>
                    horizontal layout
                </button>
            </Panel>
            <Background />
        </ReactFlow>
    );
};

export function App() {
    return <Flow />;
}
