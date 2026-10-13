const express = require('express');
const { pool } = require('../db');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT * FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT 50',
    [req.user.id]
  );
  res.json({ notifications: rows });
});

router.get('/unread-count', requireAuth, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT COUNT(*) AS c FROM notifications WHERE user_id = $1 AND is_read = 0',
    [req.user.id]
  );
  res.json({ count: Number(rows[0].c) });
});

router.patch('/:id/read', requireAuth, async (req, res) => {
  await pool.query('UPDATE notifications SET is_read = 1 WHERE id = $1 AND user_id = $2',
    [req.params.id, req.user.id]);
  res.json({ ok: true });
});

router.patch('/read-all', requireAuth, async (req, res) => {
  await pool.query('UPDATE notifications SET is_read = 1 WHERE user_id = $1', [req.user.id]);
  res.json({ ok: true });
});

module.exports = router;
