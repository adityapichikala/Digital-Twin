'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const { sendError } = require('../utils/errorResponse');

const router = Router();
const target = process.env.CLUSTER_SYNC_SERVICE_URL || 'http://localhost:8082';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  pathRewrite: { '^/api/v1/cluster': '/api/v1/sync' },
  on: {
    proxyReq: (proxyReq, req) => {
      if (req.id) {
        proxyReq.setHeader('X-Request-Id', req.id);
      }
      if (req.user) {
        proxyReq.setHeader('X-User-Id', req.user.sub || '');
        proxyReq.setHeader('X-User-Role', req.user.role || '');
      }
    },
    error: (err, req, res) => {
      sendError(res, 502, 'Bad Gateway', `cluster-sync-service unavailable: ${err.message}`, req);
    },
  },
});

router.use('/', proxy);

module.exports = router;
