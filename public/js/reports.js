document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage({ requiredRoles: ['admin', 'super_admin'] });
  if (!user) return;

  const isDark = Theme.get() === 'dark';
  Chart.defaults.color = isDark ? '#ced4da' : '#495057';
  Chart.defaults.borderColor = isDark ? '#495057' : '#dee2e6';

  const summary = await api.get('/reports/summary');

  document.getElementById('statTotal').textContent = summary.total;
  document.getElementById('statUnassigned').textContent = summary.unassigned;
  document.getElementById('statAvgResolution').textContent = summary.avgResolutionHours.toFixed(1) + ' hrs';

  new Chart(document.getElementById('statusChart'), {
    type: 'doughnut',
    data: {
      labels: summary.byStatus.map(s => s.status.replace('_', ' ')),
      datasets: [{ data: summary.byStatus.map(s => s.c), backgroundColor: ['#4f46e5', '#f59e0b', '#16a34a', '#6b7280'] }],
    },
  });

  new Chart(document.getElementById('priorityChart'), {
    type: 'bar',
    data: {
      labels: summary.byPriority.map(s => s.priority),
      datasets: [{ label: 'Tickets', data: summary.byPriority.map(s => s.c), backgroundColor: '#4f46e5' }],
    },
    options: { plugins: { legend: { display: false } } },
  });

  new Chart(document.getElementById('categoryChart'), {
    type: 'bar',
    data: {
      labels: summary.byCategory.map(s => s.name),
      datasets: [{ label: 'Tickets', data: summary.byCategory.map(s => s.c), backgroundColor: '#7c3aed' }],
    },
    options: { indexAxis: 'y', plugins: { legend: { display: false } } },
  });

  new Chart(document.getElementById('trendChart'), {
    type: 'line',
    data: {
      labels: summary.trend.map(t => t.day),
      datasets: [{ label: 'New tickets', data: summary.trend.map(t => t.c), borderColor: '#4f46e5', tension: 0.3 }],
    },
  });

  document.getElementById('adminWorkload').innerHTML = summary.byAdmin.length === 0
    ? '<p class="text-muted">No tickets assigned yet.</p>'
    : `<ul class="list-group">${summary.byAdmin.map(a => `<li class="list-group-item d-flex justify-content-between"><span>${escapeHtml(a.name)}</span><span class="badge bg-primary rounded-pill">${a.c}</span></li>`).join('')}</ul>`;
});
