const db = require('../db');

function notify(userId, message, ticketId = null) {
  db.prepare(`INSERT INTO notifications (user_id, ticket_id, message, is_read, created_at)
              VALUES (?, ?, ?, 0, ?)`)
    .run(userId, ticketId, message, new Date().toISOString());
}

module.exports = notify;
