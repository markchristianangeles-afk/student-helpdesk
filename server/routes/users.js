const asyncRouter = require('../utils/asyncRouter');
const bcrypt = require('bcryptjs');
const { pool } = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const { validateEmailOrThrow } = require('../utils/validateEmail');
const { trimmedString } = require('../utils/input');
const logActivity = require('../utils/logActivity');

const router = asyncRouter();

router.get('/', requireAuth, requireRole('super_admin'), async (req, res) => {
  const { role, q } = req.query;
  let sql = 'SELECT id, name, email, role, is_active, section, created_at FROM users WHERE 1=1';
  const params = [];
  if (role) { params.push(role); sql += ` AND role = $${params.length}`; }
  if (q) { params.push(`%${q}%`); sql += ` AND (name ILIKE $${params.length} OR email ILIKE $${params.length})`; }
  sql += ' ORDER BY created_at DESC';
  const { rows } = await pool.query(sql, params);
  res.json({ users: rows });
});

router.get('/assignable', requireAuth, requireRole('super_admin', 'admin'), async (req, res) => {
  const { rows } = await pool.query(
    `SELECT id, name, email, role FROM users WHERE role IN ('admin','super_admin') AND is_active = 1 ORDER BY name`
  );
  res.json({ users: rows });
});

router.get('/approvers', requireAuth, requireRole('super_admin', 'admin'), async (req, res) => {
  const { rows } = await pool.query(
    `SELECT id, name, email FROM users WHERE role = 'approver' AND is_active = 1 ORDER BY name`
  );
  res.json({ users: rows });
});

router.post('/', requireAuth, requireRole('super_admin'), async (req, res) => {
  const body = req.body || {};
  const name = trimmedString(body.name);
  const email = trimmedString(body.email);
  const role = trimmedString(body.role);
  const password = typeof body.password === 'string' ? body.password : null;

  if (!name || !email || !password || !role) {
    return res.status(400).json({ error: 'Name, email, password and role are required' });
  }
  if (!['super_admin', 'admin', 'approver', 'student'].includes(role)) {
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

  const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email.toLowerCase()]);
  if (existing.rows.length > 0) return res.status(409).json({ error: 'An account with this email already exists' });

  const hash = bcrypt.hashSync(password, 10);
  const inserted = await pool.query(
    `INSERT INTO users (name, email, password_hash, role, is_active, created_at)
     VALUES ($1, $2, $3, $4, 1, $5)
     RETURNING id, name, email, role, is_active, section, created_at`,
    [name, email.toLowerCase(), hash, role, new Date().toISOString()]
  );

  const user = inserted.rows[0];
  await logActivity(req.user, 'create', 'user', user.id, `Created ${role} account for ${user.email}`);

  res.status(201).json({ user });
});

