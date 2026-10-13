'use strict';

const rateLimit = require('express-rate-limit');
const { RedisStore } = require('rate-limit-redis');
const Redis = require('ioredis');
const logger = require('../logger');

let store;

// Use Redis-backed store in production; fall back to memory store if no REDIS_URL
if (process.env.REDIS_URL) {
  try {
    const redisClient = new Redis(process.env.REDIS_URL, { lazyConnect: true });
    redisClient.on('error', (err) => logger.error({ message: 'Redis rate-limit error', error: err.message }));
    store = new RedisStore({
      sendCommand: (...args) => redisClient.call(...args),
    });
    logger.info({ message: 'Rate limiter using Redis store' });
  } catch (err) {
    logger.warn({ message: 'Could not connect to Redis for rate limiting, using memory store', error: err.message });
  }
} else {
  logger.warn({ message: 'REDIS_URL not set; using in-memory rate limiter (not suitable for multi-instance)' });
}

const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  max: parseInt(process.env.RATE_LIMIT_MAX || '100', 10),
  standardHeaders: true,
  legacyHeaders: false,
  store,
  handler: (_req, res) => {
    res.status(429).json({ error: 'Too many requests — please try again later' });
  },
});

module.exports = limiter;
