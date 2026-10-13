'use strict';

const promClient = require('prom-client');

const registry = new promClient.Registry();
promClient.collectDefaultMetrics({ register: registry });

const httpRequestDuration = new promClient.Histogram({
  name: 'gateway_http_request_duration_seconds',
  help: 'Duration of HTTP requests through the gateway in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  registers: [registry],
});

const httpRequestCount = new promClient.Counter({
  name: 'gateway_http_requests_total',
  help: 'Total count of HTTP requests processed by the API gateway',
  labelNames: ['method', 'route', 'status_code'],
  registers: [registry],
});

const httpErrorCount = new promClient.Counter({
  name: 'gateway_http_errors_total',
  help: 'Total count of HTTP 4xx and 5xx errors returned by the gateway',
  labelNames: ['method', 'route', 'status_code', 'error_type'],
  registers: [registry],
});

function requestMiddleware(req, res, next) {
  const start = process.hrtime();

  res.on('finish', () => {
    const diff = process.hrtime(start);
    const durationSeconds = diff[0] + diff[1] / 1e9;

    const routePath = req.baseUrl
      ? `${req.baseUrl}${req.route ? req.route.path : ''}`
      : (req.route ? req.route.path : req.path || '/');

    const statusCode = String(res.statusCode);

    const labels = {
      method: req.method,
      route: routePath,
      status_code: statusCode,
    };

    httpRequestDuration.observe(labels, durationSeconds);
    httpRequestCount.inc(labels);

    if (res.statusCode >= 500) {
      httpErrorCount.inc({ ...labels, error_type: '5xx' });
    } else if (res.statusCode >= 400) {
      httpErrorCount.inc({ ...labels, error_type: '4xx' });
    }
  });

  next();
}

module.exports = { registry, requestMiddleware, httpRequestCount, httpRequestDuration, httpErrorCount };
