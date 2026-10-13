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
  document.getElementById('usersTableBody').innerHTML = users.map(u => {
    const isSelf = u.id === window.currentUser.id;
    return `
    <tr data-row-id="${u.id}">
      <td><input type="text" class="form-control form-control-sm" data-field="name" value="${escapeHtml(u.name)}" disabled></td>
      <td><input type="email" class="form-control form-control-sm" data-field="email" value="${escapeHtml(u.email)}" disabled></td>
      <td><input type="text" class="form-control form-control-sm" data-field="section" value="${u.section ? escapeHtml(u.section) : ''}" placeholder="e.g., BSIT-3A" disabled></td>
      <td>
        <select class="form-select form-select-sm" data-id="${u.id}" data-field="role" disabled>
          <option value="student" ${u.role === 'student' ? 'selected' : ''}>Student</option>
          <option value="admin" ${u.role === 'admin' ? 'selected' : ''}>Admin</option>
          <option value="approver" ${u.role === 'approver' ? 'selected' : ''}>Approver</option>
          <option value="super_admin" ${u.role === 'super_admin' ? 'selected' : ''}>Super Admin</option>
        </select>
      </td>
      <td>
        <div class="form-check form-switch">
          <input class="form-check-input" type="checkbox" data-id="${u.id}" data-field="is_active"
                 ${u.is_active ? 'checked' : ''} disabled>
        </div>
      </td>
      <td>${new Date(u.created_at).toLocaleDateString()}</td>
      <td class="d-flex gap-2">
        <button class="btn btn-sm btn-outline-secondary" data-modify-id="${u.id}" ${isSelf ? 'disabled' : ''}>
          Modify
        </button>
        <button class="btn btn-sm btn-outline-primary" data-save-id="${u.id}" disabled>
          Save
        </button>
        <button class="btn btn-sm btn-outline-danger" data-delete-id="${u.id}" data-email="${escapeHtml(u.email)}" ${isSelf ? 'disabled' : ''}>
          Delete
        </button>
      </td>
    </tr>
  `;
  }).join('');

  document.querySelectorAll('[data-modify-id]').forEach(btn => {
    btn.addEventListener('click', () => {
      const row = btn.closest('tr');
      row.querySelector('[data-field="name"]').disabled = false;
      row.querySelector('[data-field="email"]').disabled = false;
      row.querySelector('[data-field="section"]').disabled = false;
      row.querySelector('[data-field="role"]').disabled = false;
      row.querySelector('[data-field="is_active"]').disabled = false;
      row.querySelector('[data-save-id]').disabled = false;
      btn.disabled = true;
    });
  });

  document.querySelectorAll('[data-save-id]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.saveId;
      const row = btn.closest('tr');
      const name = row.querySelector('[data-field="name"]').value.trim();
      const email = row.querySelector('[data-field="email"]').value.trim();
      const section = row.querySelector('[data-field="section"]').value.trim();
      const role = row.querySelector('[data-field="role"]').value;
      const is_active = row.querySelector('[data-field="is_active"]').checked ? 1 : 0;

      const originalLabel = btn.textContent;
      btn.disabled = true;
      btn.textContent = 'Saving...';
      try {
        await api.patch(`/users/${id}`, { name, email, section, role, is_active });
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

  document.querySelectorAll('[data-delete-id]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.deleteId;
      const email = btn.dataset.email;
      const confirmed = confirm(
        `Permanently delete ${email}?\n\nThis will also delete every ticket they submitted (and its comments/attachments). ` +
        `Tickets they were assigned to or approving will just be unassigned, not deleted. This cannot be undone.`
      );
      if (!confirmed) return;

      btn.disabled = true;
      btn.textContent = 'Deleting...';
      try {
        await api.delete(`/users/${id}`);
        await loadUsers();
      } catch (err) {
        alert(err.message);
        btn.disabled = false;
        btn.textContent = 'Delete';
      }
    });
  });
}
