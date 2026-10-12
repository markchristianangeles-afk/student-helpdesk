const express = require('express');
const db = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');
const notify = require('../utils/notify');
const logActivity = require('../utils/logActivity');

const router = express.Router();

const PHYSICS_EQUIPMENT_CATEGORY = 'Physics Lab Equipment Borrowing';

const TICKET_SELECT = `
  SELECT t.*,
         c.name AS category_name,
         creator.name AS creator_name, creator.email AS creator_email, creator.section AS creator_section,
         assignee.name AS assignee_name, assignee.email AS assignee_email,
         approver.name AS approver_name, approver.email AS approver_email,
         er.equipment_name AS equipment_name,
         er.quantity AS equipment_quantity,
         er.borrow_date AS equipment_borrow_date,
         er.return_date AS equipment_return_date
  FROM tickets t
  LEFT JOIN categories c ON c.id = t.category_id
  LEFT JOIN users creator ON creator.id = t.created_by
  LEFT JOIN users assignee ON assignee.id = t.assigned_to
  LEFT JOIN users approver ON approver.id = t.approver_id
  LEFT JOIN equipment_requests er ON er.ticket_id = t.id
`;

function isValidDateString(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value));
}

function loadTicketOr404(req, res) {
  const id = Number(req.params.id);
  const ticket = db.prepare(TICKET_SELECT + ' WHERE t.id = ?').get(id);
  if (!ticket) {
    res.status(404).json({ error: 'Ticket not found' });
    return null;
  }
  return ticket;
}

function canAccessTicket(user, ticket) {
  if (user.role === 'super_admin' || user.role === 'admin') return true;
  return ticket.created_by === user.id;
}

router.post('/', requireAuth, requireRole('student'), (req, res) => {
  const { subject, description, category_id, priority, equipment_name, equipment_quantity, borrow_date, return_date } = req.body || {};
  if (!subject || !subject.trim() || !description || !description.trim()) {
    return res.status(400).json({ error: 'Subject and description are required' });
  }
  const validPriorities = ['low', 'medium', 'high', 'urgent'];
  const finalPriority = validPriorities.includes(priority) ? priority : 'medium';

  const category = category_id ? db.prepare('SELECT * FROM categories WHERE id = ?').get(category_id) : null;
  const isEquipmentRequest = category && category.name === PHYSICS_EQUIPMENT_CATEGORY;

  let quantity = null;
  if (isEquipmentRequest) {
    if (!equipment_name || !equipment_name.trim()) {
      return res.status(400).json({ error: 'Equipment name is required for a Physics Lab Equipment Borrowing request' });
    }
    quantity = Number(equipment_quantity);
    if (!Number.isInteger(quantity) || quantity < 1) {
      return res.status(400).json({ error: 'Quantity must be a whole number of at least 1' });
    }
    if (!isValidDateString(borrow_date) || !isValidDateString(return_date)) {
      return res.status(400).json({ error: 'Borrow date and return date are required' });
    }
    if (new Date(return_date) < new Date(borrow_date)) {
      return res.status(400).json({ error: 'Return date cannot be before the borrow date' });
    }
  }

  const now = new Date().toISOString();

  const info = db.prepare(`INSERT INTO tickets (subject, description, category_id, priority, status, created_by, created_at, updated_at)
                            VALUES (?, ?, ?, ?, 'open', ?, ?, ?)`)
    .run(subject.trim(), description.trim(), category_id || null, finalPriority, req.user.id, now, now);

  if (isEquipmentRequest) {
    db.prepare(`INSERT INTO equipment_requests (ticket_id, equipment_name, quantity, borrow_date, return_date)
                VALUES (?, ?, ?, ?, ?)`)
      .run(info.lastInsertRowid, equipment_name.trim(), quantity, borrow_date, return_date);
  }

  const staff = db.prepare("SELECT id FROM users WHERE role IN ('admin','super_admin') AND is_active = 1").all();
  for (const s of staff) {
    notify(s.id, `New ticket submitted: "${subject.trim()}"`, info.lastInsertRowid);
  }

  logActivity(req.user, 'create', 'ticket', info.lastInsertRowid, `Submitted ticket "${subject.trim()}"`);

  const ticket = db.prepare(TICKET_SELECT + ' WHERE t.id = ?').get(info.lastInsertRowid);
  res.status(201).json({ ticket });
});

