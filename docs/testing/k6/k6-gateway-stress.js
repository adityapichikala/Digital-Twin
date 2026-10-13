import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ==============================================================================
// TwinDigital Gateway Stress & Latency Evaluation (k6)
// Targets:
//   - Configurable ramp to N req/s
//   - Captures p95 latency, throughput, and error rate
//   - Thresholds: p95 latency < 300ms, error rate < 1%
// ==============================================================================

// Custom Metrics
const errorRate = new Rate('twin_error_rate');
const p95Latency = new Trend('twin_p95_latency');
const totalRequests = new Counter('twin_total_requests');
const simulationDuration = new Trend('twin_simulation_duration');

// Configurable environment parameters
const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TARGET_RPS = parseInt(__ENV.TARGET_RPS || '500', 10);
const JWT_TOKEN = __ENV.JWT_TOKEN || ''; // Or dynamic login fallback

export const options = {
  scenarios: {
    // Ramp-up to target RPS, sustain, and ramp-down
    gateway_stress_test: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { duration: '30s', target: Math.floor(TARGET_RPS * 0.2) },  // Warm-up to 20%
        { duration: '1m',  target: Math.floor(TARGET_RPS * 0.6) },  // Ramp to 60%
        { duration: '2m',  target: TARGET_RPS },                     // Peak stress at 100%
        { duration: '1m',  target: Math.floor(TARGET_RPS * 1.2) },  // 120% stress spike
        { duration: '30s', target: 0 },                             // Cooldown
      ],
    },
  },
  thresholds: {
    // Production Acceptance Criteria:
    'http_req_duration': ['p(95)<300', 'p(99)<800'], // p95 latency < 300ms, p99 < 800ms
    'http_req_failed': ['rate<0.01'],                 // HTTP errors < 1%
    'twin_error_rate': ['rate<0.01'],                 // Application errors < 1%
  },
};

export function setup() {
  // If no token is passed, obtain one via auth-service login
  let token = JWT_TOKEN;
  if (!token) {
    const loginPayload = JSON.stringify({
      username: 'admin',
      password: 'AdminPassword123!',
    });
    const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
      headers: { 'Content-Type': 'application/json' },
    });
    if (loginRes.status === 200) {
      try {
        const body = JSON.parse(loginRes.body);
        token = body.accessToken || body.token || '';
      } catch (e) {
        console.warn('Could not parse auth token; continuing with unauthenticated requests.');
      }
    }
  }
  return { token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
  if (data.token) {
    headers['Authorization'] = `Bearer ${data.token}`;
  }

  // Realistic endpoint distribution:
  // 30% Health, 25% Topology Graph, 20% Cost Summary, 15% Sync Status, 10% Simulation Run
  const rand = Math.random();

  if (rand < 0.30) {
    // 1. Health Probe
    group('GET /health', () => {
      const res = http.get(`${BASE_URL}/health`, { headers });
      totalRequests.add(1);
      p95Latency.add(res.timings.duration);
      const passed = check(res, {
        'status is 200': (r) => r.status === 200,
        'has status UP': (r) => r.body.includes('UP'),
      });
      errorRate.add(!passed);
    });
  } else if (rand < 0.55) {
    // 2. Topology Directed Graph
    group('GET /api/topology', () => {
      const res = http.get(`${BASE_URL}/api/topology`, { headers });
      totalRequests.add(1);
      p95Latency.add(res.timings.duration);
      const passed = check(res, {
        'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
      });
      errorRate.add(!passed);
    });
  } else if (rand < 0.75) {
    // 3. Cost & Right-Sizing Summary
    group('GET /api/cost/summary', () => {
      const res = http.get(`${BASE_URL}/api/cost/summary`, { headers });
      totalRequests.add(1);
      p95Latency.add(res.timings.duration);
      const passed = check(res, {
        'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
      });
      errorRate.add(!passed);
    });
  } else if (rand < 0.90) {
    // 4. Cluster Sync Status
    group('GET /api/sync/status', () => {
      const res = http.get(`${BASE_URL}/api/sync/status`, { headers });
      totalRequests.add(1);
      p95Latency.add(res.timings.duration);
      const passed = check(res, {
        'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
      });
      errorRate.add(!passed);
    });
  } else {
    // 5. Simulation Engine Assessment Run
    group('POST /api/v1/simulations/run', () => {
      const payload = JSON.stringify({
        kind: 'SCALE',
        target: 'billing-service',
        replicas: 3,
        cpuRequest: '150m',
        memRequest: '256Mi',
        cpuLimit: '300m',
        memLimit: '512Mi',
      });
      const res = http.post(`${BASE_URL}/api/v1/simulations/run`, payload, { headers });
      totalRequests.add(1);
      simulationDuration.add(res.timings.duration);
      p95Latency.add(res.timings.duration);
      const passed = check(res, {
        'simulation returns 200 or 401/403': (r) => [200, 401, 403].includes(r.status),
      });
      errorRate.add(!passed);
    });
  }

  sleep(0.05); // Small pacing between iterations
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'].toFixed(2) : 'N/A';
  const err = data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : '0.00';
  const rps = data.metrics.http_reqs ? data.metrics.http_reqs.values.rate.toFixed(1) : 'N/A';

  console.log('\n======================================================');
  console.log(` TWINDIGITAL GATEWAY STRESS TEST RESULTS`);
  console.log('======================================================');
  console.log(` Achieved Throughput : ${rps} req/sec`);
  console.log(` p95 Latency         : ${p95} ms (Threshold: < 300 ms)`);
  console.log(` Error Rate          : ${err}% (Threshold: < 1.0 %)`);
  console.log('======================================================\n');

  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'k6-summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  return `k6 run completed. Details written to k6-summary.json`;
}
