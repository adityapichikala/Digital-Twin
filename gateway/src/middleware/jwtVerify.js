'use strict';

const jwt = require('jsonwebtoken');
const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET environment variable is required');
}

/**
 * Express middleware that validates the Bearer JWT on protected requests.
 * Attaches decoded payload (sub, username, role) to req.user.
 */
function jwtVerify(req, res, next) {
  const header = req.headers['authorization'];
  if (!header || !header.startsWith('Bearer ')) {
    return sendError(res, 401, 'Unauthorized', 'Missing or malformed Authorization header', req);
  }

  const token = header.slice(7).trim();
  if (!token) {
    return sendError(res, 401, 'Unauthorized', 'Token is empty', req);
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
    req.user = payload;
    logger.debug({
      message: 'JWT verified',
      sub: payload.sub,
      role: payload.role,
      requestId: req.id,
    });
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      logger.warn({ message: 'JWT verification failed: expired', requestId: req.id });
      return sendError(res, 401, 'Unauthorized', 'Token has expired', req);
    }
    logger.warn({ message: 'JWT verification failed: invalid/forged', reason: err.message, requestId: req.id });
    return sendError(res, 401, 'Unauthorized', 'Invalid or forged token', req);
  }
}

module.exports = jwtVerify;
