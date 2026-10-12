const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const logActivity = require('../utils/logActivity');

const router = express.Router();

router.get('/', requireAuth, (req, res) => {
  const categories = db.prepare(`
    SELECT c.*, COUNT(t.id) AS ticket_count
    FROM categories c
    LEFT JOIN tickets t ON t.category_id = c.id
    GROUP BY c.id
    ORDER BY c.name
  `).all();
  res.json({ categories });
});

router.post('/', requireAuth, requireRole('super_admin'), (req, res) => {
  const { name } = req.body || {};
  if (!name || !name.trim()) return res.status(400).json({ error: 'Category name is required' });
  try {
    const info = db.prepare('INSERT INTO categories (name) VALUES (?)').run(name.trim());
    logActivity(req.user, 'create', 'category', info.lastInsertRowid, `Created category "${name.trim()}"`);
    res.status(201).json({ category: { id: info.lastInsertRowid, name: name.trim() } });
  } catch (err) {
    res.status(409).json({ error: 'Category already exists' });
  }
});

router.patch('/:id', requireAuth, requireRole('super_admin'), (req, res) => {
  const id = Number(req.params.id);
  const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
  if (!category) return res.status(404).json({ error: 'Category not found' });

  const { name } = req.body || {};
  if (!name || !name.trim()) return res.status(400).json({ error: 'Category name is required' });

  const existing = db.prepare('SELECT id FROM categories WHERE name = ? AND id != ?').get(name.trim(), id);
  if (existing) return res.status(409).json({ error: 'A category with this name already exists' });

  db.prepare('UPDATE categories SET name = ? WHERE id = ?').run(name.trim(), id);

  if (name.trim() !== category.name) {
    logActivity(req.user, 'update', 'category', id, `Renamed category "${category.name}" to "${name.trim()}"`);
  }

  res.json({ category: { id, name: name.trim() } });
});

router.delete('/:id', requireAuth, requireRole('super_admin'), (req, res) => {
  const id = Number(req.params.id);
  const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
  if (!category) return res.status(404).json({ error: 'Category not found' });

  db.prepare('UPDATE tickets SET category_id = NULL WHERE category_id = ?').run(id);
  db.prepare('DELETE FROM categories WHERE id = ?').run(id);

  logActivity(req.user, 'delete', 'category', id, `Deleted category "${category.name}"`);

  res.json({ ok: true });
});

module.exports = router;
