'use strict';

const rateLimit = require('express-rate-limit');
const { RedisStore } = require('rate-limit-redis');
const Redis = require('ioredis');
const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

let store;

// Connect to Redis if REDIS_URL is provided; gracefully fall back to MemoryStore
if (process.env.REDIS_URL && process.env.NODE_ENV !== 'test') {
  try {
    const redisClient = new Redis(process.env.REDIS_URL, {
      lazyConnect: true,
      maxRetriesPerRequest: 2,
    });
    redisClient.on('error', (err) => {
      logger.error({ message: 'Redis rate-limit error', error: err.message });
    });
    store = new RedisStore({
      sendCommand: (...args) => redisClient.call(...args),
    });
    logger.info({ message: 'Rate limiter using Redis store' });
  } catch (err) {
    logger.warn({ message: 'Could not connect to Redis for rate limiting, using memory store', error: err.message });
  }
} else {
  logger.warn({ message: 'REDIS_URL not set; using in-memory rate limiter store' });
}

/**
 * Standard rate limiter per IP (used for public auth/login endpoints).
 */
const ipRateLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  max: parseInt(process.env.RATE_LIMIT_MAX_IP || '60', 10),
  standardHeaders: true,
  legacyHeaders: false,
  store,
  keyGenerator: (req) => req.ip || req.headers['x-forwarded-for'] || 'unknown-ip',
  handler: (req, res) => {
    sendError(res, 429, 'Too Many Requests', 'Too many requests from this IP — please try again later', req);
  },
});

/**
 * Rate limiter per authenticated user (falls back to IP if unauthenticated).
 */
const userRateLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  max: parseInt(process.env.RATE_LIMIT_MAX_USER || '120', 10),
  standardHeaders: true,
  legacyHeaders: false,
  store,
  keyGenerator: (req) => {
    if (req.user && (req.user.sub || req.user.username)) {
      return `usr:${req.user.sub || req.user.username}`;
    }
    return `ip:${req.ip || req.headers['x-forwarded-for'] || 'unknown'}`;
  },
  handler: (req, res) => {
    sendError(res, 429, 'Too Many Requests', 'User rate limit exceeded — please try again later', req);
  },
});

module.exports = { ipRateLimiter, userRateLimiter };