router.get('/', requireAuth, (req, res) => {
  const { status, priority, category_id, assigned_to, q } = req.query;
  let sql = TICKET_SELECT + ' WHERE 1=1';
  const params = [];

  if (req.user.role === 'student') {
    sql += ' AND t.created_by = ?';
    params.push(req.user.id);
  }

  if (status) { sql += ' AND t.status = ?'; params.push(status); }
  if (priority) { sql += ' AND t.priority = ?'; params.push(priority); }
  if (category_id) { sql += ' AND t.category_id = ?'; params.push(category_id); }
  if (assigned_to) { sql += ' AND t.assigned_to = ?'; params.push(assigned_to); }
  if (q) { sql += ' AND t.subject LIKE ?'; params.push(`%${q}%`); }

  sql += ' ORDER BY t.created_at DESC';
  const tickets = db.prepare(sql).all(...params);
  res.json({ tickets });
});

router.get('/:id', requireAuth, (req, res) => {
  const ticket = loadTicketOr404(req, res);
  if (!ticket) return;
  if (!canAccessTicket(req.user, ticket)) return res.status(403).json({ error: 'Access denied' });
  res.json({ ticket });
});

router.patch('/:id', requireAuth, requireRole('admin', 'super_admin'), (req, res) => {
  const ticket = loadTicketOr404(req, res);
  if (!ticket) return;

  const validPriorities = ['low', 'medium', 'high', 'urgent'];

  const { status, priority, assigned_to, category_id, approver_id } = req.body || {};
  const validStatuses = ['open', 'in_progress', 'resolved', 'closed'];

  const next = {
    status: status && validStatuses.includes(status) ? status : ticket.status,
    priority: priority && validPriorities.includes(priority) ? priority : ticket.priority,
    assigned_to: assigned_to !== undefined ? (assigned_to || null) : ticket.assigned_to,
    category_id: category_id !== undefined ? (category_id || null) : ticket.category_id,
    approver_id: approver_id !== undefined ? (approver_id || null) : ticket.approver_id,
  };

  const approverChanged = next.approver_id !== ticket.approver_id;
  const nextApprovalStatus = approverChanged
    ? (next.approver_id ? 'pending' : null)
    : ticket.approval_status;
  const nextApprovedAt = approverChanged ? null : ticket.approved_at;

  const now = new Date().toISOString();
  const resolvedAt = (next.status === 'resolved' || next.status === 'closed')
    ? (ticket.resolved_at || now)
    : null;

  db.prepare(`UPDATE tickets SET status = ?, priority = ?, assigned_to = ?, category_id = ?, approver_id = ?, approval_status = ?, approved_at = ?, updated_at = ?, resolved_at = ?
              WHERE id = ?`)
    .run(next.status, next.priority, next.assigned_to, next.category_id, next.approver_id, nextApprovalStatus, nextApprovedAt, now, resolvedAt, ticket.id);

  if (next.status !== ticket.status) {
    notify(ticket.created_by, `Your ticket "${ticket.subject}" is now ${next.status.replace('_', ' ')}`, ticket.id);
  }
  if (next.assigned_to && next.assigned_to !== ticket.assigned_to) {
    notify(next.assigned_to, `You were assigned to ticket "${ticket.subject}"`, ticket.id);
  }
  if (approverChanged && next.approver_id) {
    notify(next.approver_id, `You were designated as approver for ticket "${ticket.subject}"`, ticket.id);
  }

  const changes = [];
  if (next.status !== ticket.status) changes.push(`status ${ticket.status} → ${next.status}`);
  if (next.priority !== ticket.priority) changes.push(`priority ${ticket.priority} → ${next.priority}`);
  if (next.assigned_to !== ticket.assigned_to) {
    const assigneeName = next.assigned_to
      ? db.prepare('SELECT name FROM users WHERE id = ?').get(next.assigned_to)?.name
      : 'Unassigned';
    changes.push(`assigned to ${assigneeName}`);
  }
  if (approverChanged) {
    const approverName = next.approver_id
      ? db.prepare('SELECT name FROM users WHERE id = ?').get(next.approver_id)?.name
      : 'No approver';
    changes.push(`approver set to ${approverName}`);
  }
  if (changes.length > 0) {
    logActivity(req.user, 'update', 'ticket', ticket.id, `Updated ticket "${ticket.subject}" (${changes.join(', ')})`);
  }

  const updated = db.prepare(TICKET_SELECT + ' WHERE t.id = ?').get(ticket.id);
  res.json({ ticket: updated });
});

