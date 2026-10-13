'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const router = Router();
const target = process.env.TOPOLOGY_SERVICE_URL || 'http://localhost:8083';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  on: {
    error: (err, _req, res) => {
      res.status(502).json({ error: 'topology-service unavailable', detail: err.message });
    },
  },
});

router.use('/', proxy);

module.exports = router;
