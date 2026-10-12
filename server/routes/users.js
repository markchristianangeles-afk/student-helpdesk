const express = require('express');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { validateEmailOrThrow } = require('../utils/validateEmail');

const router = express.Router();

router.get('/', requireAuth, requireRole('super_admin'), (req, res) => {
  const { role, q } = req.query;
  let sql = 'SELECT id, name, email, role, is_active, created_at FROM users WHERE 1=1';
  const params = [];
  if (role) { sql += ' AND role = ?'; params.push(role); }
  if (q) { sql += ' AND (name LIKE ? OR email LIKE ?)'; params.push(`%${q}%`, `%${q}%`); }
  sql += ' ORDER BY created_at DESC';
  const users = db.prepare(sql).all(...params);
  res.json({ users });
});

router.get('/assignable', requireAuth, requireRole('super_admin', 'admin'), (req, res) => {
  const users = db.prepare(
    `SELECT id, name, email, role FROM users WHERE role IN ('admin','super_admin') AND is_active = 1 ORDER BY name`
  ).all();
  res.json({ users });
});

router.post('/', requireAuth, requireRole('super_admin'), async (req, res) => {
  const { name, email, password, role } = req.body || {};
  if (!name || !email || !password || !role) {
    return res.status(400).json({ error: 'Name, email, password and role are required' });
  }
  if (!['super_admin', 'admin', 'student'].includes(role)) {
    return res.status(400).json({ error: 'Invalid role' });
  }
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    await validateEmailOrThrow(email);
  } catch (err) {
    return res.status(err.status || 400).json({ error: err.message });
  }

  const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email.toLowerCase());
  if (existing) return res.status(409).json({ error: 'An account with this email already exists' });

  const hash = bcrypt.hashSync(password, 10);
  const info = db.prepare(`INSERT INTO users (name, email, password_hash, role, is_active, created_at)
                            VALUES (?, ?, ?, ?, 1, ?)`)
    .run(name, email.toLowerCase(), hash, role, new Date().toISOString());

  const user = db.prepare('SELECT id, name, email, role, is_active, created_at FROM users WHERE id = ?')
    .get(info.lastInsertRowid);
  res.status(201).json({ user });
});

router.patch('/:id', requireAuth, requireRole('super_admin'), (req, res) => {
  const id = Number(req.params.id);
  const target = db.prepare('SELECT * FROM users WHERE id = ?').get(id);
  if (!target) return res.status(404).json({ error: 'User not found' });

  const { name, role, is_active, password } = req.body || {};

  if (role && !['super_admin', 'admin', 'student'].includes(role)) {
    return res.status(400).json({ error: 'Invalid role' });
  }

  const demotingOrDeactivatingSuperAdmin =
    target.role === 'super_admin' &&
    ((role && role !== 'super_admin') || is_active === 0 || is_active === false);

  if (demotingOrDeactivatingSuperAdmin) {
    const otherSuperAdmins = db.prepare(
      "SELECT COUNT(*) AS c FROM users WHERE role = 'super_admin' AND id != ?"
    ).get(id).c;
    if (otherSuperAdmins === 0) {
      return res.status(400).json({ error: 'Cannot demote or deactivate the last Super Admin' });
    }
  }

  const next = {
    name: name !== undefined ? name : target.name,
    role: role !== undefined ? role : target.role,
    is_active: is_active !== undefined ? (is_active ? 1 : 0) : target.is_active,
  };

  db.prepare('UPDATE users SET name = ?, role = ?, is_active = ? WHERE id = ?')
    .run(next.name, next.role, next.is_active, id);

  if (password) {
    if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters' });
    const hash = bcrypt.hashSync(password, 10);
    db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(hash, id);
  }

  const user = db.prepare('SELECT id, name, email, role, is_active, created_at FROM users WHERE id = ?').get(id);
  res.json({ user });
});

module.exports = router;
