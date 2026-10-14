const asyncRouter = require('../utils/asyncRouter');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { pool } = require('../db');
const { requireAuth, JWT_SECRET } = require('../middleware/auth');
const { validateEmailOrThrow } = require('../utils/validateEmail');
const { trimmedString } = require('../utils/input');
const { normalizeUsername, usernameError } = require('../utils/username');
const logActivity = require('../utils/logActivity');
const { sendVerificationEmail, sendPasswordResetEmail } = require('../utils/mailer');

const router = asyncRouter();

const VERIFICATION_TOKEN_TTL_MS = 24 * 60 * 60 * 1000;
const RESET_TOKEN_TTL_MS = 60 * 60 * 1000;

function appUrl() {
  return process.env.APP_URL || 'http://localhost:3000';
}

async function issueAndSendVerification(user) {
  const token = crypto.randomBytes(32).toString('hex');
  const expires = new Date(Date.now() + VERIFICATION_TOKEN_TTL_MS).toISOString();
  await pool.query('UPDATE users SET verification_token = $1, verification_token_expires = $2 WHERE id = $3',
    [token, expires, user.id]);
  const verifyUrl = `${appUrl()}/api/auth/verify-email?token=${token}`;
  // Deliberately not awaited: delivery takes as long as the SMTP server does,
  // and a slow or unreachable one would otherwise hang the caller's response.
  // The mailer handles its own failures and logs the link either way.
  sendVerificationEmail(user.email, user.name, verifyUrl);
}

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
  const body = req.body || {};
  const name = trimmedString(body.name);
  const email = trimmedString(body.email);
  const section = trimmedString(body.section);
  const username = normalizeUsername(body.username);
  const password = typeof body.password === 'string' ? body.password : null;

  if (!name || !email || !password || !section || !username) {
    return res.status(400).json({ error: 'Name, username, email, password and section are required' });
  }
  const badUsername = usernameError(username);
  if (badUsername) return res.status(400).json({ error: badUsername });
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    await validateEmailOrThrow(email);
  } catch (err) {
    return res.status(err.status || 400).json({ error: err.message });
  }

  const takenUsername = await pool.query('SELECT id FROM users WHERE LOWER(username) = $1', [username]);
  if (takenUsername.rows.length > 0) return res.status(409).json({ error: 'That username is already taken' });

  const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email.toLowerCase()]);
  if (existing.rows.length > 0) return res.status(409).json({ error: 'An account with this email already exists' });

  const hash = bcrypt.hashSync(password, 10);
  const inserted = await pool.query(
    `INSERT INTO users (name, email, username, password_hash, role, is_active, section, email_verified, created_at)
     VALUES ($1, $2, $3, $4, 'student', 1, $5, 0, $6)
     RETURNING id, name, email, username, role, section`,
    [name, email.toLowerCase(), username, hash, section, new Date().toISOString()]
  );

  const user = inserted.rows[0];
  await issueAndSendVerification(user);

  res.status(201).json({
    message: 'Account created. You can now sign in with your username.',
  });
});

router.post('/login', async (req, res) => {
  const username = normalizeUsername((req.body || {}).username);
  const password = typeof (req.body || {}).password === 'string' ? req.body.password : null;
  if (!username || !password) return res.status(400).json({ error: 'Username and password are required' });

  const { rows } = await pool.query('SELECT * FROM users WHERE LOWER(username) = $1', [username]);
  const user = rows[0];
  if (!user || !bcrypt.compareSync(password, user.password_hash)) {
    return res.status(401).json({ error: 'Invalid username or password' });
  }
  if (!user.is_active) return res.status(403).json({ error: 'This account has been deactivated' });

  // email_verified is still recorded, but deliberately does not gate sign-in:
  // the login identity is the username, and blocking on an email that may
  // never arrive would leave accounts permanently unusable.

  issueToken(res, user);
  res.json({ user: { id: user.id, name: user.name, email: user.email, username: user.username, role: user.role, section: user.section } });
});

router.get('/verify-email', async (req, res) => {
  const { token } = req.query;
  if (!token) return res.redirect('/index.html?verified=invalid');

  const { rows } = await pool.query(
    'SELECT * FROM users WHERE verification_token = $1',
    [token]
  );
  const user = rows[0];

  if (!user || !user.verification_token_expires || new Date(user.verification_token_expires) < new Date()) {
    return res.redirect('/index.html?verified=invalid');
  }

  await pool.query(
    'UPDATE users SET email_verified = 1, verification_token = NULL, verification_token_expires = NULL WHERE id = $1',
    [user.id]
  );

  res.redirect('/index.html?verified=success');
});

router.post('/resend-verification', async (req, res) => {
  const email = trimmedString((req.body || {}).email);
  if (!email) return res.status(400).json({ error: 'Email is required' });

  const { rows } = await pool.query('SELECT * FROM users WHERE email = $1', [email.toLowerCase()]);
  const user = rows[0];

  if (user && !user.email_verified) {
    await issueAndSendVerification(user);
  }

  res.json({ message: 'If that email has a pending verification, a new link has been sent.' });
});

