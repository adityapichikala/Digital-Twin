import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import {
  Server,
  Box,
  Layers,
  Globe,
  ArrowRightCircle,
  RotateCcw,
  Radio,
  Crosshair,
} from 'lucide-react';
import { CustomNodeData, NodeType } from '../../types/topology';

function getTypeIcon(type: NodeType) {
  switch (type) {
    case 'NODE':
      return <Server size={14} className="node-type-icon text-cyan-400" />;
    case 'DEPLOYMENT':
      return <Layers size={14} className="node-type-icon text-indigo-400" />;
    case 'POD':
      return <Box size={14} className="node-type-icon text-emerald-400" />;
    case 'SERVICE':
      return <Globe size={14} className="node-type-icon text-sky-400" />;
    case 'INGRESS':
      return <ArrowRightCircle size={14} className="node-type-icon text-purple-400" />;
    default:
      return <Radio size={14} className="node-type-icon" />;
  }
}

function getHealthBadge(health: string) {
  switch (health) {
    case 'CRITICAL':
      return { bg: 'rgba(239, 68, 68, 0.15)', text: '#ef4444', border: 'rgba(239, 68, 68, 0.4)', label: 'CRITICAL' };
    case 'DEGRADED':
      return { bg: 'rgba(245, 158, 11, 0.15)', text: '#f59e0b', border: 'rgba(245, 158, 11, 0.4)', label: 'DEGRADED' };
    default:
      return { bg: 'rgba(16, 185, 129, 0.15)', text: '#10b981', border: 'rgba(16, 185, 129, 0.4)', label: 'HEALTHY' };
  }
}

export const CustomNode = memo(({ data, isConnectable, targetPosition, sourcePosition }: NodeProps<any>) => {
  const nodeData = data as CustomNodeData;
  const {
    name,
    type,
    namespace,
    health = 'HEALTHY',
    healthColor = '#10b981',
    details = {},
    blastRole = 'normal',
    isSelected = false,
    onAnalyzeBlast,
  } = nodeData;

  const healthStyle = getHealthBadge(health);

  // Derive styling based on blast-radius role
  let containerClass = 'topology-node-card';
  let blastBadge = null;

  if (blastRole === 'target') {
    containerClass += ' blast-target';
    blastBadge = <span className="blast-role-pill blast-target-pill">TARGET</span>;
  } else if (blastRole === 'upstream') {
    containerClass += ' blast-upstream';
    blastBadge = <span className="blast-role-pill blast-upstream-pill">UPSTREAM CALLER</span>;
  } else if (blastRole === 'downstream') {
    containerClass += ' blast-downstream';
    blastBadge = <span className="blast-role-pill blast-downstream-pill">DOWNSTREAM DEP</span>;
  } else if (blastRole === 'dimmed') {
    containerClass += ' blast-dimmed';
  }

  if (isSelected) {
    containerClass += ' selected';
  }

  // Summarize primary metrics preview
  let metricSummary = '';
  if (type === 'POD') {
    const restarts = details.restarts ?? 0;
    const cpu = details.cpuRequests || '-';
    metricSummary = `restarts: ${restarts} • cpu: ${cpu}`;
  } else if (type === 'DEPLOYMENT') {
    const ready = details.readyReplicas ?? '-';
    const total = details.replicas ?? '-';
    metricSummary = `ready: ${ready}/${total} replicas`;
  } else if (type === 'NODE') {
    const allocCpu = details.allocatableCpu || '-';
    metricSummary = `alloc: ${allocCpu} • ${details.status || 'Ready'}`;
  } else if (type === 'SERVICE') {
    metricSummary = `${details.serviceType || 'ClusterIP'} • ${details.clusterIp || 'dynamic'}`;
  }

  return (
    <div className={containerClass}>
      <Handle
        type="target"
        position={targetPosition || Position.Top}
        isConnectable={isConnectable}
        className="flow-handle"
      />

      <div className="node-header">
        <div className="node-title-group">
          <div className="node-icon-wrapper">{getTypeIcon(type)}</div>
          <div className="node-text-wrapper">
            <div className="node-name" title={name}>
              {name}
            </div>
            <div className="node-sub">
              <span className="node-type-label">{type}</span>
              {namespace && <span className="node-namespace-label">ns/{namespace}</span>}
            </div>
          </div>
        </div>

        <div className="node-health-indicator">
          <span
            className="health-pulse-dot"
            style={{ backgroundColor: healthColor, boxShadow: `0 0 8px ${healthColor}` }}
          />
          <span
            className="health-text"
            style={{
              color: healthStyle.text,
              backgroundColor: healthStyle.bg,
              border: `1px solid ${healthStyle.border}`,
            }}
          >
            {healthStyle.label}
          </span>
        </div>
      </div>

      {metricSummary && (
        <div className="node-metric-strip">
          {type === 'POD' && details.restarts && Number(details.restarts) > 0 ? (
            <span className="restarts-warning">
              <RotateCcw size={11} /> {details.restarts} restarts
            </span>
          ) : (
            <span>{metricSummary}</span>
          )}
        </div>
      )}

      {blastBadge && <div className="node-blast-tag">{blastBadge}</div>}

      {/* Hover action for services */}
      {type === 'SERVICE' && onAnalyzeBlast && (
        <button
          className="node-blast-btn"
          title={`Analyze blast radius for ${name}`}
          onClick={(e) => {
            e.stopPropagation();
            onAnalyzeBlast(name);
          }}
        >
          <Crosshair size={12} /> Blast Radius
        </button>
      )}

      <Handle
        type="source"
        position={sourcePosition || Position.Bottom}
        isConnectable={isConnectable}
        className="flow-handle"
      />
    </div>
  );
});

CustomNode.displayName = 'CustomNode';
export default CustomNode;
