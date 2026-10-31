'use strict';

const http = require('http');
const request = require('supertest');
const jwt = require('jsonwebtoken');

const TEST_SECRET = '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40';
process.env.JWT_SECRET = TEST_SECRET;
process.env.NODE_ENV = 'test';

function generateToken(role = 'ADMIN', username = 'testuser') {
  return jwt.sign(
    { sub: username, username, role, jti: 'jti-test-' + Math.random() },
    TEST_SECRET,
    { algorithm: 'HS256', expiresIn: '1h' }
  );
}

describe('Gateway Proxy Path Forwarding Integration', () => {
  let upstreamServer;
  let upstreamPort;
  let capturedRequests = [];
  let app;
  let gatewayServer;

  beforeAll((done) => {
    capturedRequests = [];
    upstreamServer = http.createServer((req, res) => {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        capturedRequests.push({
          method: req.method,
          url: req.url,
          headers: req.headers,
          body,
        });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'UPSTREAM_OK', receivedUrl: req.url }));
      });
    });

    upstreamServer.listen(0, '127.0.0.1', () => {
      upstreamPort = upstreamServer.address().port;
      const upstreamUrl = `http://127.0.0.1:${upstreamPort}`;

      process.env.TOPOLOGY_SERVICE_URL = upstreamUrl;
      process.env.CLUSTER_SYNC_SERVICE_URL = upstreamUrl;
      process.env.SIMULATION_SERVICE_URL = upstreamUrl;
      process.env.COST_RISK_SERVICE_URL = upstreamUrl;
      process.env.AUTH_SERVICE_URL = upstreamUrl;

      // Invalidate require cache to reload route proxies with the stub target URL
      Object.keys(require.cache).forEach((key) => {
        if (key.includes('/src/') || key.includes('\\src\\')) {
          delete require.cache[key];
        }
      });

      const gateway = require('../index');
      app = gateway.app;
      gatewayServer = gateway.server;
      done();
    });
  });

  afterAll((done) => {
    if (gatewayServer && gatewayServer.close) {
      gatewayServer.close(() => {
        upstreamServer.close(done);
      });
    } else {
      upstreamServer.close(done);
    }
  });

  beforeEach(() => {
    capturedRequests = [];
  });

  it('forwards /api/v1/topology with full path to topology-service', async () => {
    const token = generateToken('VIEWER');
    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', `Bearer ${token}`);

    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe('UPSTREAM_OK');
    expect(capturedRequests.length).toBe(1);
    expect(capturedRequests[0].method).toBe('GET');
    expect(capturedRequests[0].url).toBe('/api/v1/topology');
  });

  it('forwards /api/v1/cluster/status with full path and cluster->sync rewrite to cluster-sync-service', async () => {
    const token = generateToken('VIEWER');
    const res = await request(app)
      .get('/api/v1/cluster/status')
      .set('Authorization', `Bearer ${token}`);

    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe('UPSTREAM_OK');
    expect(capturedRequests.length).toBe(1);
    expect(capturedRequests[0].method).toBe('GET');
    expect(capturedRequests[0].url).toBe('/api/v1/sync/status');
  });

  it('forwards /api/v1/simulations with full path to simulation-service', async () => {
    const token = generateToken('ADMIN');
    const res = await request(app)
      .post('/api/v1/simulations')
      .set('Authorization', `Bearer ${token}`)
      .send({ kind: 'SCALE', target: 'billing-service', replicas: 3 });

    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe('UPSTREAM_OK');
    expect(capturedRequests.length).toBe(1);
    expect(capturedRequests[0].method).toBe('POST');
    expect(capturedRequests[0].url).toBe('/api/v1/simulations');
  });
});
