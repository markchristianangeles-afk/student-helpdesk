const express = require('express');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAuth, JWT_SECRET } = require('../middleware/auth');
const { validateEmailOrThrow } = require('../utils/validateEmail');

const router = express.Router();

function issueToken(res, user) {
  const token = jwt.sign({ id: user.id }, JWT_SECRET, { expiresIn: '7d' });
  res.cookie('token', token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    maxAge: 7 * 24 * 60 * 60 * 1000,
  });
}

router.post('/register', async (req, res) => {
  const { name, email, password, section } = req.body || {};
  if (!name || !email || !password || !section || !section.trim()) {
    return res.status(400).json({ error: 'Name, email, password and section are required' });
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
  const info = db.prepare(`INSERT INTO users (name, email, password_hash, role, is_active, section, created_at)
                            VALUES (?, ?, ?, 'student', 1, ?, ?)`)
    .run(name, email.toLowerCase(), hash, section.trim(), new Date().toISOString());

  const user = db.prepare('SELECT id, name, email, role, section FROM users WHERE id = ?').get(info.lastInsertRowid);
  issueToken(res, user);
  res.status(201).json({ user });
});

router.post('/login', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) return res.status(400).json({ error: 'Email and password are required' });

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(email.toLowerCase());
  if (!user || !bcrypt.compareSync(password, user.password_hash)) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }
  if (!user.is_active) return res.status(403).json({ error: 'This account has been deactivated' });

  issueToken(res, user);
  res.json({ user: { id: user.id, name: user.name, email: user.email, role: user.role, section: user.section } });
});

router.post('/logout', (req, res) => {
  res.clearCookie('token');
  res.json({ ok: true });
});

router.get('/me', requireAuth, (req, res) => {
  res.json({ user: req.user });
});

router.patch('/me', requireAuth, async (req, res) => {
  const { name, email, section, currentPassword, newPassword } = req.body || {};
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);

  let nextName = user.name;
  if (name !== undefined) {
    if (!name.trim()) return res.status(400).json({ error: 'Name cannot be empty' });
    nextName = name.trim();
  }

  let nextSection = user.section;
  if (section !== undefined) {
    nextSection = section.trim() || null;
  }
  if (user.role === 'student' && !nextSection) {
    return res.status(400).json({ error: 'Section is required' });
  }

  let nextEmail = user.email;
  if (email !== undefined && email.trim().toLowerCase() !== user.email) {
    try {
      await validateEmailOrThrow(email);
    } catch (err) {
      return res.status(err.status || 400).json({ error: err.message });
    }
    const existing = db.prepare('SELECT id FROM users WHERE email = ? AND id != ?')
      .get(email.trim().toLowerCase(), user.id);
    if (existing) return res.status(409).json({ error: 'An account with this email already exists' });
    nextEmail = email.trim().toLowerCase();
  }

  let nextPasswordHash = null;
  if (newPassword) {
    if (!currentPassword || !bcrypt.compareSync(currentPassword, user.password_hash)) {
      return res.status(401).json({ error: 'Current password is incorrect' });
    }
    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters' });
    }
    nextPasswordHash = bcrypt.hashSync(newPassword, 10);
  }

  if (nextPasswordHash) {
    db.prepare('UPDATE users SET name = ?, email = ?, section = ?, password_hash = ? WHERE id = ?')
      .run(nextName, nextEmail, nextSection, nextPasswordHash, user.id);
  } else {
    db.prepare('UPDATE users SET name = ?, email = ?, section = ? WHERE id = ?')
      .run(nextName, nextEmail, nextSection, user.id);
  }

  const updated = db.prepare('SELECT id, name, email, role, is_active, section FROM users WHERE id = ?').get(user.id);
  res.json({ user: updated });
});

module.exports = router;
