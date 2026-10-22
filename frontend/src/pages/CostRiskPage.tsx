import React, { useEffect, useState } from 'react';
import {
  DollarSign,
  TrendingDown,
  AlertTriangle,
  RefreshCw,
  PieChart,
  CheckCircle,
} from 'lucide-react';
import api from '../api/client';

interface OverprovisionedPod {
  podName: string;
  namespace: string;
  serviceName: string;
  nodeName: string;
  currentCpuRequest: string;
  p95CpuUsage: string;
  recommendedCpuRequest: string;
  currentMemoryRequest: string;
  p95MemoryUsage: string;
  recommendedMemoryRequest: string;
  cpuWastePercent: number;
  memoryWastePercent: number;
  estimatedMonthlyWasteUsd: number;
  explanation: string;
}

interface OverprovisionedReport {
  totalPodsAnalyzed: number;
  totalOverprovisionedPods: number;
  potentialMonthlySavingsUsd: number;
  potentialAnnualSavingsUsd: number;
  overprovisionedPods: OverprovisionedPod[];
  evaluatedWindow: string;
  dataAvailable?: boolean;
}

interface NamespaceCost {
  namespace: string;
  monthlyCostUsd: number;
  percentOfTotal: number;
  podCount: number;
}

interface ServiceCost {
  serviceName: string;
  namespace: string;
  monthlyCostUsd: number;
  percentOfTotal: number;
}

interface CostSummary {
  totalMonthlyCostUsd: number;
  totalAnnualCostUsd: number;
  currency: string;
  dataSource: string;
  potentialMonthlySavingsUsd: number;
  overprovisionedPodCount: number;
  controlPlaneCostUsd?: number;
  dataAvailable?: boolean;
  byNamespace: NamespaceCost[];
  byService: ServiceCost[];
  lastUpdated: string;
}

