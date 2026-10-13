'use strict';

const logger = require('../logger');
const { sendError } = require('../utils/errorResponse');

/**
 * Middleware factory that restricts access to users possessing at least one of the specified roles.
 *
 * @param {...string} allowedRoles (e.g. 'ADMIN', 'VIEWER')
 */
function requireRole(...allowedRoles) {
  const normalizedAllowed = allowedRoles.map((r) => r.toUpperCase());

  return (req, res, next) => {
    if (!req.user || !req.user.role) {
      logger.warn({
        message: 'RBAC denied: user or role missing in request',
        requestId: req.id,
      });
      return sendError(res, 401, 'Unauthorized', 'User identity or role missing from token', req);
    }

    const userRole = String(req.user.role).trim().toUpperCase();
    if (!normalizedAllowed.includes(userRole)) {
      logger.warn({
        message: 'RBAC forbidden',
        userRole,
        requiredRoles: normalizedAllowed,
        path: req.originalUrl || req.url,
        requestId: req.id,
      });
      return sendError(
        res,
        403,
        'Forbidden',
        `Access denied: only [${normalizedAllowed.join(', ')}] role is authorized to perform this operation`,
        req
      );
    }

    next();
  };
}

module.exports = { requireRole };
