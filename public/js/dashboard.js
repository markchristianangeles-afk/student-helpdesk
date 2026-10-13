document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage();
  if (!user) return;

  document.getElementById('welcomeMsg').textContent = `Welcome back, ${user.name}.`;

  const statsRow = document.getElementById('statsRow');
  const quickActions = document.getElementById('quickActions');
  const recentList = document.getElementById('recentList');

  if (user.role === 'student') {
    quickActions.innerHTML = `<a href="/tickets.html" class="btn btn-primary">View My Tickets</a>`;
    const { tickets } = await api.get('/tickets');
    const counts = { open: 0, in_progress: 0, resolved: 0, closed: 0 };
    tickets.forEach(t => { counts[t.status] = (counts[t.status] || 0) + 1; });
    statsRow.innerHTML = renderStatCards([
      ['Open', counts.open, 'primary'],
      ['In Progress', counts.in_progress, 'warning'],
      ['Resolved', counts.resolved, 'success'],
      ['Closed', counts.closed, 'secondary'],
    ]);
    recentList.innerHTML = renderTicketTable(tickets.slice(0, 5));
  } else if (user.role === 'approver') {
    quickActions.innerHTML = `<a href="/tickets.html" class="btn btn-primary">Review My Approvals</a>`;
    const { tickets } = await api.get('/tickets');
    const counts = { pending: 0, approved: 0, rejected: 0 };
    tickets.forEach(t => { if (t.approval_status) counts[t.approval_status] = (counts[t.approval_status] || 0) + 1; });
    statsRow.innerHTML = renderStatCards([
      ['Pending', counts.pending, 'warning'],
      ['Approved', counts.approved, 'success'],
      ['Rejected', counts.rejected, 'danger'],
      ['Total', tickets.length, 'dark'],
    ]);
    recentList.innerHTML = renderTicketTable(tickets.slice(0, 5), { showAssignee: true });
  } else {
    quickActions.innerHTML = `
      <a href="/tickets.html" class="btn btn-primary me-2">Manage Tickets</a>
      <a href="/reports.html" class="btn btn-outline-primary me-2">View Reports</a>
      ${user.role === 'super_admin' ? '<a href="/users.html" class="btn btn-outline-secondary">Manage Users</a>' : ''}
    `;
    const summary = await api.get('/reports/summary');
    const byStatus = Object.fromEntries(summary.byStatus.map(s => [s.status, s.c]));
    statsRow.innerHTML = renderStatCards([
      ['Total Tickets', summary.total, 'dark'],
      ['Unassigned', summary.unassigned, 'danger'],
      ['Open', byStatus.open || 0, 'primary'],
      ['Resolved', byStatus.resolved || 0, 'success'],
    ]);
    const { tickets } = await api.get('/tickets?status=open');
    recentList.innerHTML = renderTicketTable(tickets.slice(0, 5), { showAssignee: true });
  }
});
