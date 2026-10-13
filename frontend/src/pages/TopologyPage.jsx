import { useEffect, useState, useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import api from '../api/client';

// ── Placeholder nodes until the topology API is wired ──────────────────────────
const PLACEHOLDER_NODES = [
  { id: 'n1', position: { x: 300, y: 80  }, data: { label: '🖥  node-1 (m5.xlarge)' } },
  { id: 'n2', position: { x: 600, y: 80  }, data: { label: '🖥  node-2 (m5.xlarge)' } },
  { id: 'ns1',position: { x: 200, y: 220 }, data: { label: '📦 namespace: production' } },
  { id: 'ns2',position: { x: 520, y: 220 }, data: { label: '📦 namespace: monitoring' } },
  { id: 'w1', position: { x: 100, y: 360 }, data: { label: '🚀 api-gateway (3 replicas)' } },
  { id: 'w2', position: { x: 330, y: 360 }, data: { label: '🚀 auth-service (2 replicas)' } },
  { id: 'w3', position: { x: 540, y: 360 }, data: { label: '🔍 prometheus (1 replica)' } },
];

const PLACEHOLDER_EDGES = [
  { id: 'e-n1-ns1', source: 'n1', target: 'ns1', animated: true },
  { id: 'e-n2-ns2', source: 'n2', target: 'ns2', animated: true },
  { id: 'e-ns1-w1', source: 'ns1', target: 'w1' },
  { id: 'e-ns1-w2', source: 'ns1', target: 'w2' },
  { id: 'e-ns2-w3', source: 'ns2', target: 'w3' },
];

export default function TopologyPage() {
  const [nodes, setNodes, onNodesChange] = useNodesState(PLACEHOLDER_NODES);
  const [edges, setEdges, onEdgesChange] = useEdgesState(PLACEHOLDER_EDGES);
  const [loading, setLoading] = useState(false);

  const onConnect = useCallback(
    (params) => setEdges((eds) => addEdge(params, eds)),
    [setEdges]
  );

  // Fetch live topology when the API is ready
  useEffect(() => {
    setLoading(true);
    api.get('/api/v1/topology/graph')
      .then(({ data }) => {
        if (data.nodes?.length) {
          setNodes(data.nodes);
          setEdges(data.edges || []);
        }
        // if empty (scaffold), keep placeholders
      })
      .catch(() => { /* keep placeholders if service unreachable */ })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: '0.75rem 1.5rem', borderBottom: '1px solid var(--color-border)',
                    display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)' }}>
          Cluster topology • {nodes.length} nodes • {edges.length} edges
        </span>
        {loading && <span className="badge badge-blue">Syncing…</span>}
      </div>

      <div className="topology-canvas">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
        >
          <Background color="var(--color-border)" gap={24} />
          <Controls />
          <MiniMap
            nodeColor="var(--color-surface-2)"
            maskColor="rgba(13,17,23,0.7)"
          />
        </ReactFlow>
      </div>
    </div>
  );
}
