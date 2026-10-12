const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/summary', requireAuth, requireRole('admin', 'super_admin'), (req, res) => {
  const total = db.prepare('SELECT COUNT(*) AS c FROM tickets').get().c;

  const byStatus = db.prepare('SELECT status, COUNT(*) AS c FROM tickets GROUP BY status').all();
  const byPriority = db.prepare('SELECT priority, COUNT(*) AS c FROM tickets GROUP BY priority').all();
  const byCategory = db.prepare(`
    SELECT COALESCE(c.name, 'Uncategorized') AS name, COUNT(*) AS c
    FROM tickets t LEFT JOIN categories c ON c.id = t.category_id
    GROUP BY c.name
  `).all();

  const byAdmin = db.prepare(`
    SELECT u.name, COUNT(*) AS c
    FROM tickets t JOIN users u ON u.id = t.assigned_to
    GROUP BY u.id ORDER BY c DESC
  `).all();

  const avgRow = db.prepare(`
    SELECT AVG((julianday(resolved_at) - julianday(created_at)) * 24) AS avg_hours
    FROM tickets WHERE resolved_at IS NOT NULL
  `).get();

  const trend = db.prepare(`
    SELECT substr(created_at, 1, 10) AS day, COUNT(*) AS c
    FROM tickets
    WHERE created_at >= datetime('now', '-14 days')
    GROUP BY day ORDER BY day ASC
  `).all();

  const unassigned = db.prepare(
    "SELECT COUNT(*) AS c FROM tickets WHERE assigned_to IS NULL AND status NOT IN ('resolved','closed')"
  ).get().c;

  res.json({
    total,
    unassigned,
    byStatus,
    byPriority,
    byCategory,
    byAdmin,
    avgResolutionHours: avgRow.avg_hours || 0,
    trend,
  });
});

module.exports = router;
