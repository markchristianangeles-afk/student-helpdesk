const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const bcrypt = require('bcryptjs');

const dataDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'helpdesk.sqlite'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('super_admin','admin','student')),
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tickets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  subject TEXT NOT NULL,
  description TEXT NOT NULL,
  category_id INTEGER,
  priority TEXT NOT NULL CHECK(priority IN ('low','medium','high','urgent')) DEFAULT 'medium',
  status TEXT NOT NULL CHECK(status IN ('open','in_progress','resolved','closed')) DEFAULT 'open',
  created_by INTEGER NOT NULL,
  assigned_to INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  resolved_at TEXT,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (created_by) REFERENCES users(id),
  FOREIGN KEY (assigned_to) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS equipment_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ticket_id INTEGER NOT NULL UNIQUE,
  equipment_name TEXT NOT NULL,
  quantity INTEGER NOT NULL,
  borrow_date TEXT NOT NULL,
  return_date TEXT NOT NULL,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id)
);

CREATE TABLE IF NOT EXISTS comments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ticket_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  message TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  ticket_id INTEGER,
  message TEXT NOT NULL,
  is_read INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (ticket_id) REFERENCES tickets(id)
);
`);

function ensureColumn(table, column, definition) {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all();
  if (!columns.some(c => c.name === column)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
  }
}

ensureColumn('users', 'section', 'TEXT');

const defaultCategories = ['IT Support', 'Academic', 'Administrative', 'Facilities', 'Physics Lab Equipment Borrowing', 'Other'];
const insertCategory = db.prepare('INSERT OR IGNORE INTO categories (name) VALUES (?)');
for (const name of defaultCategories) insertCategory.run(name);

const superAdminCount = db.prepare("SELECT COUNT(*) AS c FROM users WHERE role = 'super_admin'").get().c;
if (superAdminCount === 0) {
  const name = process.env.SUPER_ADMIN_NAME || 'Super Admin';
  const email = (process.env.SUPER_ADMIN_EMAIL || 'admin@example.com').toLowerCase();
  const password = process.env.SUPER_ADMIN_PASSWORD || 'ChangeMe123!';
  const hash = bcrypt.hashSync(password, 10);
  db.prepare(`INSERT INTO users (name, email, password_hash, role, is_active, created_at)
              VALUES (?, ?, ?, 'super_admin', 1, ?)`)
    .run(name, email, hash, new Date().toISOString());
  console.log(`[seed] Created default Super Admin account: ${email} / ${password} (change this password immediately)`);
}

module.exports = db;
