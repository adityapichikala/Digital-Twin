import React from 'react';
import {
  Crosshair,
  ArrowUpRight,
  ArrowDownRight,
  ShieldAlert,
  X,
  AlertOctagon,
  CheckCircle,
} from 'lucide-react';
import { BlastRadiusResponse, RiskScore } from '../../types/topology';

interface BlastRadiusPanelProps {
  blastData: BlastRadiusResponse | null;
  onClear: () => void;
  onSelectNode: (nodeId: string) => void;
}

function getRiskBadge(score: RiskScore) {
  switch (score) {
    case 'CRITICAL':
      return { bg: 'rgba(239, 68, 68, 0.2)', text: '#ef4444', border: '#ef4444', icon: <AlertOctagon size={14} /> };
    case 'HIGH':
      return { bg: 'rgba(249, 115, 22, 0.2)', text: '#f97316', border: '#f97316', icon: <ShieldAlert size={14} /> };
    case 'MEDIUM':
      return { bg: 'rgba(245, 158, 11, 0.2)', text: '#f59e0b', border: '#f59e0b', icon: <ShieldAlert size={14} /> };
    default:
      return { bg: 'rgba(16, 185, 129, 0.2)', text: '#10b981', border: '#10b981', icon: <CheckCircle size={14} /> };
  }
}

export const BlastRadiusPanel: React.FC<BlastRadiusPanelProps> = ({
  blastData,
  onClear,
  onSelectNode,
}) => {
  if (!blastData) return null;

  const { target, upstream, downstream, totalImpacted, riskScore, explanation } = blastData;
  const riskBadge = getRiskBadge(riskScore);

  return (
    <div className="blast-radius-panel animate-slide-up">
      <div className="blast-panel-header">
        <div className="blast-title-group">
          <Crosshair size={18} className="text-amber-400" />
          <div>
            <div className="blast-panel-title">Blast Radius Assessment</div>
            <div className="blast-panel-target">
              Target: <strong>{target.name}</strong>
            </div>
          </div>
        </div>

        <div className="blast-header-actions">
          <div
            className="blast-risk-pill"
            style={{
              backgroundColor: riskBadge.bg,
              color: riskBadge.text,
              borderColor: riskBadge.border,
            }}
          >
            {riskBadge.icon}
            <span>{riskScore} RISK</span>
          </div>

          <button className="panel-close-btn" onClick={onClear} title="Clear blast radius highlight">
            <X size={16} />
          </button>
        </div>
      </div>

      <div className="blast-explanation-box">{explanation}</div>

      <div className="blast-columns">
        {/* Upstream Callers */}
        <div className="blast-col">
          <div className="blast-col-header">
            <ArrowUpRight size={14} className="text-orange-400" />
            <span>Upstream Affected Callers ({upstream.length})</span>
          </div>
          <div className="blast-node-list">
            {upstream.length === 0 ? (
              <div className="blast-empty-text">No direct upstream callers</div>
            ) : (
              upstream.map((node) => (
                <div
                  key={node.id}
                  className="blast-node-item upstream-item"
                  onClick={() => onSelectNode(node.id)}
                >
                  <span className="dot-indicator" style={{ backgroundColor: node.healthColor || '#10b981' }} />
                  <span className="node-item-name">{node.name}</span>
                  <span className="node-item-type">{node.type}</span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Downstream Dependencies */}
        <div className="blast-col">
          <div className="blast-col-header">
            <ArrowDownRight size={14} className="text-purple-400" />
            <span>Downstream Dependencies ({downstream.length})</span>
          </div>
          <div className="blast-node-list">
            {downstream.length === 0 ? (
              <div className="blast-empty-text">No downstream dependencies</div>
            ) : (
              downstream.map((node) => (
                <div
                  key={node.id}
                  className="blast-node-item downstream-item"
                  onClick={() => onSelectNode(node.id)}
                >
                  <span className="dot-indicator" style={{ backgroundColor: node.healthColor || '#10b981' }} />
                  <span className="node-item-name">{node.name}</span>
                  <span className="node-item-type">{node.type}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="blast-footer">
        <span className="blast-impact-total">
          Total Affected Resources: <strong>{totalImpacted}</strong>
        </span>
        <button className="btn btn-outline btn-sm" onClick={onClear}>
          Exit Blast Analysis
        </button>
      </div>
    </div>
  );
};

export default BlastRadiusPanel;
