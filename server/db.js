const { Pool } = require('pg');
const bcrypt = require('bcryptjs');

const connectionString = process.env.DATABASE_URL
  || 'postgres://postgres:PostgresLocal123!@localhost:5432/helpdesk';

const pool = new Pool({
  connectionString,
  ssl: process.env.DATABASE_URL && process.env.NODE_ENV === 'production'
    ? { rejectUnauthorized: false }
    : false,
});

async function ensureColumn(table, column, definition) {
  const { rows } = await pool.query(
    `SELECT column_name FROM information_schema.columns WHERE table_name = $1 AND column_name = $2`,
    [table, column]
  );
  if (rows.length === 0) {
    await pool.query(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  }
}

async function initDb() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL CHECK (role IN ('super_admin','admin','approver','student')),
      is_active INTEGER NOT NULL DEFAULT 1,
      email_verified INTEGER NOT NULL DEFAULT 1,
      verification_token TEXT,
      verification_token_expires TEXT,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS categories (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL UNIQUE
    );

    CREATE TABLE IF NOT EXISTS tickets (
      id SERIAL PRIMARY KEY,
      subject TEXT NOT NULL,
      description TEXT NOT NULL,
      category_id INTEGER REFERENCES categories(id),
      priority TEXT NOT NULL CHECK (priority IN ('low','medium','high','urgent')) DEFAULT 'medium',
      status TEXT NOT NULL CHECK (status IN ('open','in_progress','resolved','closed')) DEFAULT 'open',
      created_by INTEGER NOT NULL REFERENCES users(id),
      assigned_to INTEGER REFERENCES users(id),
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      resolved_at TEXT
    );

    CREATE TABLE IF NOT EXISTS equipment_requests (
      id SERIAL PRIMARY KEY,
      ticket_id INTEGER NOT NULL UNIQUE REFERENCES tickets(id),
      equipment_name TEXT NOT NULL,
      quantity INTEGER NOT NULL,
      borrow_date TEXT NOT NULL,
      return_date TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS comments (
      id SERIAL PRIMARY KEY,
      ticket_id INTEGER NOT NULL REFERENCES tickets(id),
      user_id INTEGER NOT NULL REFERENCES users(id),
      message TEXT NOT NULL,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS notifications (
      id SERIAL PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES users(id),
      ticket_id INTEGER REFERENCES tickets(id),
      message TEXT NOT NULL,
      is_read INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS activity_log (
      id SERIAL PRIMARY KEY,
      actor_id INTEGER REFERENCES users(id),
      actor_name TEXT NOT NULL,
      actor_role TEXT NOT NULL,
      action TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      entity_id INTEGER,
      message TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
  `);

  await ensureColumn('users', 'section', 'TEXT');
  await ensureColumn('users', 'email_verified', 'INTEGER NOT NULL DEFAULT 1');
  await ensureColumn('users', 'verification_token', 'TEXT');
  await ensureColumn('users', 'verification_token_expires', 'TEXT');
  await pool.query(`ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check`);
  await pool.query(`ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('super_admin','admin','approver','student'))`);
  await ensureColumn('tickets', 'approver_id', 'INTEGER');
  await ensureColumn('tickets', 'approval_status', 'TEXT');
  await ensureColumn('tickets', 'approved_at', 'TEXT');

  const defaultCategories = ['IT Support', 'Academic', 'Administrative', 'Facilities', 'Physics Lab Equipment Borrowing', 'Other'];
  for (const name of defaultCategories) {
    await pool.query('INSERT INTO categories (name) VALUES ($1) ON CONFLICT (name) DO NOTHING', [name]);
  }

  const { rows: superAdminRows } = await pool.query("SELECT COUNT(*) AS c FROM users WHERE role = 'super_admin'");
  if (Number(superAdminRows[0].c) === 0) {
    const name = process.env.SUPER_ADMIN_NAME || 'Super Admin';
    const email = (process.env.SUPER_ADMIN_EMAIL || 'admin@example.com').toLowerCase();
    const password = process.env.SUPER_ADMIN_PASSWORD || 'ChangeMe123!';
    const hash = bcrypt.hashSync(password, 10);
    await pool.query(
      `INSERT INTO users (name, email, password_hash, role, is_active, created_at)
       VALUES ($1, $2, $3, 'super_admin', 1, $4)`,
      [name, email, hash, new Date().toISOString()]
    );
    console.log(`[seed] Created default Super Admin account: ${email} / ${password} (change this password immediately)`);
  }
}

module.exports = { pool, initDb };
