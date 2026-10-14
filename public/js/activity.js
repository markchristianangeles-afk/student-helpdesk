document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage({ requiredRoles: ['admin', 'super_admin'] });
  if (!user) return;

  document.getElementById('filterForm').addEventListener('submit', (e) => {
    e.preventDefault();
    loadActivity();
  });
  document.getElementById('clearFiltersBtn').addEventListener('click', () => {
    document.getElementById('filterForm').reset();
    loadActivity();
  });

  loadActivity();
});

const ACTION_BADGE = {
  create: 'success',
  update: 'primary',
  delete: 'danger',
  comment: 'secondary',
};

async function loadActivity() {
  const params = new URLSearchParams();
  const entityType = document.getElementById('filterEntityType').value;
  const action = document.getElementById('filterAction').value;
  const q = document.getElementById('filterSearch').value.trim();
  if (entityType) params.set('entity_type', entityType);
  if (action) params.set('action', action);
  if (q) params.set('q', q);

  const { entries } = await api.get(`/activity?${params.toString()}`);
  document.getElementById('activityTableBody').innerHTML = entries.length === 0
    ? '<tr><td colspan="4" class="text-muted text-center py-4">No activity recorded yet.</td></tr>'
    : entries.map(e => `
        <tr>
          <td data-label="When" class="text-nowrap">${new Date(e.created_at).toLocaleString()}</td>
          <td data-label="Who">${escapeHtml(e.actor_name)} <span class="badge bg-secondary">${Helpdesk.roleLabel(e.actor_role)}</span></td>
          <td data-label="Action"><span class="badge bg-${ACTION_BADGE[e.action] || 'secondary'}">${e.action}</span> <span class="text-muted small">${e.entity_type}</span></td>
          <td data-label="Details">${escapeHtml(e.message)}</td>
        </tr>
      `).join('');
}
