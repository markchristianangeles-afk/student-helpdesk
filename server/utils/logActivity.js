const db = require('../db');

function logActivity(actor, action, entityType, entityId, message) {
  db.prepare(`INSERT INTO activity_log (actor_id, actor_name, actor_role, action, entity_type, entity_id, message, created_at)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(actor.id, actor.name, actor.role, action, entityType, entityId || null, message, new Date().toISOString());
}

module.exports = logActivity;
