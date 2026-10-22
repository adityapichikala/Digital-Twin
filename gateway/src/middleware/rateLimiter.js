'use strict';

const { rateLimit, MemoryStore } = require('express-rate-limit');
const { RedisStore } = require('rate-limit-redis');
const Redis = require('ioredis');
const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

/**
 * ResilientStore wraps a primary store (e.g. RedisStore) and automatically
 * falls back to an in-memory MemoryStore if Redis is unavailable or throws errors.
 */
class ResilientStore {
  constructor(primaryStore) {
    this.primaryStore = primaryStore;
    this.fallbackStore = new MemoryStore();
  }

  init(options) {
    if (this.primaryStore && typeof this.primaryStore.init === 'function') {
      this.primaryStore.init(options);
    }
    if (this.fallbackStore && typeof this.fallbackStore.init === 'function') {
      this.fallbackStore.init(options);
    }
  }

  async get(key) {
    if (this.primaryStore && typeof this.primaryStore.get === 'function') {
      try {
        return await this.primaryStore.get(key);
      } catch (err) {
        logger.warn({ message: 'Primary rate-limit store failed during get; falling back to memory', error: err.message });
      }
    }
    if (typeof this.fallbackStore.get === 'function') {
      return this.fallbackStore.get(key);
    }
  }

  async increment(key) {
    if (this.primaryStore) {
      try {
        return await this.primaryStore.increment(key);
      } catch (err) {
        logger.warn({ message: 'Primary rate-limit store failed during increment; falling back to memory', error: err.message });
      }
    }
    return this.fallbackStore.increment(key);
  }

  async decrement(key) {
    if (this.primaryStore && typeof this.primaryStore.decrement === 'function') {
      try {
        return await this.primaryStore.decrement(key);
      } catch (err) {
        logger.warn({ message: 'Primary rate-limit store failed during decrement; falling back to memory', error: err.message });
      }
    }
    if (typeof this.fallbackStore.decrement === 'function') {
      return this.fallbackStore.decrement(key);
    }
  }

  async resetKey(key) {
    if (this.primaryStore && typeof this.primaryStore.resetKey === 'function') {
      try {
        return await this.primaryStore.resetKey(key);
      } catch (err) {
        logger.warn({ message: 'Primary rate-limit store failed during resetKey; falling back to memory', error: err.message });
      }
    }
    return this.fallbackStore.resetKey(key);
  }

  async resetAll() {
    if (this.primaryStore && typeof this.primaryStore.resetAll === 'function') {
      try {
        return await this.primaryStore.resetAll();
      } catch (err) {
        logger.warn({ message: 'Primary rate-limit store failed during resetAll; falling back to memory', error: err.message });
      }
    }
    if (typeof this.fallbackStore.resetAll === 'function') {
      return this.fallbackStore.resetAll();
    }
  }
}

let redisClient = null;

if (process.env.REDIS_URL && process.env.NODE_ENV !== 'test') {
  try {
    redisClient = new Redis(process.env.REDIS_URL, {
      lazyConnect: true,
      maxRetriesPerRequest: 2,
    });
    redisClient.on('error', (err) => {
      logger.error({ message: 'Redis rate-limit error', error: err.message });
    });
    logger.info({ message: 'Rate limiter configured with Redis store' });
  } catch (err) {
    logger.warn({ message: 'Could not connect to Redis for rate limiting, using memory store', error: err.message });
  }
} else {
  logger.warn({ message: 'REDIS_URL not set; using in-memory rate limiter store' });
}

// Distinct Redis prefixes per limiter wrapped in ResilientStore for real in-memory fallback
const ipStore = new ResilientStore(
  redisClient
    ? new RedisStore({
        sendCommand: (...args) => redisClient.call(...args),
        prefix: 'rl:ip:',
      })
    : null
);

const userStore = new ResilientStore(
  redisClient
    ? new RedisStore({
        sendCommand: (...args) => redisClient.call(...args),
        prefix: 'rl:usr:',
      })
    : null
);

/**
 * Standard rate limiter per IP (used for public auth/login endpoints).
 */
const ipRateLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  max: parseInt(process.env.RATE_LIMIT_MAX_IP || '60', 10),
  standardHeaders: true,
  legacyHeaders: false,
  passOnStoreError: true,
  store: ipStore,
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
  passOnStoreError: true,
  store: userStore,
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

module.exports = { ipRateLimiter, userRateLimiter, ResilientStore };

