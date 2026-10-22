import { Activity, Server, GitBranch, DollarSign, AlertTriangle, RefreshCw, CheckCircle2 } from 'lucide-react';
import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import useAuthStore from '../store/authStore';

function StatCard({ title, value, sub, icon: Icon, badgeClass, badge }) {
  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="card-title">{title}</div>
          <div className="card-value">{value}</div>
          {sub && <div className="card-sub">{sub}</div>}
        </div>
        <div style={{ padding: '0.5rem', background: 'var(--color-surface-2)',
                      borderRadius: 'var(--radius-sm)', color: 'var(--color-accent)' }}>
          <Icon size={18} />
        </div>
      </div>
      {badge && <div className={`badge ${badgeClass}`} style={{ marginTop: '0.75rem' }}>{badge}</div>}
    </div>
  );
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const isViewer = user?.role?.toUpperCase() === 'VIEWER';

  const [loading, setLoading] = useState(false);
  const [nodeCount, setNodeCount] = useState('—');
  const [workloadCount, setWorkloadCount] = useState('—');
  const [simulations, setSimulations] = useState([]);
  const [monthlyCost, setMonthlyCost] = useState('—');
  const [syncStatus, setSyncStatus] = useState(null);
  const [overprovisionedCount, setOverprovisionedCount] = useState(0);

  const fetchDashboardData = useCallback(async () => {
    setLoading(true);
    try {
      // 1. Sync & Node status
      try {
        const { data: statusData } = await api.get('/api/v1/cluster/status');
        setSyncStatus(statusData);
      } catch {
        // cluster-sync offline
      }

      // 2. Nodes count
      try {
        const { data: nodes } = await api.get('/api/v1/cluster/nodes');
        if (Array.isArray(nodes)) setNodeCount(String(nodes.length));
      } catch {
        // fallback
      }

      // 3. Workloads count
      try {
        const { data: deployments } = await api.get('/api/v1/cluster/deployments');
        if (Array.isArray(deployments)) setWorkloadCount(String(deployments.length));
      } catch {
        // fallback
      }

      // 4. Simulations (only ADMIN can access /api/v1/simulations)
      if (!isViewer) {
        try {
          const { data: simList } = await api.get('/api/v1/simulations');
          if (Array.isArray(simList)) setSimulations(simList);
        } catch {
          // simulation service offline
        }
      }

      // 5. Cost summary
      try {
        const { data: costData } = await api.get('/api/v1/cost/summary');
        if (costData?.totalMonthlyCostUsd != null) {
          setMonthlyCost(`$${costData.totalMonthlyCostUsd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`);
        }
        if (costData?.overprovisionedPodCount != null) {
          setOverprovisionedCount(costData.overprovisionedPodCount);
        }
      } catch {
        // cost service offline
      }
    } finally {
      setLoading(false);
    }
  }, [isViewer]);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  return (
    <div className="page-content">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 600 }}>Cluster Overview</h2>
          <p style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
            Live mirror of your EKS cluster
          </p>
        </div>
        <button id="refresh-btn" className="btn btn-outline" onClick={fetchDashboardData} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
        <StatCard
          title="Nodes"
          value={nodeCount}
          sub="Discovered in EKS"
          icon={Server}
          badge={nodeCount !== '—' ? 'Ready' : 'Pending'}
          badgeClass={nodeCount !== '—' ? 'badge-green' : 'badge-yellow'}
        />
        <StatCard
          title="Workloads"
          value={workloadCount}
          sub="Active Deployments"
          icon={GitBranch}
          badge={syncStatus?.status === 'STALE' ? 'Stale' : 'Synced'}
          badgeClass={syncStatus?.status === 'STALE' ? 'badge-yellow' : 'badge-blue'}
        />
        <StatCard
          title="Simulations Run"
          value={isViewer ? 'Restricted' : String(simulations.length)}
          sub={isViewer ? 'Admin access only' : 'Total audit records'}
          icon={Activity}
          badge={isViewer ? 'Viewer' : simulations.length > 0 ? `${simulations.length} runs` : 'No runs yet'}
          badgeClass={isViewer ? 'badge-blue' : simulations.length > 0 ? 'badge-green' : 'badge-yellow'}
        />
        <StatCard
          title="Est. Monthly Cost"
          value={monthlyCost}
          sub="Current cluster compute"
          icon={DollarSign}
        />
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Recent Simulations</span>
            {!isViewer && (
              <Link to="/simulate" className="btn btn-outline btn-sm" style={{ fontSize: '0.72rem' }}>
                Run What-If
              </Link>
            )}
          </div>
          {isViewer ? (
            <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem 0' }}>
              Simulations are restricted to Administrator roles.
            </div>
          ) : simulations.length === 0 ? (
            <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem 0' }}>
              No simulations recorded yet. Run a what-if simulation to evaluate proposed cluster changes.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {simulations.slice(0, 5).map((sim, i) => (
                <div key={sim.id || sim.simulationId || i} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0.6rem 0.75rem',
                  background: 'var(--color-surface-2)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.8rem',
                }}>
                  <div>
                    <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>
                      {sim.changeSet?.target || sim.target || 'Cluster change'}
                    </span>
                    <span style={{ marginLeft: '0.5rem', color: 'var(--color-text-secondary)', fontSize: '0.72rem' }}>
                      ({sim.changeSet?.kind || sim.riskLevel || 'SIMULATION'})
                    </span>
                  </div>
                  <span className={`badge ${
                    sim.riskLevel === 'CRITICAL' ? 'badge-red' :
                    sim.riskLevel === 'HIGH' ? 'badge-yellow' :
                    sim.riskLevel === 'MEDIUM' ? 'badge-blue' : 'badge-green'
                  }`}>
                    {sim.riskLevel || 'COMPLETED'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem' }}>Cluster Alerts & Telemetry</div>
          {syncStatus?.status === 'STALE' ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#f59e0b', fontSize: '0.85rem', padding: '1rem 0' }}>
              <AlertTriangle size={16} /> Cluster sync lag detected: {syncStatus.lagSeconds ?? 0}s behind K8s API.
            </div>
          ) : overprovisionedCount > 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#38bdf8', fontSize: '0.85rem', padding: '1rem 0' }}>
              <AlertTriangle size={16} /> {overprovisionedCount} workload(s) flagged as over-provisioned based on 7-day p95 usage.
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#10b981', fontSize: '0.85rem', padding: '2rem 0', justifyContent: 'center' }}>
              <CheckCircle2 size={16} /> All cluster informers healthy; zero active alerts
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
