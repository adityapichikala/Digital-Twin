'use strict';

const crypto = require('crypto');

/**
 * Request ID propagation middleware.
 * Uses incoming X-Request-Id header or generates a new UUIDv4.
 * Attaches req.id and sets X-Request-Id response header.
 */
function requestIdMiddleware(req, res, next) {
  const incomingId = req.headers['x-request-id'];
  const requestId = (incomingId && typeof incomingId === 'string' && incomingId.trim().length > 0)
    ? incomingId.trim()
    : crypto.randomUUID();

  req.id = requestId;
  res.setHeader('X-Request-Id', requestId);
  next();
}

module.exports = requestIdMiddleware;
