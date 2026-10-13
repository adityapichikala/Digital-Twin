import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Node,
  Edge,
  NodeMouseHandler,
  ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import api from '../api/client';
import {
  BackendTopologyNode,
  BackendTopologyEdge,
  TopologyResponse,
  BlastRadiusResponse,
  CustomNodeData,
  BlastRole,
} from '../types/topology';
import CustomNode from '../components/topology/CustomNode';
import NodeDetailPanel from '../components/topology/NodeDetailPanel';
import StalenessBanner from '../components/topology/StalenessBanner';
import BlastRadiusPanel from '../components/topology/BlastRadiusPanel';
import TopologyToolbar from '../components/topology/TopologyToolbar';
import { getLayoutedElements, LayoutDirection } from '../components/topology/layoutUtils';

// Fallback seed topology for offline or loading state
const FALLBACK_SEED_NODES: BackendTopologyNode[] = [
  {
    id: 'node:ip-10-0-1-10.ec2.internal',
    name: 'ip-10-0-1-10.ec2.internal',
    type: 'NODE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { allocatableCpu: '1930m', allocatableMemory: '3.8Gi', capacityCpu: '2000m', capacityMemory: '4.0Gi', ready: true, status: 'Ready' },
  },
  {
    id: 'node:ip-10-0-2-20.ec2.internal',
    name: 'ip-10-0-2-20.ec2.internal',
    type: 'NODE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { allocatableCpu: '1930m', allocatableMemory: '3.8Gi', capacityCpu: '2000m', capacityMemory: '4.0Gi', ready: true, status: 'Ready' },
  },
  {
    id: 'service:twindigital/gateway',
    name: 'gateway',
    namespace: 'twindigital',
    type: 'SERVICE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { serviceType: 'LoadBalancer', clusterIp: '172.20.10.1', ports: [{ port: 80, targetPort: 3000 }] },
  },
  {
    id: 'service:twindigital/auth-service',
    name: 'auth-service',
    namespace: 'twindigital',
    type: 'SERVICE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { serviceType: 'ClusterIP', clusterIp: '172.20.10.2', ports: [{ port: 8081 }] },
  },
  {
    id: 'service:twindigital/cluster-sync-service',
    name: 'cluster-sync-service',
    namespace: 'twindigital',
    type: 'SERVICE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { serviceType: 'ClusterIP', clusterIp: '172.20.10.3', ports: [{ port: 8082 }] },
  },
  {
    id: 'service:twindigital/topology-service',
    name: 'topology-service',
    namespace: 'twindigital',
    type: 'SERVICE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { serviceType: 'ClusterIP', clusterIp: '172.20.10.4', ports: [{ port: 8083 }] },
  },
  {
    id: 'service:twindigital/simulation-service',
    name: 'simulation-service',
    namespace: 'twindigital',
    type: 'SERVICE',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { serviceType: 'ClusterIP', clusterIp: '172.20.10.5', ports: [{ port: 8084 }] },
  },
  {
    id: 'deployment:twindigital/auth-service',
    name: 'auth-service',
    namespace: 'twindigital',
    type: 'DEPLOYMENT',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { replicas: 2, readyReplicas: 2, availableReplicas: 2 },
  },
  {
    id: 'deployment:twindigital/topology-service',
    name: 'topology-service',
    namespace: 'twindigital',
    type: 'DEPLOYMENT',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { replicas: 2, readyReplicas: 2, availableReplicas: 2 },
  },
  {
    id: 'pod:twindigital/auth-service-pod-1',
    name: 'auth-service-784f4f-xk92m',
    namespace: 'twindigital',
    type: 'POD',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { cpuRequests: '100m', cpuLimits: '500m', memoryRequests: '256Mi', memoryLimits: '512Mi', restarts: 0, phase: 'Running', nodeName: 'ip-10-0-1-10.ec2.internal' },
  },
  {
    id: 'pod:twindigital/topology-service-pod-1',
    name: 'topology-service-59bc6d-pl92x',
    namespace: 'twindigital',
    type: 'POD',
    health: 'HEALTHY',
    healthColor: '#10b981',
    details: { cpuRequests: '150m', cpuLimits: '600m', memoryRequests: '384Mi', memoryLimits: '768Mi', restarts: 0, phase: 'Running', nodeName: 'ip-10-0-2-20.ec2.internal' },
  },
];

