'use strict';

require('express-async-errors');
require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const morgan = require('morgan');

const logger = require('./logger');
const metrics = require('./middleware/metrics');
const rateLimiter = require('./middleware/rateLimiter');
const jwtVerify = require('./middleware/jwtVerify');
const errorHandler = require('./middleware/errorHandler');

const authRoutes = require('./routes/auth');
const clusterSyncRoutes = require('./routes/clusterSync');
const topologyRoutes = require('./routes/topology');
const simulationRoutes = require('./routes/simulation');
const costRiskRoutes = require('./routes/costRisk');

const app = express();

// ── Security headers ──────────────────────────────────────────────────────────
app.use(helmet());

// ── Request logging (structured JSON via Winston) ─────────────────────────────
app.use(morgan('combined', {
  stream: { write: (msg) => logger.http(msg.trim()) }
}));

// ── Body parsing ──────────────────────────────────────────────────────────────
app.use(express.json({ limit: '1mb' }));

// ── Prometheus metrics collection ─────────────────────────────────────────────
app.use(metrics.requestMiddleware);

// ── Health & metrics (public, no auth) ───────────────────────────────────────
app.get('/health', (_req, res) => {
  res.json({ status: 'UP', service: 'twindigital-gateway', timestamp: new Date().toISOString() });
});

app.get('/metrics', async (_req, res) => {
  res.set('Content-Type', metrics.registry.contentType);
  res.end(await metrics.registry.metrics());
});

// ── Rate limiting ─────────────────────────────────────────────────────────────
app.use(rateLimiter);

// ── JWT verification (applied to all routes below) ────────────────────────────
// Auth login/register are proxied WITHOUT the JWT middleware so users can obtain tokens
app.use('/api/v1/auth', authRoutes);

// All other API routes require a valid JWT
app.use('/api/v1', jwtVerify);
app.use('/api/v1/cluster', clusterSyncRoutes);
app.use('/api/v1/topology', topologyRoutes);
app.use('/api/v1/simulations', simulationRoutes);
app.use('/api/v1/assessments', costRiskRoutes);

// ── Global error handler ──────────────────────────────────────────────────────
app.use(errorHandler);

// ── Start ─────────────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
const server = app.listen(PORT, () => {
  logger.info({ message: 'Gateway started', port: PORT });
});

module.exports = { app, server }; // export for tests
