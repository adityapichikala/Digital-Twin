'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const router = Router();
const target = process.env.COST_RISK_SERVICE_URL || 'http://localhost:8085';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  on: {
    error: (err, _req, res) => {
      res.status(502).json({ error: 'cost-risk-service unavailable', detail: err.message });
    },
  },
});

router.use('/', proxy);

module.exports = router;
