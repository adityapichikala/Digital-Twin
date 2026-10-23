'use strict';

const request = require('supertest');
const jwt = require('jsonwebtoken');

const TEST_SECRET = '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40';
process.env.JWT_SECRET = TEST_SECRET;
process.env.PORT = '0';
process.env.NODE_ENV = 'test';

const { app, server } = require('../index');

afterAll((done) => {
  if (server && server.close) {
    server.close(done);
  } else {
    done();
  }
});

// Helper to generate test JWTs
function generateTestToken(payload, options = {}) {
  return jwt.sign(payload, TEST_SECRET, {
    algorithm: 'HS256',
    expiresIn: '1h',
    ...options,
  });
}

describe('Gateway - Health & Prometheus Metrics', () => {
  it('GET /health returns 200 with UP status, service name, and X-Request-Id header', async () => {
    const res = await request(app).get('/health');
    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe('UP');
    expect(res.body.service).toBe('twindigital-gateway');
    expect(res.headers['x-request-id']).toBeDefined();
    expect(res.body.requestId).toBe(res.headers['x-request-id']);
  });

  it('Propagates incoming X-Request-Id header in response', async () => {
    const customId = 'client-uuid-98765';
    const res = await request(app)
      .get('/health')
      .set('X-Request-Id', customId);

    expect(res.statusCode).toBe(200);
    expect(res.headers['x-request-id']).toBe(customId);
    expect(res.body.requestId).toBe(customId);
  });

  it('GET /metrics returns Prometheus metric declarations with aligned naming and labels', async () => {
    const res = await request(app).get('/metrics');
    expect(res.statusCode).toBe(200);
    expect(res.text).toContain('gateway_http_requests_total');
    expect(res.text).toContain('gateway_http_request_duration_seconds');
    expect(res.text).toContain('gateway_http_errors_total');
  });
});

describe('Gateway - JWT Verification', () => {
  it('rejects protected routes with 401 when Authorization header is missing', async () => {
    const res = await request(app).get('/api/v1/topology');
    expect(res.statusCode).toBe(401);
    expect(res.body).toHaveProperty('error', 'Unauthorized');
    expect(res.body).toHaveProperty('message');
    expect(res.body).toHaveProperty('requestId');
    expect(res.body).toHaveProperty('timestamp');
  });

  it('rejects protected routes with 401 when Authorization header is malformed', async () => {
    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', 'Basic dXNlcjpwYXNz');

    expect(res.statusCode).toBe(401);
    expect(res.body.error).toBe('Unauthorized');
  });

  it('Acceptance: a forged JWT (signed with wrong secret) is rejected with 401', async () => {
    const forgedToken = jwt.sign(
      { sub: 'attacker', username: 'attacker', role: 'ADMIN' },
      'forged-wrong-secret-key-123456789012345678901234567890',
      { algorithm: 'HS256' }
    );

    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', `Bearer ${forgedToken}`);

    expect(res.statusCode).toBe(401);
    expect(res.body.error).toBe('Unauthorized');
    expect(res.body.message).toMatch(/Invalid or forged token/i);
    expect(res.body.requestId).toBeDefined();
    expect(res.body.timestamp).toBeDefined();
  });

  it('rejects expired JWT with 401 and Token has expired message', async () => {
    const expiredToken = generateTestToken(
      { sub: 'alice', username: 'alice', role: 'VIEWER' },
      { expiresIn: '-10s' }
    );

    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', `Bearer ${expiredToken}`);

    expect(res.statusCode).toBe(401);
    expect(res.body.error).toBe('Unauthorized');
    expect(res.body.message).toMatch(/expired/i);
    expect(res.body.requestId).toBeDefined();
  });

  it('rejects revoked JWT (logout enforcement) with 401 and Token has been revoked message', async () => {
    const jwtVerify = require('../middleware/jwtVerify');
    const revokedToken = generateTestToken(
      { sub: 'alice', username: 'alice', role: 'VIEWER', jti: 'jti-logged-out-999' }
    );
    jwtVerify.revokeJti('jti-logged-out-999');

    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', `Bearer ${revokedToken}`);

    expect(res.statusCode).toBe(401);
    expect(res.body.error).toBe('Unauthorized');
    expect(res.body.message).toMatch(/revoked/i);
    jwtVerify.clearRevokedJtis();
  });
});

