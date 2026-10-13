const { pool } = require('../db');

async function logActivity(actor, action, entityType, entityId, message) {
  await pool.query(
    `INSERT INTO activity_log (actor_id, actor_name, actor_role, action, entity_type, entity_id, message, created_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
    [actor.id, actor.name, actor.role, action, entityType, entityId || null, message, new Date().toISOString()]
  );
}

module.exports = logActivity;
