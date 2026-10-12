const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, requireRole('admin', 'super_admin'), (req, res) => {
  const { entity_type, action, q } = req.query;
  let sql = 'SELECT * FROM activity_log WHERE 1=1';
  const params = [];

  if (entity_type) { sql += ' AND entity_type = ?'; params.push(entity_type); }
  if (action) { sql += ' AND action = ?'; params.push(action); }
  if (q) { sql += ' AND (message LIKE ? OR actor_name LIKE ?)'; params.push(`%${q}%`, `%${q}%`); }

  sql += ' ORDER BY created_at DESC LIMIT 200';
  const entries = db.prepare(sql).all(...params);
  res.json({ entries });
});

module.exports = router;