router.post('/forgot-password', async (req, res) => {
  const email = trimmedString((req.body || {}).email);
  if (!email) return res.status(400).json({ error: 'Email is required' });

  const { rows } = await pool.query('SELECT * FROM users WHERE email = $1', [email.toLowerCase()]);
  const user = rows[0];

  if (user && user.is_active) {
    const token = crypto.randomBytes(32).toString('hex');
    const expires = new Date(Date.now() + RESET_TOKEN_TTL_MS).toISOString();
    await pool.query('UPDATE users SET reset_token = $1, reset_token_expires = $2 WHERE id = $3',
      [token, expires, user.id]);
    const resetUrl = `${appUrl()}/reset-password.html?token=${token}`;
    sendPasswordResetEmail(user.email, user.name, resetUrl);
  }

  res.json({ message: 'If that email has an account, a password reset link has been sent.' });
});

router.post('/reset-password', async (req, res) => {
  const body = req.body || {};
  const token = trimmedString(body.token);
  const password = typeof body.password === 'string' ? body.password : null;
  if (!token || !password) return res.status(400).json({ error: 'Token and new password are required' });
  if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters' });

  const { rows } = await pool.query('SELECT * FROM users WHERE reset_token = $1', [token]);
  const user = rows[0];

  if (!user || !user.reset_token_expires || new Date(user.reset_token_expires) < new Date()) {
    return res.status(400).json({ error: 'This reset link is invalid or has expired. Please request a new one.' });
  }
  if (!user.is_active) return res.status(403).json({ error: 'This account has been deactivated' });

  const hash = bcrypt.hashSync(password, 10);
  // Following a link sent to the address proves the mailbox is theirs, so this
  // doubles as verification - otherwise an unverified user who resets is still
  // locked out at login with no way forward.
  await pool.query(
    `UPDATE users SET password_hash = $1, reset_token = NULL, reset_token_expires = NULL,
     email_verified = 1, verification_token = NULL, verification_token_expires = NULL WHERE id = $2`,
    [hash, user.id]);

  res.json({ message: 'Password reset. You can now log in with your new password.' });
});

router.post('/logout', (req, res) => {
  res.clearCookie('token');
  res.json({ ok: true });
});

router.get('/me', requireAuth, (req, res) => {
  res.json({ user: req.user });
});

router.patch('/me', requireAuth, async (req, res) => {
  const body = req.body || {};
  const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [req.user.id]);
  const user = rows[0];

  let nextName = user.name;
  if (body.name !== undefined) {
    const name = trimmedString(body.name);
    if (!name) return res.status(400).json({ error: 'Name cannot be empty' });
    nextName = name;
  }

  let nextSection = user.section;
  if (body.section !== undefined) {
    nextSection = trimmedString(body.section) || null;
  }
  if (user.role === 'student' && !nextSection) {
    return res.status(400).json({ error: 'Section is required' });
  }

  let nextEmail = user.email;
  let emailChanged = false;
  if (body.email !== undefined) {
    const email = trimmedString(body.email);
    if (!email) return res.status(400).json({ error: 'Email cannot be empty' });
    if (email.toLowerCase() !== user.email) {
      try {
        await validateEmailOrThrow(email);
      } catch (err) {
        return res.status(err.status || 400).json({ error: err.message });
      }
      const existing = await pool.query('SELECT id FROM users WHERE email = $1 AND id != $2', [email.toLowerCase(), user.id]);
      if (existing.rows.length > 0) return res.status(409).json({ error: 'An account with this email already exists' });
      nextEmail = email.toLowerCase();
      emailChanged = true;
    }
  }

  let nextPasswordHash = null;
  if (body.newPassword) {
    const newPassword = typeof body.newPassword === 'string' ? body.newPassword : null;
    const currentPassword = typeof body.currentPassword === 'string' ? body.currentPassword : null;
    if (!newPassword) return res.status(400).json({ error: 'New password must be text' });
    if (!currentPassword || !bcrypt.compareSync(currentPassword, user.password_hash)) {
      return res.status(401).json({ error: 'Current password is incorrect' });
    }
    if (newPassword.length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters' });
    }
    nextPasswordHash = bcrypt.hashSync(newPassword, 10);
  }

  if (nextPasswordHash) {
    await pool.query('UPDATE users SET name = $1, email = $2, section = $3, password_hash = $4 WHERE id = $5',
      [nextName, nextEmail, nextSection, nextPasswordHash, user.id]);
  } else {
    await pool.query('UPDATE users SET name = $1, email = $2, section = $3 WHERE id = $4',
      [nextName, nextEmail, nextSection, user.id]);
  }

  // A new address is unproven, so it has to be verified again before it can be
  // used to log in - otherwise changing email sidesteps verification entirely.
  if (emailChanged) {
    await pool.query('UPDATE users SET email_verified = 0 WHERE id = $1', [user.id]);
    await issueAndSendVerification({ id: user.id, email: nextEmail, name: nextName });
  }

  const changes = [];
  if (nextName !== user.name) changes.push('name');
  if (nextEmail !== user.email) changes.push('email');
  if (nextSection !== user.section) changes.push('section');
  if (nextPasswordHash) changes.push('password');
  if (changes.length > 0) {
    await logActivity(req.user, 'update', 'user', user.id, `Updated their own profile (${changes.join(', ')})`);
  }

  const updated = await pool.query('SELECT id, name, email, username, role, is_active, section FROM users WHERE id = $1', [user.id]);
  res.json({ user: updated.rows[0] });
});

module.exports = router;