router.patch('/:id', requireAuth, requireRole('super_admin'), async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) return res.status(400).json({ error: 'Invalid user id' });

  const { rows: targetRows } = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
  const target = targetRows[0];
  if (!target) return res.status(404).json({ error: 'User not found' });

  const body = req.body || {};
  const { is_active } = body;

  let nextRole = target.role;
  if (body.role !== undefined) {
    const role = trimmedString(body.role);
    if (!role || !['super_admin', 'admin', 'approver', 'student'].includes(role)) {
      return res.status(400).json({ error: 'Invalid role' });
    }
    nextRole = role;
  }

  // Checked before anything is written: the update below used to run first, so
  // a rejected request still committed the role and email changes alongside it.
  const passwordProvided = Boolean(body.password);
  if (passwordProvided && (typeof body.password !== 'string' || body.password.length < 6)) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  let nextName = target.name;
  if (body.name !== undefined) {
    const name = trimmedString(body.name);
    if (!name) return res.status(400).json({ error: 'Name cannot be empty' });
    nextName = name;
  }

  let nextEmail = target.email;
  if (body.email !== undefined) {
    const email = trimmedString(body.email);
    if (!email) return res.status(400).json({ error: 'Email cannot be empty' });
    if (email.toLowerCase() !== target.email) {
      try {
        await validateEmailOrThrow(email);
      } catch (err) {
        return res.status(err.status || 400).json({ error: err.message });
      }
      const existing = await pool.query('SELECT id FROM users WHERE email = $1 AND id != $2', [email.toLowerCase(), id]);
      if (existing.rows.length > 0) return res.status(409).json({ error: 'An account with this email already exists' });
      nextEmail = email.toLowerCase();
    }
  }

  const demotingOrDeactivatingSuperAdmin =
    target.role === 'super_admin' &&
    (nextRole !== 'super_admin' || is_active === 0 || is_active === false);

  if (demotingOrDeactivatingSuperAdmin) {
    const { rows } = await pool.query(
      "SELECT COUNT(*) AS c FROM users WHERE role = 'super_admin' AND id != $1", [id]
    );
    if (Number(rows[0].c) === 0) {
      return res.status(400).json({ error: 'Cannot demote or deactivate the last Super Admin' });
    }
  }

  const next = {
    name: nextName,
    email: nextEmail,
    role: nextRole,
    is_active: is_active !== undefined ? (is_active ? 1 : 0) : target.is_active,
    section: body.section !== undefined ? (trimmedString(body.section) || null) : target.section,
  };

  if (next.role === 'student' && !next.section) {
    return res.status(400).json({ error: 'Section is required for students' });
  }

  await pool.query('UPDATE users SET name = $1, email = $2, role = $3, is_active = $4, section = $5 WHERE id = $6',
    [next.name, next.email, next.role, next.is_active, next.section, id]);

  let passwordReset = false;
  if (passwordProvided) {
    const hash = bcrypt.hashSync(body.password, 10);
    await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [hash, id]);
    passwordReset = true;
  }

  const changes = [];
  if (next.name !== target.name) changes.push(`name changed to "${next.name}"`);
  if (next.email !== target.email) changes.push(`email changed to ${next.email}`);
  if (next.role !== target.role) changes.push(`role ${target.role} → ${next.role}`);
  if (Number(next.is_active) !== Number(target.is_active)) changes.push(Number(next.is_active) ? 'activated' : 'deactivated');
  if (next.section !== target.section) changes.push(`section changed to "${next.section || 'none'}"`);
  if (passwordReset) changes.push('password reset');
  if (changes.length > 0) {
    await logActivity(req.user, 'update', 'user', id, `Updated ${target.email} (${changes.join(', ')})`);
  }

  const { rows: userRows } = await pool.query('SELECT id, name, email, role, is_active, section, created_at FROM users WHERE id = $1', [id]);
  res.json({ user: userRows[0] });
});

router.delete('/:id', requireAuth, requireRole('super_admin'), async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) return res.status(400).json({ error: 'Invalid user id' });

  const { rows: targetRows } = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
  const target = targetRows[0];
  if (!target) return res.status(404).json({ error: 'User not found' });

  if (id === req.user.id) {
    return res.status(400).json({ error: 'You cannot delete your own account' });
  }

  if (target.role === 'super_admin') {
    const { rows } = await pool.query(
      "SELECT COUNT(*) AS c FROM users WHERE role = 'super_admin' AND id != $1", [id]
    );
    if (Number(rows[0].c) === 0) {
      return res.status(400).json({ error: 'Cannot delete the last Super Admin' });
    }
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const { rows: ownTickets } = await client.query('SELECT id FROM tickets WHERE created_by = $1', [id]);
    const ownTicketIds = ownTickets.map(t => t.id);

    for (const ticketId of ownTicketIds) {
      await client.query('DELETE FROM equipment_requests WHERE ticket_id = $1', [ticketId]);
      await client.query('DELETE FROM comments WHERE ticket_id = $1', [ticketId]);
      await client.query('DELETE FROM notifications WHERE ticket_id = $1', [ticketId]);
    }
    await client.query('DELETE FROM tickets WHERE created_by = $1', [id]);

    await client.query('UPDATE tickets SET assigned_to = NULL WHERE assigned_to = $1', [id]);
    await client.query('UPDATE tickets SET approver_id = NULL, approval_status = NULL, approved_at = NULL WHERE approver_id = $1', [id]);

    await client.query('DELETE FROM comments WHERE user_id = $1', [id]);
    await client.query('DELETE FROM notifications WHERE user_id = $1', [id]);
    await client.query('UPDATE activity_log SET actor_id = NULL WHERE actor_id = $1', [id]);

    await client.query('DELETE FROM users WHERE id = $1', [id]);

    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }

  await logActivity(req.user, 'delete', 'user', id, `Deleted ${target.role} account ${target.email}`);

  res.json({ ok: true });
});

module.exports = router;
