'use strict';

/**
 * Sends a standardized, consistent JSON error response.
 *
 * @param {import('express').Response} res
 * @param {number} status
 * @param {string} error
 * @param {string} message
 * @param {import('express').Request} [req]
 */
function sendError(res, status, error, message, req) {
  const requestId = req?.id || req?.headers?.['x-request-id'] || 'req-unknown';
  return res.status(status).json({
    error,
    message,
    requestId,
    timestamp: new Date().toISOString(),
  });
}

module.exports = { sendError };
