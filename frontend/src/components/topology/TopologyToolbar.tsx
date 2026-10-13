import React from 'react';
import {
  Play,
  Pause,
  RefreshCw,
  Search,
  Layers,
  Box,
  Server,
  Globe,
} from 'lucide-react';
import { ClusterHealthSummary } from '../../types/topology';
import { LayoutDirection } from './layoutUtils';

interface TopologyToolbarProps {
  healthSummary?: ClusterHealthSummary;
  autoRefresh: boolean;
  onToggleAutoRefresh: () => void;
  onManualRefresh: () => void;
  loading: boolean;
  direction: LayoutDirection;
  onChangeDirection: (dir: LayoutDirection) => void;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  selectedType: string;
  onTypeFilterChange: (type: string) => void;
}

export const TopologyToolbar: React.FC<TopologyToolbarProps> = ({
  healthSummary,
  autoRefresh,
  onToggleAutoRefresh,
  onManualRefresh,
  loading,
  direction,
  onChangeDirection,
  searchQuery,
  onSearchChange,
  selectedType,
  onTypeFilterChange,
}) => {
  return (
    <div className="topology-toolbar">
      {/* Left side: Health & Stats */}
      <div className="toolbar-left">
        {healthSummary && (
          <div className="toolbar-health-badge">
            <span
              className="health-badge-indicator"
              style={{
                backgroundColor:
                  healthSummary.overallStatus === 'CRITICAL'
                    ? '#ef4444'
                    : healthSummary.overallStatus === 'DEGRADED'
                    ? '#f59e0b'
                    : '#10b981',
              }}
            />
            <span className="health-badge-title">
              Cluster {healthSummary.overallStatus}
            </span>
            <span className="health-badge-counts">
              ({healthSummary.healthyCount} healthy • {healthSummary.degradedCount} degraded • {healthSummary.criticalCount} critical)
            </span>
          </div>
        )}

        {/* Filter buttons */}
        <div className="type-filter-group">
          <button
            className={`type-filter-btn ${selectedType === 'ALL' ? 'active' : ''}`}
            onClick={() => onTypeFilterChange('ALL')}
          >
            All
          </button>
          <button
            className={`type-filter-btn ${selectedType === 'SERVICE' ? 'active' : ''}`}
            onClick={() => onTypeFilterChange('SERVICE')}
            title="Filter Services"
          >
            <Globe size={12} /> Services
          </button>
          <button
            className={`type-filter-btn ${selectedType === 'DEPLOYMENT' ? 'active' : ''}`}
            onClick={() => onTypeFilterChange('DEPLOYMENT')}
            title="Filter Deployments"
          >
            <Layers size={12} /> Deployments
          </button>
          <button
            className={`type-filter-btn ${selectedType === 'POD' ? 'active' : ''}`}
            onClick={() => onTypeFilterChange('POD')}
            title="Filter Pods"
          >
            <Box size={12} /> Pods
          </button>
          <button
            className={`type-filter-btn ${selectedType === 'NODE' ? 'active' : ''}`}
            onClick={() => onTypeFilterChange('NODE')}
            title="Filter Nodes"
          >
            <Server size={12} /> Nodes
          </button>
        </div>
      </div>

      {/* Right side: Search, Layout toggle & Auto-refresh */}
      <div className="toolbar-right">
        {/* Search */}
        <div className="toolbar-search-box">
          <Search size={14} className="search-icon" />
          <input
            type="text"
            placeholder="Search node or service..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="search-input"
          />
          {searchQuery && (
            <button className="clear-search-btn" onClick={() => onSearchChange('')}>
              ×
            </button>
          )}
        </div>

        {/* Layout direction toggle */}
        <div className="layout-direction-toggle">
          <button
            className={`layout-dir-btn ${direction === 'TB' ? 'active' : ''}`}
            onClick={() => onChangeDirection('TB')}
            title="Top-to-Bottom Layout"
          >
            Vertical (TB)
          </button>
          <button
            className={`layout-dir-btn ${direction === 'LR' ? 'active' : ''}`}
            onClick={() => onChangeDirection('LR')}
            title="Left-to-Right Layout"
          >
            Horizontal (LR)
          </button>
        </div>

        {/* Auto Refresh Toggle */}
        <button
          className={`btn btn-sm ${autoRefresh ? 'btn-refresh-active' : 'btn-outline'}`}
          onClick={onToggleAutoRefresh}
          title={autoRefresh ? 'Pause 5s auto-polling' : 'Enable 5s auto-polling'}
        >
          {autoRefresh ? <Pause size={13} /> : <Play size={13} />}
          <span>{autoRefresh ? '5s Polling ON' : 'Polling Paused'}</span>
        </button>

        {/* Manual Refresh */}
        <button
          className="btn btn-outline btn-sm"
          onClick={onManualRefresh}
          disabled={loading}
          title="Refresh topology immediately"
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>
    </div>
  );
};

export default TopologyToolbar;