router.patch('/:id/approval', requireAuth, requireRole('admin', 'super_admin'), (req, res) => {
  const ticket = loadTicketOr404(req, res);
  if (!ticket) return;

  if (!ticket.approver_id) {
    return res.status(400).json({ error: 'No approver has been assigned to this ticket yet' });
  }
  if (req.user.id !== ticket.approver_id && req.user.role !== 'super_admin') {
    return res.status(403).json({ error: 'Only the designated approver can record a decision' });
  }

  const { decision } = req.body || {};
  if (!['approved', 'rejected'].includes(decision)) {
    return res.status(400).json({ error: 'Decision must be either "approved" or "rejected"' });
  }

  const now = new Date().toISOString();
  db.prepare('UPDATE tickets SET approval_status = ?, approved_at = ?, updated_at = ? WHERE id = ?')
    .run(decision, now, now, ticket.id);

  notify(ticket.created_by, `Your ticket "${ticket.subject}" was ${decision} by the approver`, ticket.id);
  logActivity(req.user, 'update', 'ticket', ticket.id, `${decision === 'approved' ? 'Approved' : 'Rejected'} ticket "${ticket.subject}"`);

  const updated = db.prepare(TICKET_SELECT + ' WHERE t.id = ?').get(ticket.id);
  res.json({ ticket: updated });
});

router.get('/:id/comments', requireAuth, (req, res) => {
  const ticket = loadTicketOr404(req, res);
  if (!ticket) return;
  if (!canAccessTicket(req.user, ticket)) return res.status(403).json({ error: 'Access denied' });

  const comments = db.prepare(`
    SELECT cm.*, u.name AS author_name, u.role AS author_role
    FROM comments cm JOIN users u ON u.id = cm.user_id
    WHERE cm.ticket_id = ? ORDER BY cm.created_at ASC
  `).all(ticket.id);
  res.json({ comments });
});

router.post('/:id/comments', requireAuth, (req, res) => {
  const ticket = loadTicketOr404(req, res);
  if (!ticket) return;
  if (!canAccessTicket(req.user, ticket)) return res.status(403).json({ error: 'Access denied' });

  const { message } = req.body || {};
  if (!message || !message.trim()) return res.status(400).json({ error: 'Comment message is required' });

  const now = new Date().toISOString();
  const info = db.prepare(`INSERT INTO comments (ticket_id, user_id, message, created_at) VALUES (?, ?, ?, ?)`)
    .run(ticket.id, req.user.id, message.trim(), now);

  db.prepare('UPDATE tickets SET updated_at = ? WHERE id = ?').run(now, ticket.id);

  const recipients = new Set([ticket.created_by, ticket.assigned_to].filter(Boolean));
  recipients.delete(req.user.id);
  for (const uid of recipients) {
    notify(uid, `New comment on ticket "${ticket.subject}"`, ticket.id);
  }

  logActivity(req.user, 'comment', 'ticket', ticket.id, `Commented on ticket "${ticket.subject}"`);

  const comment = db.prepare(`
    SELECT cm.*, u.name AS author_name, u.role AS author_role
    FROM comments cm JOIN users u ON u.id = cm.user_id WHERE cm.id = ?
  `).get(info.lastInsertRowid);
  res.status(201).json({ comment });
});

module.exports = router;
