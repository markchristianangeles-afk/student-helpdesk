document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage({ requiredRoles: ['super_admin'] });
  if (!user) return;

  await loadCategories();

  document.getElementById('addCategoryForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errBox = document.getElementById('addCategoryError');
    errBox.classList.add('d-none');
    const name = document.getElementById('newCategoryName').value.trim();
    try {
      await api.post('/categories', { name });
      bootstrap.Modal.getInstance(document.getElementById('addCategoryModal')).hide();
      e.target.reset();
      await loadCategories();
    } catch (err) {
      errBox.textContent = err.message;
      errBox.classList.remove('d-none');
    }
  });
});

async function loadCategories() {
  const { categories } = await api.get('/categories');
  document.getElementById('categoriesTableBody').innerHTML = categories.map(c => `
    <tr data-id="${c.id}">
      <td><input type="text" class="form-control form-control-sm category-name-input" value="${escapeHtml(c.name)}"></td>
      <td>${c.ticket_count}</td>
      <td class="d-flex gap-2">
        <button class="btn btn-sm btn-outline-primary save-category-btn">Save</button>
        <button class="btn btn-sm btn-outline-danger delete-category-btn">Delete</button>
      </td>
    </tr>
  `).join('');

  document.querySelectorAll('.save-category-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const row = btn.closest('tr');
      const id = row.dataset.id;
      const name = row.querySelector('.category-name-input').value.trim();
      const originalLabel = btn.textContent;
      btn.disabled = true;
      btn.textContent = 'Saving...';
      try {
        await api.patch(`/categories/${id}`, { name });
        btn.classList.remove('btn-outline-primary');
        btn.classList.add('btn-success');
        btn.textContent = 'Saved';
        setTimeout(() => loadCategories(), 600);
      } catch (err) {
        alert(err.message);
        btn.disabled = false;
        btn.textContent = originalLabel;
      }
    });
  });

  document.querySelectorAll('.delete-category-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const row = btn.closest('tr');
      const id = row.dataset.id;
      const name = row.querySelector('.category-name-input').value;
      const count = Number(row.children[1].textContent);
      const confirmMsg = count > 0
        ? `"${name}" has ${count} ticket(s) using it. Deleting it will mark those tickets as Uncategorized. Continue?`
        : `Delete category "${name}"?`;
      if (!confirm(confirmMsg)) return;
      try {
        await api.delete(`/categories/${id}`);
        await loadCategories();
      } catch (err) {
        alert(err.message);
      }
    });
  });
}
