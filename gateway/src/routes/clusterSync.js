'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const router = Router();
const target = process.env.CLUSTER_SYNC_SERVICE_URL || 'http://localhost:8082';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  pathRewrite: { '^/api/v1/cluster': '/api/v1/sync' },
  on: {
    error: (err, _req, res) => {
      res.status(502).json({ error: 'cluster-sync-service unavailable', detail: err.message });
    },
  },
});

router.use('/', proxy);

module.exports = router;