describe('Gateway - Role-Based Access Control (RBAC)', () => {
  it('Acceptance: a VIEWER hitting the simulation route gets 403 Forbidden', async () => {
    const viewerToken = generateTestToken({
      sub: 'viewer-user-1',
      username: 'bob_viewer',
      role: 'VIEWER',
    });

    const res = await request(app)
      .post('/api/v1/simulations/run')
      .set('Authorization', `Bearer ${viewerToken}`)
      .send({ whatIf: 'remove-node-1' });

    expect(res.statusCode).toBe(403);
    expect(res.body.error).toBe('Forbidden');
    expect(res.body.message).toMatch(/ADMIN/i);
    expect(res.body.requestId).toBeDefined();
    expect(res.body.timestamp).toBeDefined();
  });

  it('VIEWER role is authorized to read topology (passes RBAC, reaches proxy)', async () => {
    const viewerToken = generateTestToken({
      sub: 'viewer-user-1',
      username: 'bob_viewer',
      role: 'VIEWER',
    });

    const res = await request(app)
      .get('/api/v1/topology')
      .set('Authorization', `Bearer ${viewerToken}`);

    // Passes RBAC! Will receive 502 from proxy because topology-service is offline, but NOT 401 or 403
    expect(res.statusCode).not.toBe(401);
    expect(res.statusCode).not.toBe(403);
  });

  it('ADMIN role is authorized to access simulations (passes RBAC, reaches proxy)', async () => {
    const adminToken = generateTestToken({
      sub: 'admin-user-1',
      username: 'carol_admin',
      role: 'ADMIN',
    });

    const res = await request(app)
      .post('/api/v1/simulations')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ test: true });

    // Passes RBAC! Will receive 502 from proxy because simulation-service is offline, but NOT 401 or 403
    expect(res.statusCode).not.toBe(401);
    expect(res.statusCode).not.toBe(403);
  });
});

describe('Gateway - Standard Error Handling & 404', () => {
  it('returns consistent JSON 404 for unknown routes', async () => {
    const res = await request(app).get('/unknown-api-endpoint');
    expect(res.statusCode).toBe(404);
    expect(res.body.error).toBe('Not Found');
    expect(res.body.message).toMatch(/Endpoint not found/i);
    expect(res.body.requestId).toBeDefined();
    expect(res.body.timestamp).toBeDefined();
  });
});

describe('Gateway - ResilientStore Rate Limiter Fallback', () => {
  const { ResilientStore } = require('../middleware/rateLimiter');
  const { rateLimit } = require('express-rate-limit');
  const express = require('express');

  it('falls back to MemoryStore when primary Redis store throws errors', async () => {
    const brokenPrimaryStore = {
      init: jest.fn(),
      increment: jest.fn().mockRejectedValue(new Error('Redis connection refused')),
      decrement: jest.fn().mockRejectedValue(new Error('Redis connection refused')),
      resetKey: jest.fn().mockRejectedValue(new Error('Redis connection refused')),
    };

    const resilientStore = new ResilientStore(brokenPrimaryStore);
    resilientStore.init({ windowMs: 60000 });

    // increment should catch the Redis error and use fallback MemoryStore
    const res1 = await resilientStore.increment('test-client-ip');
    expect(res1.totalHits).toBe(1);

    const res2 = await resilientStore.increment('test-client-ip');
    expect(res2.totalHits).toBe(2);

    expect(brokenPrimaryStore.increment).toHaveBeenCalledTimes(2);
  });

  it('enforces rate limits in-memory and passes on store error when primary store fails', async () => {
    const failingStore = {
      init: jest.fn(),
      increment: jest.fn().mockRejectedValue(new Error('Redis ECONNRESET')),
    };

    const testApp = express();
    testApp.use(rateLimit({
      windowMs: 60000,
      max: 2,
      store: new ResilientStore(failingStore),
      passOnStoreError: true,
    }));
    testApp.get('/test-limit', (req, res) => res.json({ status: 'ok' }));

    const r1 = await request(testApp).get('/test-limit');
    expect(r1.status).toBe(200);

    const r2 = await request(testApp).get('/test-limit');
    expect(r2.status).toBe(200);

    const r3 = await request(testApp).get('/test-limit');
    expect(r3.status).toBe(429);
  });
});

