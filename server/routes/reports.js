const express = require('express');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/summary', requireAuth, requireRole('admin', 'super_admin'), async (req, res) => {
  const totalRes = await pool.query('SELECT COUNT(*) AS c FROM tickets');
  const total = Number(totalRes.rows[0].c);

  const byStatusRes = await pool.query('SELECT status, COUNT(*) AS c FROM tickets GROUP BY status');
  const byPriorityRes = await pool.query('SELECT priority, COUNT(*) AS c FROM tickets GROUP BY priority');
  const byCategoryRes = await pool.query(`
    SELECT COALESCE(c.name, 'Uncategorized') AS name, COUNT(*) AS c
    FROM tickets t LEFT JOIN categories c ON c.id = t.category_id
    GROUP BY c.name
  `);

  const byAdminRes = await pool.query(`
    SELECT u.name, COUNT(*) AS c
    FROM tickets t JOIN users u ON u.id = t.assigned_to
    GROUP BY u.id, u.name ORDER BY c DESC
  `);

  const avgRes = await pool.query(`
    SELECT AVG(EXTRACT(EPOCH FROM (resolved_at::timestamptz - created_at::timestamptz)) / 3600) AS avg_hours
    FROM tickets WHERE resolved_at IS NOT NULL
  `);

  const trendRes = await pool.query(`
    SELECT substr(created_at, 1, 10) AS day, COUNT(*) AS c
    FROM tickets
    WHERE created_at::timestamptz >= NOW() - INTERVAL '14 days'
    GROUP BY day ORDER BY day ASC
  `);

  const unassignedRes = await pool.query(
    "SELECT COUNT(*) AS c FROM tickets WHERE assigned_to IS NULL AND status NOT IN ('resolved','closed')"
  );

  res.json({
    total,
    unassigned: Number(unassignedRes.rows[0].c),
    byStatus: byStatusRes.rows.map(r => ({ status: r.status, c: Number(r.c) })),
    byPriority: byPriorityRes.rows.map(r => ({ priority: r.priority, c: Number(r.c) })),
    byCategory: byCategoryRes.rows.map(r => ({ name: r.name, c: Number(r.c) })),
    byAdmin: byAdminRes.rows.map(r => ({ name: r.name, c: Number(r.c) })),
    avgResolutionHours: Number(avgRes.rows[0].avg_hours) || 0,
    trend: trendRes.rows.map(r => ({ day: r.day, c: Number(r.c) })),
  });
});

module.exports = router;
