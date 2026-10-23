'use strict';

const jwt = require('jsonwebtoken');
const Redis = require('ioredis');
const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET environment variable is required');
}

let redisClient = null;
if (process.env.REDIS_URL && process.env.NODE_ENV !== 'test') {
  try {
    redisClient = new Redis(process.env.REDIS_URL, {
      lazyConnect: true,
      maxRetriesPerRequest: 1,
    });
    redisClient.on('error', (err) => {
      logger.warn({ message: 'Redis token revocation check error', error: err.message });
    });
  } catch (err) {
    logger.warn({ message: 'Could not connect to Redis for token revocation check', error: err.message });
  }
}

// In-memory set for revoked tokens (used in testing and local fallback)
const revokedJtis = new Set();

/**
 * Express middleware that validates the Bearer JWT on protected requests.
 * Attaches decoded payload (sub, username, role) to req.user.
 */
async function jwtVerify(req, res, next) {
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

    // Check token revocation (logout enforcement)
    if (payload.jti) {
      if (revokedJtis.has(payload.jti)) {
        logger.warn({ message: 'JWT rejected: token is revoked', jti: payload.jti, requestId: req.id });
        return sendError(res, 401, 'Unauthorized', 'Token has been revoked', req);
      }
      if (redisClient) {
        try {
          const isRevoked = await redisClient.get(`revoked:${payload.jti}`);
          if (isRevoked) {
            logger.warn({ message: 'JWT rejected: token is revoked in Redis', jti: payload.jti, requestId: req.id });
            return sendError(res, 401, 'Unauthorized', 'Token has been revoked', req);
          }
        } catch (err) {
          logger.warn({ message: 'Redis check failed during token verification', error: err.message });
        }
      }
    }

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

jwtVerify.revokeJti = (jti) => revokedJtis.add(jti);
jwtVerify.clearRevokedJtis = () => revokedJtis.clear();

module.exports = jwtVerify;
