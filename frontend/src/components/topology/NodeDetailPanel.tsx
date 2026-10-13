import React from 'react';
import {
  X,
  Server,
  Layers,
  Globe,
  Cpu,
  HardDrive,
  RotateCcw,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Crosshair,
  Tag,
  Activity,
} from 'lucide-react';
import { BackendTopologyNode } from '../../types/topology';

interface NodeDetailPanelProps {
  node: BackendTopologyNode | null;
  onClose: () => void;
  onAnalyzeBlast?: (serviceName: string) => void;
}

export const NodeDetailPanel: React.FC<NodeDetailPanelProps> = ({
  node,
  onClose,
  onAnalyzeBlast,
}) => {
  if (!node) return null;

  const { name, type, namespace, health, healthColor, details = {}, labels = {} } = node;

  function renderHealthIcon() {
    if (health === 'CRITICAL') return <XCircle size={16} color="#ef4444" />;
    if (health === 'DEGRADED') return <AlertTriangle size={16} color="#f59e0b" />;
    return <CheckCircle size={16} color="#10b981" />;
  }

  return (
    <div className="node-detail-panel animate-slide-in">
      {/* Header */}
      <div className="panel-header">
        <div className="panel-title-group">
          <div className="panel-badge-type">{type}</div>
          <h3 className="panel-node-title" title={name}>
            {name}
          </h3>
          {namespace && <div className="panel-namespace">namespace: <span>{namespace}</span></div>}
        </div>
        <button className="panel-close-btn" onClick={onClose} title="Close panel">
          <X size={18} />
        </button>
      </div>

      {/* Health status banner */}
      <div
        className="panel-health-strip"
        style={{
          borderLeftColor: healthColor,
          backgroundColor:
            health === 'CRITICAL'
              ? 'rgba(239, 68, 68, 0.1)'
              : health === 'DEGRADED'
              ? 'rgba(245, 158, 11, 0.1)'
              : 'rgba(16, 185, 129, 0.1)',
        }}
      >
        <div className="panel-health-info">
          {renderHealthIcon()}
          <div>
            <div className="panel-health-status" style={{ color: healthColor }}>
              {health}
            </div>
            <div className="panel-health-hint">
              {health === 'CRITICAL'
                ? 'High restarts or missing replicas detected'
                : health === 'DEGRADED'
                ? 'Sub-optimal capacity or recent restart observed'
                : 'All health checks passing nominally'}
            </div>
          </div>
        </div>

        {type === 'SERVICE' && onAnalyzeBlast && (
          <button
            className="btn btn-primary btn-sm"
            onClick={() => onAnalyzeBlast(name)}
            title="Inspect blast radius of this service"
          >
            <Crosshair size={14} /> Blast Radius
          </button>
        )}
      </div>

      {/* Body content based on Node Type */}
      <div className="panel-body">
        {/* POD METRICS: Requests, Limits, Restarts */}
        {type === 'POD' && (
          <div className="panel-section">
            <h4 className="section-title">
              <Activity size={14} /> Resource Requests & Limits
            </h4>
            <div className="metrics-grid">
              <div className="metric-box">
                <div className="metric-label">
                  <Cpu size={13} /> CPU Request
                </div>
                <div className="metric-val">{details.cpuRequests || 'Not set'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">
                  <Cpu size={13} /> CPU Limit
                </div>
                <div className="metric-val">{details.cpuLimits || 'Not set'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">
                  <HardDrive size={13} /> Memory Request
                </div>
                <div className="metric-val">{details.memoryRequests || 'Not set'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">
                  <HardDrive size={13} /> Memory Limit
                </div>
                <div className="metric-val">{details.memoryLimits || 'Not set'}</div>
              </div>
            </div>

            <h4 className="section-title" style={{ marginTop: '1.25rem' }}>
              <RotateCcw size={14} /> Pod Runtime Status
            </h4>
            <div className="spec-table">
              <div className="spec-row">
                <span className="spec-key">Container Restarts</span>
                <span
                  className={`spec-val ${
                    Number(details.restarts || 0) > 0 ? 'text-amber-400 font-bold' : ''
                  }`}
                >
                  {details.restarts ?? 0}
                </span>
              </div>
              <div className="spec-row">
                <span className="spec-key">Phase</span>
                <span className="spec-val">{details.phase || 'Running'}</span>
              </div>
              <div className="spec-row">
                <span className="spec-key">Scheduled Node</span>
                <span className="spec-val font-mono">{details.nodeName || 'N/A'}</span>
              </div>
              <div className="spec-row">
                <span className="spec-key">Controller Owner</span>
                <span className="spec-val">
                  {details.ownerKind ? `${details.ownerKind} / ${details.ownerName}` : 'None'}
                </span>
              </div>
            </div>
          </div>
        )}

        {/* NODE METRICS: Allocatable vs Capacity */}
        {type === 'NODE' && (
          <div className="panel-section">
            <h4 className="section-title">
              <Server size={14} /> Node Capacity & Allocatable
            </h4>
            <div className="metrics-grid">
              <div className="metric-box">
                <div className="metric-label">Allocatable CPU</div>
                <div className="metric-val">{details.allocatableCpu || '-'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">Allocatable RAM</div>
                <div className="metric-val">{details.allocatableMemory || '-'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">Total CPU Capacity</div>
                <div className="metric-val">{details.capacityCpu || '-'}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">Total RAM Capacity</div>
                <div className="metric-val">{details.capacityMemory || '-'}</div>
              </div>
            </div>
            <div className="spec-table" style={{ marginTop: '1rem' }}>
              <div className="spec-row">
                <span className="spec-key">Kubelet Condition</span>
                <span className="spec-val text-emerald-400">
                  {details.ready ? 'Ready (KubeletHealthy)' : 'NotReady'}
                </span>
              </div>
              <div className="spec-row">
                <span className="spec-key">Status</span>
                <span className="spec-val">{details.status || 'Ready'}</span>
              </div>
            </div>
          </div>
        )}

        {/* DEPLOYMENT METRICS: Replicas */}
        {type === 'DEPLOYMENT' && (
          <div className="panel-section">
            <h4 className="section-title">
              <Layers size={14} /> Replica Set Status
            </h4>
            <div className="metrics-grid">
              <div className="metric-box">
                <div className="metric-label">Desired Replicas</div>
                <div className="metric-val">{details.replicas ?? 0}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">Ready Replicas</div>
                <div className="metric-val">{details.readyReplicas ?? 0}</div>
              </div>
              <div className="metric-box">
                <div className="metric-label">Available Replicas</div>
                <div className="metric-val">{details.availableReplicas ?? 0}</div>
              </div>
            </div>
          </div>
        )}

        {/* SERVICE METRICS: Ports & IPs */}
        {type === 'SERVICE' && (
          <div className="panel-section">
            <h4 className="section-title">
              <Globe size={14} /> Service Networking
            </h4>
            <div className="spec-table">
              <div className="spec-row">
                <span className="spec-key">Service Type</span>
                <span className="spec-val">{details.serviceType || 'ClusterIP'}</span>
              </div>
              <div className="spec-row">
                <span className="spec-key">Cluster IP</span>
                <span className="spec-val font-mono">{details.clusterIp || 'None'}</span>
              </div>
            </div>

            {Array.isArray(details.ports) && details.ports.length > 0 && (
              <div style={{ marginTop: '1rem' }}>
                <div className="sub-heading">Exposed Ports</div>
                <div className="ports-pill-group">
                  {details.ports.map((p, idx) => (
                    <span key={idx} className="port-pill">
                      {p.name ? `${p.name}: ` : ''}
                      {p.port}
                      {p.targetPort ? ` → ${p.targetPort}` : ''}
                      {p.protocol ? `/${p.protocol}` : ''}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Labels Section */}
        {Object.keys(labels).length > 0 && (
          <div className="panel-section">
            <h4 className="section-title">
              <Tag size={14} /> Kubernetes Labels & Annotations
            </h4>
            <div className="labels-wrap">
              {Object.entries(labels).map(([k, v]) => (
                <div key={k} className="label-item" title={`${k}=${v}`}>
                  <span className="label-key">{k}</span>
                  <span className="label-val">{v}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default NodeDetailPanel;
