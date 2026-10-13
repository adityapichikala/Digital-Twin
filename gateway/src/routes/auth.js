'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const router = Router();
const target = process.env.AUTH_SERVICE_URL || 'http://localhost:8081';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  on: {
    error: (err, _req, res) => {
      res.status(502).json({ error: 'auth-service unavailable', detail: err.message });
    },
  },
});

// Proxy all /api/v1/auth/* → auth-service:/api/v1/auth/*
router.use('/', proxy);

module.exports = router;