export default function CostRiskPage() {
  const [costSummary, setCostSummary] = useState<CostSummary | null>(null);
  const [overReport, setOverReport] = useState<OverprovisionedReport | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<'overprovisioned' | 'breakdown'>('overprovisioned');
  const [isDemoData, setIsDemoData] = useState<boolean>(false);

  async function fetchData() {
    setLoading(true);
    try {
      let costRes, overRes;
      try {
        [costRes, overRes] = await Promise.all([
          api.get<CostSummary>('/api/cost/summary'),
          api.get<OverprovisionedReport>('/api/cost/overprovisioned'),
        ]);
      } catch {
        [costRes, overRes] = await Promise.all([
          api.get<CostSummary>('/api/v1/cost/summary'),
          api.get<OverprovisionedReport>('/api/v1/cost/overprovisioned'),
        ]);
      }

      setCostSummary(costRes.data);
      setOverReport(overRes.data);
      setIsDemoData(false);
    } catch (err) {
      console.warn('Cost API offline; providing truthful offline response with DEMO DATA banner:', err);
      setIsDemoData(true);
      // Realistic pricing catalog without synthetic right-sizing fake numbers
      setCostSummary({
        totalMonthlyCostUsd: 133.74,
        totalAnnualCostUsd: 1604.88,
        currency: 'USD',
        dataSource: 'INSTANCE_PRICING_CATALOG',
        potentialMonthlySavingsUsd: 0.0,
        overprovisionedPodCount: 0,
        controlPlaneCostUsd: 73.0,
        dataAvailable: false,
        byNamespace: [
          { namespace: 'twindigital', monthlyCostUsd: 85.00, percentOfTotal: 63.6, podCount: 4 },
          { namespace: 'monitoring', monthlyCostUsd: 48.74, percentOfTotal: 36.4, podCount: 2 },
        ],
        byService: [
          { serviceName: 'billing-service', namespace: 'twindigital', monthlyCostUsd: 36.44, percentOfTotal: 27.2 },
          { serviceName: 'auth-service', namespace: 'twindigital', monthlyCostUsd: 28.56, percentOfTotal: 21.3 },
          { serviceName: 'gateway', namespace: 'twindigital', monthlyCostUsd: 20.00, percentOfTotal: 15.0 },
          { serviceName: 'prometheus', namespace: 'monitoring', monthlyCostUsd: 48.74, percentOfTotal: 36.5 },
        ],
        lastUpdated: new Date().toISOString(),
      });

      setOverReport({
        totalPodsAnalyzed: 0,
        totalOverprovisionedPods: 0,
        potentialMonthlySavingsUsd: 0.0,
        potentialAnnualSavingsUsd: 0.0,
        evaluatedWindow: '7-day p95 PromQL (no data)',
        dataAvailable: false,
        overprovisionedPods: [],
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchData();
  }, []);

  const hasPrometheusData = overReport?.dataAvailable !== false;

  return (
    <div className="page-content" style={{ maxWidth: 1200, margin: '0 auto' }}>
      {/* Top Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ fontSize: '1.35rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <DollarSign size={22} className="text-accent" /> Cluster Cost & Right-Sizing
          </h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem', marginTop: '0.25rem' }}>
            Prometheus 7-day p95 PromQL actual usage analysis vs requests, AWS Cost Explorer billing, and right-sizing recommendations.
          </p>
        </div>

        <button className="btn btn-outline btn-sm" onClick={fetchData} disabled={loading}>
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
          {loading ? 'Analyzing…' : 'Refresh'}
        </button>
      </div>

      {/* Visible DEMO DATA Banner if backend is offline */}
      {isDemoData && (
        <div
          style={{
            background: 'rgba(234, 179, 8, 0.15)',
            border: '1px solid rgba(234, 179, 8, 0.4)',
            borderRadius: 'var(--radius-sm)',
            padding: '0.65rem 0.85rem',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            color: '#eab308',
            fontSize: '0.8rem',
            fontWeight: 600,
          }}
        >
          <span
            style={{
              background: '#eab308',
              color: '#000',
              padding: '0.1rem 0.35rem',
              borderRadius: '3px',
              fontSize: '0.68rem',
              fontWeight: 800,
              letterSpacing: '0.05em',
            }}
          >
            DEMO DATA
          </span>
          <span>Cost & Risk service is offline. Displaying static catalog pricing demo data.</span>
        </div>
      )}

      {/* Prometheus Telemetry Unavailable Banner */}
      {!hasPrometheusData && (
        <div
          className="card animate-fade-in"
          style={{
            marginBottom: '1.5rem',
            borderLeft: '4px solid #f59e0b',
            background: 'rgba(245, 158, 11, 0.08)',
            display: 'flex',
            alignItems: 'center',
            gap: '1rem',
            padding: '1rem 1.25rem',
          }}
        >
          <AlertTriangle size={24} style={{ color: '#f59e0b', flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 600, color: '#f59e0b', fontSize: '0.9rem' }}>
              Prometheus Metrics Unavailable (dataAvailable = false)
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
              No 7-day p95 container usage telemetry was returned by Prometheus. Synthetic estimations have been removed to preserve data integrity. Right-sizing recommendations will appear once Prometheus telemetry is active.
            </div>
          </div>
        </div>
      )}

      {/* Metric Cards Grid */}
      <div className="grid-4" style={{ marginBottom: '1.5rem' }}>
        <div className="card">
          <div className="card-title">Monthly Estimated Cost</div>
          <div className="card-value">${costSummary?.totalMonthlyCostUsd.toFixed(2) || '133.74'}</div>
          <div className="card-sub" style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
            <span className="badge badge-blue">{costSummary?.dataSource || 'PRICING_CATALOG'}</span>
            <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
              (incl. ${(costSummary?.controlPlaneCostUsd ?? 73.0).toFixed(0)}/mo EKS CP)
            </span>
          </div>
        </div>

        <div className="card">
          <div className="card-title">Potential Monthly Savings</div>
          <div className="card-value" style={{ color: hasPrometheusData ? '#3fb950' : 'var(--color-text-secondary)' }}>
            {hasPrometheusData
              ? `$${overReport?.potentialMonthlySavingsUsd.toFixed(2) || '0.00'}`
              : 'N/A'}
          </div>
          <div className="card-sub" style={{ color: hasPrometheusData ? 'var(--color-accent-2)' : 'var(--color-text-secondary)' }}>
            {hasPrometheusData ? (
              <>
                <TrendingDown size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 2 }} />
                Save ~${overReport?.potentialAnnualSavingsUsd.toFixed(0) || '0'}/yr with right-sizing
              </>
            ) : (
              'Telemetry unavailable'
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-title">Over-provisioned Pods</div>
          <div className="card-value" style={{ color: hasPrometheusData ? '#d29922' : 'var(--color-text-secondary)' }}>
            {hasPrometheusData ? (overReport?.totalOverprovisionedPods ?? 0) : 'N/A'}
          </div>
          <div className="card-sub">
            {hasPrometheusData
              ? `Out of ${overReport?.totalPodsAnalyzed ?? 0} active cluster pods analyzed`
              : 'No PromQL metrics collected'}
          </div>
        </div>

        <div className="card">
          <div className="card-title">Annualized Run-Rate</div>
          <div className="card-value">
            ${costSummary?.totalAnnualCostUsd.toFixed(2) || '1604.88'}
          </div>
          <div className="card-sub">
            Calculated over 730 hours/month compute
          </div>
        </div>
      </div>

      {/* Tabs navigation */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid var(--color-border)', paddingBottom: '0.5rem' }}>
        <button
          className={`btn btn-sm ${activeTab === 'overprovisioned' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('overprovisioned')}
        >
          <TrendingDown size={14} /> Right-Sizing Recommendations ({hasPrometheusData ? (overReport?.totalOverprovisionedPods ?? 0) : 0})
        </button>
        <button
          className={`btn btn-sm ${activeTab === 'breakdown' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setActiveTab('breakdown')}
        >
          <PieChart size={14} /> Namespace & Service Spend
        </button>
      </div>

      {/* Tab 1: Overprovisioned Pods Table */}
      {activeTab === 'overprovisioned' && (
        <div className="card animate-fade-in" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <div>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 600 }}>Detected Over-provisioned Pods</h3>
              <p style={{ fontSize: '0.78rem', color: 'var(--color-text-secondary)', marginTop: '0.2rem' }}>
                Pods where 7-day p95 PromQL CPU utilization &lt; 35% or Memory working-set &lt; 40% of requested allocation.
              </p>
            </div>
            <span className={`badge ${hasPrometheusData ? 'badge-yellow' : 'badge-blue'}`}>
              Window: {overReport?.evaluatedWindow || '7-day p95'}
            </span>
          </div>

          {!hasPrometheusData ? (
            <div style={{ padding: '2.5rem 1rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
              <AlertTriangle size={36} style={{ color: '#f59e0b', margin: '0 auto 0.75rem', opacity: 0.8 }} />
              <div style={{ fontWeight: 600, fontSize: '0.95rem', color: 'var(--color-text-primary)' }}>
                No Prometheus Metrics Available (dataAvailable = false)
              </div>
              <p style={{ fontSize: '0.8rem', maxWidth: 520, margin: '0.5rem auto 0' }}>
                Prometheus has not yet accumulated or returned 7-day p95 metrics for container CPU and memory. Synthetic fallback numbers have been removed to prevent inaccurate projections.
              </p>
            </div>
          ) : (overReport?.overprovisionedPods.length ?? 0) === 0 ? (
            <div style={{ padding: '2.5rem 1rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
              <CheckCircle size={36} style={{ color: '#3fb950', margin: '0 auto 0.75rem', opacity: 0.8 }} />
              <div style={{ fontWeight: 600, fontSize: '0.95rem', color: 'var(--color-text-primary)' }}>
                No Over-provisioned Pods Detected
              </div>
              <p style={{ fontSize: '0.8rem', maxWidth: 480, margin: '0.5rem auto 0' }}>
                All active workloads are utilizing requested CPU and memory within target thresholds.
              </p>
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                    <th style={{ padding: '0.6rem 0.5rem' }}>Pod / Service</th>
                    <th style={{ padding: '0.6rem 0.5rem' }}>CPU (Req → p95)</th>
                    <th style={{ padding: '0.6rem 0.5rem' }}>Recommended CPU</th>
                    <th style={{ padding: '0.6rem 0.5rem' }}>Memory (Req → p95)</th>
                    <th style={{ padding: '0.6rem 0.5rem' }}>Recommended RAM</th>
                    <th style={{ padding: '0.6rem 0.5rem' }}>Est. Waste ($/mo)</th>
                  </tr>
                </thead>
                <tbody>
                  {overReport?.overprovisionedPods.map((p, idx) => (
                    <tr key={idx} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                      <td style={{ padding: '0.75rem 0.5rem' }}>
                        <div style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>{p.serviceName}</div>
                        <div style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)', fontFamily: 'monospace' }}>
                          {p.podName}
                        </div>
                      </td>
                      <td style={{ padding: '0.75rem 0.5rem' }}>
                        <div>{p.currentCpuRequest} → <span style={{ color: '#38bdf8' }}>{p.p95CpuUsage}</span></div>
                        <div style={{ fontSize: '0.7rem', color: '#f59e0b' }}>{p.cpuWastePercent.toFixed(0)}% unused</div>
                      </td>
                      <td style={{ padding: '0.75rem 0.5rem' }}>
                        <span className="badge badge-green" style={{ fontFamily: 'monospace' }}>
                          {p.recommendedCpuRequest}
                        </span>
                      </td>
                      <td style={{ padding: '0.75rem 0.5rem' }}>
                        <div>{p.currentMemoryRequest} → <span style={{ color: '#38bdf8' }}>{p.p95MemoryUsage}</span></div>
                        <div style={{ fontSize: '0.7rem', color: '#f59e0b' }}>{p.memoryWastePercent.toFixed(0)}% unused</div>
                      </td>
                      <td style={{ padding: '0.75rem 0.5rem' }}>
                        <span className="badge badge-green" style={{ fontFamily: 'monospace' }}>
                          {p.recommendedMemoryRequest}
                        </span>
                      </td>
                      <td style={{ padding: '0.75rem 0.5rem', fontWeight: 700, color: '#3fb950' }}>
                        ${p.estimatedMonthlyWasteUsd.toFixed(2)}/mo
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Breakdown by Namespace & Service */}
      {activeTab === 'breakdown' && (
        <div className="grid-2 animate-fade-in" style={{ gap: '1.5rem' }}>
          {/* Namespace Breakdown */}
          <div className="card">
            <h3 className="card-title">Spend by Kubernetes Namespace</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem', marginTop: '0.75rem' }}>
              {costSummary?.byNamespace.map((ns, idx) => (
                <div key={idx}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.25rem' }}>
                    <span style={{ fontWeight: 600 }}>{ns.namespace} ({ns.podCount} pods)</span>
                    <span>${ns.monthlyCostUsd.toFixed(2)}/mo ({ns.percentOfTotal}%)</span>
                  </div>
                  <div style={{ height: 6, background: 'var(--color-surface-2)', borderRadius: 3, overflow: 'hidden' }}>
                    <div
                      style={{
                        height: '100%',
                        width: `${ns.percentOfTotal}%`,
                        background: 'linear-gradient(90deg, var(--color-accent), var(--color-accent-2))',
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Service Breakdown */}
          <div className="card">
            <h3 className="card-title">Top Workloads by Cost</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', marginTop: '0.75rem' }}>
              {costSummary?.byService.map((svc, idx) => (
                <div
                  key={idx}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0.5rem 0.75rem',
                    background: 'var(--color-surface-2)',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.8rem',
                  }}
                >
                  <div>
                    <span style={{ fontWeight: 600 }}>{svc.serviceName}</span>
                    <span style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)', marginLeft: '0.5rem' }}>
                      ns/{svc.namespace}
                    </span>
                  </div>
                  <span style={{ fontWeight: 700, color: 'var(--color-text-primary)' }}>
                    ${svc.monthlyCostUsd.toFixed(2)}/mo
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
