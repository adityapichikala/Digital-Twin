'use strict';

const request = require('supertest');

// Stub env before requiring the app
process.env.JWT_SECRET = '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40';
process.env.PORT = '0'; // random port to avoid conflicts

const { app, server } = require('../index');

afterAll(() => server.close());

describe('GET /health', () => {
  it('returns 200 with status UP', async () => {
    const res = await request(app).get('/health');
    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe('UP');
    expect(res.body.service).toBe('twindigital-gateway');
  });
});

describe('GET /metrics', () => {
  it('returns 200 with prometheus text', async () => {
    const res = await request(app).get('/metrics');
    expect(res.statusCode).toBe(200);
    expect(res.text).toContain('gateway_http_requests_total');
  });
});

describe('Protected routes without JWT', () => {
  it('returns 401 on /api/v1/topology/graph without token', async () => {
    const res = await request(app).get('/api/v1/topology/graph');
    expect(res.statusCode).toBe(401);
  });
});
