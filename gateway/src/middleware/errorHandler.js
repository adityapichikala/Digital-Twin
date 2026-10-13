'use strict';

const logger = require('../logger');

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  logger.error({
    message: 'Unhandled error in gateway',
    error: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method,
  });
  const status = err.status || err.statusCode || 500;
  res.status(status).json({ error: err.message || 'Internal gateway error' });
}

module.exports = errorHandler;
