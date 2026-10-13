const { pool } = require('../db');

async function notify(userId, message, ticketId = null) {
  await pool.query(
    `INSERT INTO notifications (user_id, ticket_id, message, is_read, created_at)
     VALUES ($1, $2, $3, 0, $4)`,
    [userId, ticketId, message, new Date().toISOString()]
  );
}

module.exports = notify;
