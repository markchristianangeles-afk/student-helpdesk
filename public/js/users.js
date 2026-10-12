document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage({ requiredRoles: ['super_admin'] });
  if (!user) return;
  window.currentUser = user;

  await loadUsers();

  document.getElementById('addUserForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errBox = document.getElementById('addUserError');
    errBox.classList.add('d-none');
    try {
      await api.post('/users', {
        name: document.getElementById('newUserName').value.trim(),
        email: document.getElementById('newUserEmail').value.trim(),
        password: document.getElementById('newUserPassword').value,
        role: document.getElementById('newUserRole').value,
      });
      bootstrap.Modal.getInstance(document.getElementById('addUserModal')).hide();
      e.target.reset();
      await loadUsers();
    } catch (err) {
      errBox.textContent = err.message;
      errBox.classList.remove('d-none');
    }
  });
});

async function loadUsers() {
  const { users } = await api.get('/users');
  document.getElementById('usersTableBody').innerHTML = users.map(u => `
    <tr data-row-id="${u.id}">
      <td>${escapeHtml(u.name)}</td>
      <td>${escapeHtml(u.email)}</td>
      <td>
        <select class="form-select form-select-sm" data-id="${u.id}" data-field="role" ${u.id === window.currentUser.id ? 'disabled' : ''}>
          <option value="student" ${u.role === 'student' ? 'selected' : ''}>Student</option>
          <option value="admin" ${u.role === 'admin' ? 'selected' : ''}>Admin</option>
          <option value="super_admin" ${u.role === 'super_admin' ? 'selected' : ''}>Super Admin</option>
        </select>
      </td>
      <td>
        <div class="form-check form-switch">
          <input class="form-check-input" type="checkbox" data-id="${u.id}" data-field="is_active"
                 ${u.is_active ? 'checked' : ''} ${u.id === window.currentUser.id ? 'disabled' : ''}>
        </div>
      </td>
      <td>${new Date(u.created_at).toLocaleDateString()}</td>
      <td>
        <button class="btn btn-sm btn-outline-primary" data-save-id="${u.id}" ${u.id === window.currentUser.id ? 'disabled' : ''}>
          Save
        </button>
      </td>
    </tr>
  `).join('');

  document.querySelectorAll('[data-save-id]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.saveId;
      const row = btn.closest('tr');
      const role = row.querySelector('[data-field="role"]').value;
      const is_active = row.querySelector('[data-field="is_active"]').checked ? 1 : 0;

      const originalLabel = btn.textContent;
      btn.disabled = true;
      btn.textContent = 'Saving...';
      try {
        await api.patch(`/users/${id}`, { role, is_active });
        btn.classList.remove('btn-outline-primary', 'btn-danger');
        btn.classList.add('btn-success');
        btn.textContent = 'Saved';
        setTimeout(() => loadUsers(), 600);
      } catch (err) {
        alert(err.message);
        btn.disabled = false;
        btn.textContent = originalLabel;
      }
    });
  });
}