const FALLBACK_SEED_EDGES: BackendTopologyEdge[] = [
  { id: 'e:gw-auth', source: 'service:twindigital/gateway', target: 'service:twindigital/auth-service', type: 'INGRESS', label: 'verifies JWT', animated: true },
  { id: 'e:gw-topo', source: 'service:twindigital/gateway', target: 'service:twindigital/topology-service', type: 'INGRESS', label: 'routes /topology', animated: true },
  { id: 'e:gw-sim', source: 'service:twindigital/gateway', target: 'service:twindigital/simulation-service', type: 'INGRESS', label: 'routes /simulation', animated: true },
  { id: 'e:topo-sync', source: 'service:twindigital/topology-service', target: 'service:twindigital/cluster-sync-service', type: 'DEPENDENCY', label: 'depends on', animated: true },
  { id: 'e:sim-topo', source: 'service:twindigital/simulation-service', target: 'service:twindigital/topology-service', type: 'DEPENDENCY', label: 'depends on', animated: true },
  { id: 'e:svc-dep-auth', source: 'service:twindigital/auth-service', target: 'deployment:twindigital/auth-service', type: 'HIERARCHY', label: 'routes to' },
  { id: 'e:svc-dep-topo', source: 'service:twindigital/topology-service', target: 'deployment:twindigital/topology-service', type: 'HIERARCHY', label: 'routes to' },
  { id: 'e:dep-pod-auth', source: 'deployment:twindigital/auth-service', target: 'pod:twindigital/auth-service-pod-1', type: 'HIERARCHY', label: 'manages' },
  { id: 'e:dep-pod-topo', source: 'deployment:twindigital/topology-service', target: 'pod:twindigital/topology-service-pod-1', type: 'HIERARCHY', label: 'manages' },
  { id: 'e:node-pod-1', source: 'node:ip-10-0-1-10.ec2.internal', target: 'pod:twindigital/auth-service-pod-1', type: 'HIERARCHY', label: 'runs on' },
  { id: 'e:node-pod-2', source: 'node:ip-10-0-2-20.ec2.internal', target: 'pod:twindigital/topology-service-pod-1', type: 'HIERARCHY', label: 'runs on' },
];

