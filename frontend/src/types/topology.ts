export type NodeHealth = 'HEALTHY' | 'DEGRADED' | 'CRITICAL';
export type NodeType = 'NODE' | 'POD' | 'DEPLOYMENT' | 'SERVICE' | 'INGRESS';
export type EdgeType = 'HIERARCHY' | 'DEPENDENCY' | 'INGRESS';
export type RiskScore = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface TopologyNodeDetails {
  // Pod details
  cpuRequests?: string;
  cpuLimits?: string;
  memoryRequests?: string;
  memoryLimits?: string;
  restarts?: number;
  phase?: string;
  nodeName?: string;
  ownerKind?: string;
  ownerName?: string;

  // Node details
  allocatableCpu?: string;
  allocatableMemory?: string;
  capacityCpu?: string;
  capacityMemory?: string;
  ready?: boolean;
  status?: string;

  // Deployment details
  replicas?: number;
  readyReplicas?: number;
  availableReplicas?: number;
  selector?: Record<string, string>;

  // Service details
  serviceType?: string;
  clusterIp?: string;
  ports?: Array<{ port: number; targetPort?: number | string; protocol?: string; name?: string }>;

  [key: string]: unknown;
}

export interface BackendTopologyNode {
  id: string;
  name: string;
  type: NodeType;
  namespace?: string;
  health: NodeHealth;
  healthColor: string; // e.g. "#10b981", "#f59e0b", "#ef4444"
  details?: TopologyNodeDetails;
  labels?: Record<string, string>;
}

export interface BackendTopologyEdge {
  id: string;
  source: string;
  target: string;
  type: EdgeType;
  label?: string;
  animated?: boolean;
}

export interface ClusterHealthSummary {
  healthyCount: number;
  degradedCount: number;
  criticalCount: number;
  totalCount: number;
  overallStatus: NodeHealth;
}

export interface StalenessInfo {
  isStale: boolean;
  lagSeconds: number;
  lastSyncedAt: string;
  syncStatus: string;
}

export interface TopologyResponse {
  nodes: BackendTopologyNode[];
  edges: BackendTopologyEdge[];
  health: ClusterHealthSummary;
  staleness: StalenessInfo;
}

export interface BlastRadiusResponse {
  target: BackendTopologyNode;
  upstream: BackendTopologyNode[];
  downstream: BackendTopologyNode[];
  impactedNodeIds: string[];
  impactedEdgeIds: string[];
  totalImpacted: number;
  riskScore: RiskScore;
  explanation: string;
}

export type BlastRole = 'target' | 'upstream' | 'downstream' | 'dimmed' | 'normal';

export interface CustomNodeData {
  id: string;
  name: string;
  type: NodeType;
  namespace?: string;
  health: NodeHealth;
  healthColor: string;
  details?: TopologyNodeDetails;
  labels?: Record<string, string>;
  blastRole?: BlastRole;
  isSelected?: boolean;
  onAnalyzeBlast?: (nodeName: string) => void;
  [key: string]: unknown;
}
