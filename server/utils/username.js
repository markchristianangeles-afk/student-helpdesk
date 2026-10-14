// Usernames are identifiers rather than display names (users have `name` for
// that), so they are stored lowercased. That keeps "Admin" and "admin" from
// being two different logins.
const USERNAME_RE = /^[a-z0-9._-]{3,30}$/;

function normalizeUsername(value) {
  return typeof value === 'string' ? value.trim().toLowerCase() : null;
}

function usernameError(username) {
  if (!username) return 'Username is required';
  if (!USERNAME_RE.test(username)) {
    return 'Username must be 3-30 characters, using only letters, numbers, dots, underscores or hyphens';
  }
  return null;
}

// Derives a free username from an email address, for backfilling accounts that
// predate this field.
async function deriveUsername(pool, email, excludeId) {
  const base = String(email || '').split('@')[0].toLowerCase().replace(/[^a-z0-9._-]/g, '').slice(0, 26) || 'user';
  let candidate = base.length >= 3 ? base : `${base}user`.slice(0, 30);
  let n = 1;
  for (;;) {
    const { rows } = await pool.query(
      'SELECT 1 FROM users WHERE LOWER(username) = $1 AND id != $2',
      [candidate, excludeId || 0]
    );
    if (rows.length === 0) return candidate;
    n += 1;
    candidate = `${base}${n}`.slice(0, 30);
  }
}

module.exports = { normalizeUsername, usernameError, deriveUsername };
