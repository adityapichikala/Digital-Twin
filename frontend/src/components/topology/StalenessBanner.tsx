import React from 'react';
import { AlertTriangle, RefreshCw, Clock } from 'lucide-react';
import { StalenessInfo } from '../../types/topology';

interface StalenessBannerProps {
  staleness: StalenessInfo | null | undefined;
  onRefresh: () => void;
  refreshing?: boolean;
}

export const StalenessBanner: React.FC<StalenessBannerProps> = ({
  staleness,
  onRefresh,
  refreshing = false,
}) => {
  if (!staleness) return null;

  const isStale = staleness.isStale || staleness.lagSeconds > 60 || staleness.syncStatus === 'STALE';

  if (!isStale) return null;

  return (
    <div className="staleness-banner animate-fade-in">
      <div className="staleness-content">
        <div className="staleness-icon-wrapper">
          <AlertTriangle size={20} className="text-amber-400" />
        </div>
        <div className="staleness-text">
          <div className="staleness-headline">
            <span className="staleness-badge">SYNC STALE</span>
            <strong>Cluster synchronization lag detected ({staleness.lagSeconds}s behind)</strong>
          </div>
          <div className="staleness-details">
            <span className="staleness-timestamp">
              <Clock size={12} /> Last synced:{' '}
              {staleness.lastSyncedAt ? new Date(staleness.lastSyncedAt).toLocaleTimeString() : 'Unknown'}
            </span>
            <span>• Informer watch connection may be reconnecting or cluster sync delayed.</span>
          </div>
        </div>
      </div>

      <button
        className="btn btn-outline btn-sm staleness-refresh-btn"
        onClick={onRefresh}
        disabled={refreshing}
      >
        <RefreshCw size={13} className={refreshing ? 'animate-spin' : ''} />
        {refreshing ? 'Syncing...' : 'Sync Now'}
      </button>
    </div>
  );
};

export default StalenessBanner;
