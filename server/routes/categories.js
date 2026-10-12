const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, (req, res) => {
  const categories = db.prepare('SELECT * FROM categories ORDER BY name').all();
  res.json({ categories });
});

router.post('/', requireAuth, requireRole('super_admin'), (req, res) => {
  const { name } = req.body || {};
  if (!name || !name.trim()) return res.status(400).json({ error: 'Category name is required' });
  try {
    const info = db.prepare('INSERT INTO categories (name) VALUES (?)').run(name.trim());
    res.status(201).json({ category: { id: info.lastInsertRowid, name: name.trim() } });
  } catch (err) {
    res.status(409).json({ error: 'Category already exists' });
  }
});

module.exports = router;
