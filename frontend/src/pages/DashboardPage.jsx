import { Activity, Server, GitBranch, DollarSign, AlertTriangle, RefreshCw } from 'lucide-react';
import { useState, useEffect } from 'react';
import api from '../api/client';

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
  const [stats, setStats] = useState(null);

  useEffect(() => {
    api.get('/api/v1/cluster/status')
      .then(({ data }) => setStats(data))
      .catch(() => {}); // keep null — show placeholders
  }, []);

  return (
    <div className="page-content">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 600 }}>Cluster Overview</h2>
          <p style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
            Live mirror of your EKS cluster
          </p>
        </div>
        <button id="refresh-btn" className="btn btn-outline" onClick={() => window.location.reload()}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
        <StatCard title="Nodes" value={stats?.nodeCount ?? '—'} sub="Ready"
                  icon={Server} badge="Healthy" badgeClass="badge-green" />
        <StatCard title="Workloads" value={stats?.workloadCount ?? '—'} sub="Deployed"
                  icon={GitBranch} badge="Synced" badgeClass="badge-blue" />
        <StatCard title="Simulations Run" value="0" sub="This month"
                  icon={Activity} badge="No runs yet" badgeClass="badge-yellow" />
        <StatCard title="Est. Monthly Cost" value="—" sub="Based on current topology"
                  icon={DollarSign} />
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem' }}>Recent Simulations</div>
          <div style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', textAlign: 'center', padding: '2rem 0' }}>
            No simulations yet. Use the Simulate tab to model a what-if change.
          </div>
        </div>
        <div className="card">
          <div className="card-title" style={{ marginBottom: '1rem' }}>Cluster Alerts</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem',
                        color: 'var(--color-text-muted)', fontSize: '0.85rem', padding: '2rem 0', justifyContent: 'center' }}>
            <AlertTriangle size={16} /> No active alerts
          </div>
        </div>
      </div>
    </div>
  );
}
