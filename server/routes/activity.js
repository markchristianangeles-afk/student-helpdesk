const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, requireRole('admin', 'super_admin'), async (req, res) => {
  const { entity_type, action, q } = req.query;
  let sql = 'SELECT * FROM activity_log WHERE 1=1';
  const params = [];

  if (entity_type) { params.push(entity_type); sql += ` AND entity_type = $${params.length}`; }
  if (action) { params.push(action); sql += ` AND action = $${params.length}`; }
  if (q) {
    params.push(`%${q}%`);
    sql += ` AND (message ILIKE $${params.length} OR actor_name ILIKE $${params.length})`;
  }

  sql += ' ORDER BY created_at DESC LIMIT 200';
  const { rows } = await pool.query(sql, params);
  res.json({ entries: rows });
});

module.exports = router;
