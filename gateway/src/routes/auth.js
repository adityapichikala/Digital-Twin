'use strict';

const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const { sendError } = require('../utils/errorResponse');

const router = Router();
const target = process.env.AUTH_SERVICE_URL || 'http://localhost:8081';

const proxy = createProxyMiddleware({
  target,
  changeOrigin: true,
  on: {
    proxyReq: (proxyReq, req) => {
      if (req.id) {
        proxyReq.setHeader('X-Request-Id', req.id);
      }
    },
    error: (err, req, res) => {
      sendError(res, 502, 'Bad Gateway', `auth-service unavailable: ${err.message}`, req);
    },
  },
});

router.use('/', proxy);

module.exports = router;