export default function TopologyPage() {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<CustomNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // Raw fetched data
  const [rawNodes, setRawNodes] = useState<BackendTopologyNode[]>(FALLBACK_SEED_NODES);
  const [rawEdges, setRawEdges] = useState<BackendTopologyEdge[]>(FALLBACK_SEED_EDGES);
  const [healthSummary, setHealthSummary] = useState<TopologyResponse['health']>({
    healthyCount: 11,
    degradedCount: 0,
    criticalCount: 0,
    totalCount: 11,
    overallStatus: 'HEALTHY',
  });
  const [staleness, setStaleness] = useState<TopologyResponse['staleness']>({
    isStale: false,
    lagSeconds: 0,
    lastSyncedAt: '2026-10-14T00:00:00Z',
    syncStatus: 'HEALTHY',
  });

  // UI state
  const [loading, setLoading] = useState<boolean>(false);
  const [autoRefresh, setAutoRefresh] = useState<boolean>(true);
  const [direction, setDirection] = useState<LayoutDirection>('TB');
  const [selectedNode, setSelectedNode] = useState<BackendTopologyNode | null>(null);
  const [blastData, setBlastData] = useState<BlastRadiusResponse | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');

  const rfInstance = useRef<ReactFlowInstance | null>(null);
  const nodeTypes = useMemo(() => ({ custom: CustomNode }), []);

  // Fetch topology from backend (trying /api/topology or /api/v1/topology)
  const fetchTopology = useCallback(async (isBackground = false) => {
    if (!isBackground) setLoading(true);
    try {
      let res;
      try {
        res = await api.get<TopologyResponse>('/api/topology');
      } catch {
        res = await api.get<TopologyResponse>('/api/v1/topology');
      }

      if (res?.data && res.data.nodes?.length > 0) {
        setRawNodes(res.data.nodes);
        setRawEdges(res.data.edges || []);
        if (res.data.health) setHealthSummary(res.data.health);
        if (res.data.staleness) setStaleness(res.data.staleness);
      }
    } catch (err) {
      console.warn('Unable to load live topology, retaining cached data:', err);
    } finally {
      if (!isBackground) setLoading(false);
    }
  }, []);

  // Fetch blast radius for a target service
  const handleAnalyzeBlastRadius = useCallback(async (targetServiceName: string) => {
    if (!targetServiceName) return;
    try {
      let res;
      try {
        res = await api.get<BlastRadiusResponse>(`/api/topology/blast-radius?target=${encodeURIComponent(targetServiceName)}`);
      } catch {
        res = await api.get<BlastRadiusResponse>(`/api/v1/topology/blast-radius?target=${encodeURIComponent(targetServiceName)}`);
      }

      if (res?.data) {
        setBlastData(res.data);
      }
    } catch (err) {
      console.error('Failed to calculate blast radius:', err);
    }
  }, []);

  const handleClearBlastRadius = useCallback(() => {
    setBlastData(null);
  }, []);

  // Auto-refresh via 5s polling interval
  useEffect(() => {
    fetchTopology(false);

    if (!autoRefresh) return;

    const interval = setInterval(() => {
      fetchTopology(true);
    }, 5000);

    return () => clearInterval(interval);
  }, [autoRefresh, fetchTopology]);

  // Construct Flow elements when raw data, blastData, search, or filters change
  useEffect(() => {
    // Determine blast impact sets
    const impactedNodeIds = new Set(blastData?.impactedNodeIds || []);
    const impactedEdgeIds = new Set(blastData?.impactedEdgeIds || []);
    const targetNodeId = blastData?.target?.id;
    const upstreamIds = new Set((blastData?.upstream || []).map((n) => n.id));
    const downstreamIds = new Set((blastData?.downstream || []).map((n) => n.id));

    // Filter nodes by search and type
    const query = searchQuery.trim().toLowerCase();
    const filteredRawNodes = rawNodes.filter((n) => {
      if (typeFilter !== 'ALL' && n.type !== typeFilter) return false;
      if (query && !n.name.toLowerCase().includes(query) && !n.id.toLowerCase().includes(query)) {
        return false;
      }
      return true;
    });

    const activeNodeIds = new Set(filteredRawNodes.map((n) => n.id));

    // Convert raw nodes to ReactFlow nodes
    const flowNodes: Node<CustomNodeData>[] = filteredRawNodes.map((node) => {
      let blastRole: BlastRole = 'normal';
      if (blastData) {
        if (node.id === targetNodeId || node.name === blastData.target?.name) {
          blastRole = 'target';
        } else if (upstreamIds.has(node.id)) {
          blastRole = 'upstream';
        } else if (downstreamIds.has(node.id)) {
          blastRole = 'downstream';
        } else if (impactedNodeIds.has(node.id)) {
          blastRole = 'upstream';
        } else {
          blastRole = 'dimmed';
        }
      }

      return {
        id: node.id,
        type: 'custom',
        position: { x: 0, y: 0 }, // will be set by dagre
        data: {
          id: node.id,
          name: node.name,
          type: node.type,
          namespace: node.namespace,
          health: node.health,
          healthColor: node.healthColor,
          details: node.details,
          labels: node.labels,
          blastRole,
          isSelected: selectedNode?.id === node.id,
          onAnalyzeBlast: handleAnalyzeBlastRadius,
        },
      };
    });

    // Convert raw edges to ReactFlow edges
    const flowEdges: Edge[] = rawEdges
      .filter((e) => activeNodeIds.has(e.source) && activeNodeIds.has(e.target))
      .map((edge) => {
        const isImpacted = blastData && (impactedEdgeIds.has(edge.id) || (impactedNodeIds.has(edge.source) && impactedNodeIds.has(edge.target)));
        const isDimmed = blastData && !isImpacted;

        let edgeStroke = '#30363d';
        let strokeWidth = 1.5;
        let animated = edge.animated ?? false;

        if (edge.type === 'DEPENDENCY') {
          edgeStroke = '#38bdf8';
          animated = true;
        } else if (edge.type === 'INGRESS') {
          edgeStroke = '#a855f7';
          animated = true;
        }

        if (isImpacted) {
          edgeStroke = '#f59e0b';
          strokeWidth = 3;
          animated = true;
        }

        return {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          type: 'smoothstep',
          animated,
          label: edge.label,
          style: {
            stroke: edgeStroke,
            strokeWidth,
            opacity: isDimmed ? 0.15 : 1,
            transition: 'all 0.3s ease',
          },
          labelStyle: {
            fill: isDimmed ? '#484f58' : '#8b949e',
            fontSize: 10,
            fontWeight: 500,
          },
          labelBgStyle: {
            fill: '#161b22',
            fillOpacity: 0.85,
          },
        };
      });

    // Run Dagre Layout
    const layouted = getLayoutedElements(flowNodes, flowEdges, {
      direction,
      nodeWidth: 260,
      nodeHeight: 110,
      rankSep: 85,
      nodeSep: 55,
    });

    setNodes(layouted.nodes);
    setEdges(layouted.edges);
  }, [rawNodes, rawEdges, direction, blastData, selectedNode, searchQuery, typeFilter, handleAnalyzeBlastRadius, setNodes, setEdges]);

  // Click on node selects it and opens details panel
  const onNodeClick: NodeMouseHandler = useCallback(
    (_, node) => {
      const match = rawNodes.find((n) => n.id === node.id);
      if (match) {
        setSelectedNode(match);
      }
    },
    [rawNodes]
  );

  const handleSelectNodeById = useCallback(
    (nodeId: string) => {
      const match = rawNodes.find((n) => n.id === nodeId);
      if (match) {
        setSelectedNode(match);
        if (rfInstance.current) {
          const flowNode = nodes.find((n) => n.id === nodeId);
          if (flowNode) {
            rfInstance.current.setCenter(flowNode.position.x + 130, flowNode.position.y + 55, {
              zoom: 1.1,
              duration: 600,
            });
          }
        }
      }
    },
    [rawNodes, nodes]
  );

  return (
    <div className="topology-page-container">
      {/* Staleness Banner at top if sync lag detected */}
      <StalenessBanner
        staleness={staleness}
        onRefresh={() => fetchTopology(false)}
        refreshing={loading}
      />

      {/* Main Toolbar */}
      <TopologyToolbar
        healthSummary={healthSummary}
        autoRefresh={autoRefresh}
        onToggleAutoRefresh={() => setAutoRefresh((prev) => !prev)}
        onManualRefresh={() => fetchTopology(false)}
        loading={loading}
        direction={direction}
        onChangeDirection={setDirection}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        selectedType={typeFilter}
        onTypeFilterChange={setTypeFilter}
      />

      {/* Canvas workspace */}
      <div className="topology-canvas-wrapper">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          onInit={(instance) => {
            rfInstance.current = instance;
            instance.fitView({ padding: 0.2 });
          }}
          minZoom={0.2}
          maxZoom={2}
          fitView
        >
          <Background color="#30363d" gap={24} size={1} />
          <Controls position="bottom-left" showInteractive={false} />
          <MiniMap
            nodeColor={(n) => {
              const nd = n.data as CustomNodeData;
              return nd?.healthColor || '#58a6ff';
            }}
            maskColor="rgba(13, 17, 23, 0.75)"
            position="bottom-right"
            style={{ backgroundColor: '#161b22', border: '1px solid #30363d', borderRadius: 8 }}
          />
        </ReactFlow>

        {/* Floating Blast Radius Analysis Panel */}
        <BlastRadiusPanel
          blastData={blastData}
          onClear={handleClearBlastRadius}
          onSelectNode={handleSelectNodeById}
        />

        {/* Side Panel for Node Details */}
        <NodeDetailPanel
          node={selectedNode}
          onClose={() => setSelectedNode(null)}
          onAnalyzeBlast={handleAnalyzeBlastRadius}
        />
      </div>
    </div>
  );
}
