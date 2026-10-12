const ticketParams = new URLSearchParams(window.location.search);
const ticketId = ticketParams.get('id');

document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage();
  if (!user) return;
  if (!ticketId) { window.location.href = '/tickets.html'; return; }

  await loadTicket(user);
  await loadComments(user);

  document.getElementById('commentForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const input = document.getElementById('commentMessage');
    const msg = input.value.trim();
    if (!msg) return;
    await api.post(`/tickets/${ticketId}/comments`, { message: msg });
    input.value = '';
    await loadComments(user);
  });
});

async function loadTicket(user) {
  let ticket;
  try {
    ({ ticket } = await api.get(`/tickets/${ticketId}`));
  } catch (err) {
    document.getElementById('ticketContent').insertAdjacentHTML(
      'beforeend', `<div class="alert alert-danger">${escapeHtml(err.message)}</div>`
    );
    return;
  }

  document.getElementById('ticketSubject').textContent = ticket.subject;
  document.getElementById('ticketMeta').innerHTML = `
    <span class="badge rounded-pill badge-status-${ticket.status} me-1">${ticket.status.replace('_', ' ')}</span>
    <span class="badge rounded-pill badge-priority-${ticket.priority} me-1">${ticket.priority}</span>
    <span class="text-muted">Category: ${escapeHtml(ticket.category_name || 'Uncategorized')}</span>
  `;
  document.getElementById('ticketDescription').textContent = ticket.description;

  const equipmentDetails = document.getElementById('equipmentDetails');
  if (ticket.equipment_name) {
    equipmentDetails.classList.remove('d-none');
    document.getElementById('equipmentDetailName').textContent = ticket.equipment_name;
    document.getElementById('equipmentDetailQuantity').textContent = ticket.equipment_quantity;
    document.getElementById('equipmentDetailBorrow').textContent = ticket.equipment_borrow_date;
    document.getElementById('equipmentDetailReturn').textContent = ticket.equipment_return_date;
  } else {
    equipmentDetails.classList.add('d-none');
  }

  document.getElementById('ticketSubmitter').textContent = `${ticket.creator_name} (${ticket.creator_email})`;
  document.getElementById('ticketSection').textContent = ticket.creator_section || 'Not set';
  document.getElementById('ticketAssignee').textContent = ticket.assignee_name || 'Unassigned';
  document.getElementById('ticketCreated').textContent = new Date(ticket.created_at).toLocaleString();

  const isStaff = user.role === 'admin' || user.role === 'super_admin';

  const controls = document.getElementById('staffControls');
  if (isStaff) {
    controls.classList.remove('d-none');
    const { users } = await api.get('/users/assignable');
    document.getElementById('statusSelect').value = ticket.status;
    document.getElementById('prioritySelect').value = ticket.priority;
    const assignSelect = document.getElementById('assigneeSelect');
    assignSelect.innerHTML = '<option value="">Unassigned</option>' +
      users.map(u => `<option value="${u.id}" ${u.id === ticket.assigned_to ? 'selected' : ''}>${escapeHtml(u.name)}</option>`).join('');

    document.getElementById('saveTicketBtn').onclick = async () => {
      await api.patch(`/tickets/${ticketId}`, {
        status: document.getElementById('statusSelect').value,
        priority: document.getElementById('prioritySelect').value,
        assigned_to: assignSelect.value || null,
      });
      await loadTicket(user);
    };
  }
}

async function loadComments(user) {
  const { comments } = await api.get(`/tickets/${ticketId}/comments`);
  document.getElementById('commentsList').innerHTML = comments.length === 0
    ? '<p class="text-muted">No comments yet.</p>'
    : comments.map(c => `
        <div class="border rounded p-2 mb-2">
          <div class="d-flex justify-content-between">
            <strong>${escapeHtml(c.author_name)} <span class="badge bg-secondary">${Helpdesk.roleLabel(c.author_role)}</span></strong>
            <small class="text-muted">${new Date(c.created_at).toLocaleString()}</small>
          </div>
          <div>${escapeHtml(c.message)}</div>
        </div>
      `).join('');
}
