'use strict';

const jwt = require('jsonwebtoken');
const logger = require('../logger');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('JWT_SECRET environment variable is required');
}

/**
 * Express middleware that validates the Bearer JWT on every protected request.
 * Attaches decoded payload to req.user so downstream handlers can inspect role/username.
 */
function jwtVerify(req, res, next) {
  const header = req.headers['authorization'];
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing or malformed Authorization header' });
  }

  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
    req.user = payload;
    logger.debug({ message: 'JWT verified', sub: payload.sub, role: payload.role });
    next();
  } catch (err) {
    logger.warn({ message: 'JWT verification failed', reason: err.message });
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

module.exports = jwtVerify;
