'use strict';

require('express-async-errors');
require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');

const logger = require('./logger');
const metrics = require('./middleware/metrics');
const requestIdMiddleware = require('./middleware/requestId');
const { ipRateLimiter, userRateLimiter } = require('./middleware/rateLimiter');
const jwtVerify = require('./middleware/jwtVerify');
const { requireRole } = require('./middleware/rbac');
const errorHandler = require('./middleware/errorHandler');
const { sendError } = require('./utils/errorResponse');

const authRoutes = require('./routes/auth');
const clusterSyncRoutes = require('./routes/clusterSync');
const topologyRoutes = require('./routes/topology');
const simulationRoutes = require('./routes/simulation');
const costRiskRoutes = require('./routes/costRisk');

const app = express();

// ── 1. Security Headers & CORS ────────────────────────────────────────────────
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ALLOWED_ORIGINS ? process.env.CORS_ALLOWED_ORIGINS.split(',') : '*',
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Request-Id'],
  exposedHeaders: ['X-Request-Id'],
  credentials: true,
}));

// ── 2. Request ID Tracking ────────────────────────────────────────────────────
app.use(requestIdMiddleware);

// ── 3. Structured Request Logging ─────────────────────────────────────────────
app.use(morgan(':method :url :status :res[content-length] - :response-time ms [req: :req[x-request-id]]', {
  stream: { write: (msg) => logger.http(msg.trim()) }
}));

// ── 4. Body Parsing ───────────────────────────────────────────────────────────
app.use(express.json({ limit: '1mb' }));

// ── 5. Prometheus Metrics Collection ──────────────────────────────────────────
app.use(metrics.requestMiddleware);

// ── 6. Public Endpoints (Health & Metrics) ────────────────────────────────────
app.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    service: 'twindigital-gateway',
    timestamp: new Date().toISOString(),
    requestId: req.id,
  });
});

app.get('/metrics', async (_req, res) => {
  res.set('Content-Type', metrics.registry.contentType);
  res.end(await metrics.registry.metrics());
});

// ── 7. Public Auth Routes (Rate limited per IP, no JWT required) ──────────────
// Allows login, register, and refresh without pre-existing JWT
app.use('/api/v1/auth', ipRateLimiter, authRoutes);
app.use('/api/auth', ipRateLimiter, authRoutes);

// ── 8. Authenticated API Gateway Routes ───────────────────────────────────────
// All routes below require valid JWT and enforce per-user rate limiting
app.use('/api/v1', jwtVerify, userRateLimiter);

// VIEWER and ADMIN can read topology, cluster sync state, and cost assessments
app.use('/api/v1/topology', requireRole('VIEWER', 'ADMIN'), topologyRoutes);
app.use('/api/topology', jwtVerify, userRateLimiter, requireRole('VIEWER', 'ADMIN'), topologyRoutes);
app.use('/api/v1/cluster', requireRole('VIEWER', 'ADMIN'), clusterSyncRoutes);
app.use('/api/v1/assessments', requireRole('VIEWER', 'ADMIN'), costRiskRoutes);
app.use('/api/v1/cost', requireRole('VIEWER', 'ADMIN'), costRiskRoutes);
app.use('/api/cost', jwtVerify, userRateLimiter, requireRole('VIEWER', 'ADMIN'), costRiskRoutes);

// ONLY ADMIN can access simulations
app.use('/api/v1/simulations', requireRole('ADMIN'), simulationRoutes);
app.use('/api/simulations', jwtVerify, userRateLimiter, requireRole('ADMIN'), simulationRoutes);

// ── 9. Fallback 404 Handler ───────────────────────────────────────────────────
app.use((req, res) => {
  sendError(res, 404, 'Not Found', `Endpoint not found: ${req.method} ${req.originalUrl || req.url}`, req);
});

// ── 10. Global Error Handler ──────────────────────────────────────────────────
app.use(errorHandler);

// ── Server Startup ────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
let server;

// Only listen if not in test mode or if PORT is explicitly set
if (process.env.NODE_ENV !== 'test') {
  server = app.listen(PORT, () => {
    logger.info({ message: 'Gateway started', port: PORT });
  });
} else if (process.env.PORT === '0' || process.env.PORT) {
  server = app.listen(PORT, () => {
    logger.info({ message: 'Gateway started (test mode)', port: PORT });
  });
}

module.exports = { app, server };
