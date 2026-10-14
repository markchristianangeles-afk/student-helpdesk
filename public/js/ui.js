function renderStatCards(items) {
  return items.map(([label, value, color]) => `
    <div class="col-6 col-md-3 mb-3">
      <div class="card border-${color} h-100">
        <div class="card-body text-center">
          <div class="display-6">${value}</div>
          <div class="text-muted">${label}</div>
        </div>
      </div>
    </div>
  `).join('');
}

function renderTicketTable(tickets, opts = {}) {
  const { showAssignee = false } = opts;
  if (tickets.length === 0) return '<p class="text-muted">No tickets to show.</p>';
  return `
    <div class="card">
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>Subject</th>
              ${showAssignee ? '<th>Section</th>' : ''}
              <th>Priority</th>
              <th>Status</th>
              ${showAssignee ? '<th>Assigned To</th>' : ''}
              ${showAssignee ? '<th>Approver</th>' : ''}
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            ${tickets.map(t => `
              <tr style="cursor:pointer" onclick="window.location.href='/ticket.html?id=${t.id}'">
                <td>${escapeHtml(t.subject)}</td>
                ${showAssignee ? `<td>${t.creator_section ? escapeHtml(t.creator_section) : '<span class="text-muted">—</span>'}</td>` : ''}
                <td><span class="badge rounded-pill badge-priority-${t.priority}">${t.priority}</span></td>
                <td><span class="badge rounded-pill badge-status-${t.status}">${t.status.replace('_', ' ')}</span></td>
                ${showAssignee ? `<td>${t.assignee_name ? escapeHtml(t.assignee_name) : '<span class="text-muted">Unassigned</span>'}</td>` : ''}
                ${showAssignee ? `<td>${renderApproverCell(t)}</td>` : ''}
                <td>${new Date(t.created_at).toLocaleDateString()}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderApproverCell(t) {
  if (!t.approver_name) return '<span class="text-muted">—</span>';
  const badgeColor = { pending: 'warning', approved: 'success', rejected: 'danger' }[t.approval_status] || 'secondary';
  return `${escapeHtml(t.approver_name)} <span class="badge bg-${badgeColor}">${t.approval_status}</span>`;
}
