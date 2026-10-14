const asyncRouter = require('../utils/asyncRouter');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const logActivity = require('../utils/logActivity');

const router = asyncRouter();

router.get('/', requireAuth, async (req, res) => {
  const { rows } = await pool.query(`
    SELECT c.*, COUNT(t.id) AS ticket_count
    FROM categories c
    LEFT JOIN tickets t ON t.category_id = c.id
    GROUP BY c.id
    ORDER BY c.name
  `);
  res.json({ categories: rows.map(r => ({ ...r, ticket_count: Number(r.ticket_count) })) });
});

router.post('/', requireAuth, requireRole('super_admin'), async (req, res) => {
  const { name } = req.body || {};
  if (!name || !name.trim()) return res.status(400).json({ error: 'Category name is required' });
  try {
    const { rows } = await pool.query('INSERT INTO categories (name) VALUES ($1) RETURNING id', [name.trim()]);
    await logActivity(req.user, 'create', 'category', rows[0].id, `Created category "${name.trim()}"`);
    res.status(201).json({ category: { id: rows[0].id, name: name.trim() } });
  } catch (err) {
    res.status(409).json({ error: 'Category already exists' });
  }
});

router.patch('/:id', requireAuth, requireRole('super_admin'), async (req, res) => {
  const id = Number(req.params.id);
  const { rows: catRows } = await pool.query('SELECT * FROM categories WHERE id = $1', [id]);
  const category = catRows[0];
  if (!category) return res.status(404).json({ error: 'Category not found' });

  const { name } = req.body || {};
  if (!name || !name.trim()) return res.status(400).json({ error: 'Category name is required' });

  const existing = await pool.query('SELECT id FROM categories WHERE name = $1 AND id != $2', [name.trim(), id]);
  if (existing.rows.length > 0) return res.status(409).json({ error: 'A category with this name already exists' });

  await pool.query('UPDATE categories SET name = $1 WHERE id = $2', [name.trim(), id]);

  if (name.trim() !== category.name) {
    await logActivity(req.user, 'update', 'category', id, `Renamed category "${category.name}" to "${name.trim()}"`);
  }

  res.json({ category: { id, name: name.trim() } });
});

router.delete('/:id', requireAuth, requireRole('super_admin'), async (req, res) => {
  const id = Number(req.params.id);
  const { rows: catRows } = await pool.query('SELECT * FROM categories WHERE id = $1', [id]);
  const category = catRows[0];
  if (!category) return res.status(404).json({ error: 'Category not found' });

  await pool.query('UPDATE tickets SET category_id = NULL WHERE category_id = $1', [id]);
  await pool.query('DELETE FROM categories WHERE id = $1', [id]);

  await logActivity(req.user, 'delete', 'category', id, `Deleted category "${category.name}"`);

  res.json({ ok: true });
});

module.exports = router;
