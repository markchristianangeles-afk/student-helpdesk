const express = require('express');

// Express 4 does not catch rejected promises from async handlers: an awaited
// failure becomes an unhandled rejection, which Node 20 treats as fatal, so a
// single failing query took the whole process down. Routers built here forward
// those rejections to the error middleware instead, and any route added later
// is covered without having to remember a wrapper.
function wrap(handler) {
  if (typeof handler !== 'function') return handler;
  // Error-handling middleware is identified by its arity; leave it intact.
  if (handler.length === 4) return handler;
  return function (req, res, next) {
    Promise.resolve(handler(req, res, next)).catch(next);
  };
}

const METHODS = ['get', 'post', 'put', 'patch', 'delete', 'all', 'use'];

function asyncRouter() {
  const router = express.Router();
  for (const method of METHODS) {
    const original = router[method].bind(router);
    router[method] = (...args) => original(...args.map(wrap));
  }
  return router;
}

module.exports = asyncRouter;
