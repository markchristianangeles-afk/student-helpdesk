document.addEventListener('DOMContentLoaded', async () => {
  const user = await Helpdesk.initPage();
  if (!user) return;
  window.currentUser = user;

  const isStaff = user.role === 'admin' || user.role === 'super_admin';
  document.getElementById('newTicketBtn').classList.toggle('d-none', isStaff);
  document.getElementById('ticketSectionField').value = user.section || 'Not set';

  const { categories } = await api.get('/categories');
  const catSelect = document.getElementById('filterCategory');
  const newTicketCatSelect = document.getElementById('newTicketCategory');
  categories.forEach(c => {
    catSelect.insertAdjacentHTML('beforeend', `<option value="${c.id}">${escapeHtml(c.name)}</option>`);
    newTicketCatSelect.insertAdjacentHTML('beforeend', `<option value="${c.id}">${escapeHtml(c.name)}</option>`);
  });

  const physicsCategory = categories.find(c => c.name === 'Physics Lab Equipment Borrowing');
  const equipmentFields = document.getElementById('equipmentFields');
  const descriptionGroup = document.getElementById('descriptionGroup');
  const descriptionAnchorDefault = document.getElementById('descriptionAnchorDefault');
  const descriptionAnchorEquipment = document.getElementById('descriptionAnchorEquipment');

  const toggleEquipmentFields = () => {
    const isEquipment = physicsCategory && newTicketCatSelect.value === String(physicsCategory.id);
    equipmentFields.classList.toggle('d-none', !isEquipment);
    const anchor = isEquipment ? descriptionAnchorEquipment : descriptionAnchorDefault;
    anchor.insertAdjacentElement('afterend', descriptionGroup);
  };
  newTicketCatSelect.addEventListener('change', toggleEquipmentFields);

  document.getElementById('filterForm').addEventListener('submit', (e) => {
    e.preventDefault();
    loadTickets();
  });
  document.getElementById('clearFiltersBtn').addEventListener('click', () => {
    document.getElementById('filterForm').reset();
    loadTickets();
  });

  document.getElementById('newTicketForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const subject = document.getElementById('newTicketSubject').value.trim();
    const description = document.getElementById('newTicketDescription').value.trim();
    const category_id = newTicketCatSelect.value || null;
    const priority = document.getElementById('newTicketPriority').value;
    const errBox = document.getElementById('newTicketError');
    errBox.classList.add('d-none');

    const payload = { subject, description, category_id, priority };

    const isEquipment = physicsCategory && category_id === String(physicsCategory.id);
    if (isEquipment) {
      const equipment_name = document.getElementById('equipmentName').value.trim();
      const equipment_quantity = document.getElementById('equipmentQuantity').value;
      const borrow_date = document.getElementById('borrowDate').value;
      const return_date = document.getElementById('returnDate').value;

      if (!equipment_name || !borrow_date || !return_date) {
        errBox.textContent = 'Please fill in the equipment name, borrow date, and return date';
        errBox.classList.remove('d-none');
        return;
      }
      if (return_date < borrow_date) {
        errBox.textContent = 'Return date cannot be before the borrow date';
        errBox.classList.remove('d-none');
        return;
      }

      Object.assign(payload, { equipment_name, equipment_quantity, borrow_date, return_date });
    }

    try {
      await api.post('/tickets', payload);
      bootstrap.Modal.getInstance(document.getElementById('newTicketModal')).hide();
      e.target.reset();
      equipmentFields.classList.add('d-none');
      descriptionAnchorDefault.insertAdjacentElement('afterend', descriptionGroup);
      loadTickets();
    } catch (err) {
      errBox.textContent = err.message;
      errBox.classList.remove('d-none');
    }
  });

  loadTickets();
});

async function loadTickets() {
  const params = new URLSearchParams();
  const status = document.getElementById('filterStatus').value;
  const priority = document.getElementById('filterPriority').value;
  const category_id = document.getElementById('filterCategory').value;
  const q = document.getElementById('filterSearch').value.trim();
  if (status) params.set('status', status);
  if (priority) params.set('priority', priority);
  if (category_id) params.set('category_id', category_id);
  if (q) params.set('q', q);

  const { tickets } = await api.get(`/tickets?${params.toString()}`);
  const isStaff = window.currentUser.role === 'admin' || window.currentUser.role === 'super_admin';
  document.getElementById('ticketTableContainer').innerHTML = renderTicketTable(tickets, { showAssignee: isStaff });
}
