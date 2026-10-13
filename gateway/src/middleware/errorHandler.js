'use strict';

const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  logger.error({
    message: 'Unhandled error in gateway',
    error: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method,
    requestId: req.id,
  });

  const status = err.status || err.statusCode || 500;
  const errorTitle = status >= 500 ? 'Internal Server Error' : 'Request Error';
  sendError(res, status, errorTitle, err.message || 'An unexpected error occurred in the gateway', req);
}

module.exports = errorHandler;
