const Helpdesk = {
  user: null,

  async initPage(opts = {}) {
    const { requiredRoles } = opts;
    try {
      const { user } = await api.get('/auth/me');
      this.user = user;
    } catch (err) {
      window.location.href = '/index.html';
      return null;
    }

    if (requiredRoles && !requiredRoles.includes(this.user.role)) {
      window.location.href = '/dashboard.html';
      return null;
    }

    this.renderNav();
    this.loadNotifications();
    setInterval(() => this.loadNotifications(), 30000);
    return this.user;
  },

  roleLabel(role) {
    return { super_admin: 'Super Admin', admin: 'Admin', approver: 'Approver', student: 'Student' }[role] || role;
  },

  renderNav() {
    const el = document.getElementById('app-navbar');
    if (!el || !this.user) return;
    const role = this.user.role;

    const links = [
      { href: '/dashboard.html', label: 'Dashboard', roles: ['student', 'admin', 'approver', 'super_admin'] },
      { href: '/tickets.html', label: 'Tickets', roles: ['student', 'admin', 'approver', 'super_admin'] },
      { href: '/reports.html', label: 'Reports', roles: ['admin', 'super_admin'] },
      { href: '/users.html', label: 'Users', roles: ['super_admin'] },
      { href: '/categories.html', label: 'Categories', roles: ['super_admin'] },
      { href: '/activity.html', label: 'Activity Log', roles: ['admin', 'super_admin'] },
    ].filter(l => l.roles.includes(role));

    const current = window.location.pathname;
    const isSuperAdmin = role === 'super_admin';
    document.body.classList.toggle('has-sidebar', isSuperAdmin);

    const notifBell = `
      <div class="dropdown">
        <button class="btn btn-sm btn-outline-light position-relative" id="bellToggle" data-bs-toggle="dropdown" type="button">
          <i class="bi bi-bell"></i>
          <span id="notifBadge" class="badge bg-danger rounded-pill position-absolute top-0 start-100 translate-middle d-none">0</span>
        </button>
        <div class="dropdown-menu dropdown-menu-end p-2" style="width:300px; max-height:350px; overflow:auto;" id="notifList">
          <div class="text-muted small p-2">No notifications</div>
        </div>
      </div>
    `;

    const navHtml = isSuperAdmin ? `
      <button class="sidebar-mobile-toggle d-lg-none" id="sidebarToggleBtn" type="button" aria-label="Open menu">
        <i class="bi bi-list"></i> <span class="ms-2 fw-semibold">Student Helpdesk</span>
      </button>
      <div class="sidebar-backdrop d-lg-none" id="sidebarBackdrop"></div>
      <div class="app-sidebar" id="appSidebar">
        <div class="sidebar-brand">Student Helpdesk</div>
        <nav class="sidebar-nav flex-grow-1">
          ${links.map(l => `<a href="${l.href}" class="sidebar-link ${current.endsWith(l.href) ? 'active' : ''}">${l.label}</a>`).join('')}
        </nav>
        <div class="sidebar-footer">
          <div class="d-flex align-items-center justify-content-between mb-2">
            <button class="btn btn-sm btn-outline-light" id="themeToggleBtn" title="Toggle light/dark theme"></button>
            ${notifBell}
          </div>
          <div class="small text-light mb-2" id="navbarUserLabel">${escapeHtml(this.user.name)} <span class="badge bg-light text-dark">${this.roleLabel(role)}</span></div>
          <button class="btn btn-sm btn-outline-light w-100 mb-2" id="profileBtn" data-bs-toggle="modal" data-bs-target="#profileModal">Edit Profile</button>
          <button class="btn btn-sm btn-outline-light w-100" id="logoutBtn">Logout</button>
        </div>
      </div>
    ` : `
      <nav class="navbar navbar-expand-lg navbar-dark app-navbar">
        <div class="container">
          <a class="navbar-brand" href="/dashboard.html">Student Helpdesk</a>
          <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navLinks">
            <span class="navbar-toggler-icon"></span>
          </button>
          <div class="collapse navbar-collapse" id="navLinks">
            <ul class="navbar-nav me-auto">
              ${links.map(l => `<li class="nav-item"><a class="nav-link ${current.endsWith(l.href) ? 'active' : ''}" href="${l.href}">${l.label}</a></li>`).join('')}
            </ul>
            <ul class="navbar-nav align-items-lg-center">
              <li class="nav-item me-2">
                <button class="btn btn-outline-light btn-sm" id="themeToggleBtn" title="Toggle light/dark theme"></button>
              </li>
              <li class="nav-item dropdown me-2">
                ${notifBell}
              </li>
              <li class="nav-item">
                <span class="navbar-text text-light me-3" id="navbarUserLabel">${escapeHtml(this.user.name)} <span class="badge bg-secondary">${this.roleLabel(role)}</span></span>
              </li>
              <li class="nav-item me-2">
                <button class="btn btn-outline-light btn-sm" id="profileBtn" data-bs-toggle="modal" data-bs-target="#profileModal">Edit Profile</button>
              </li>
              <li class="nav-item">
                <button class="btn btn-outline-light btn-sm" id="logoutBtn">Logout</button>
              </li>
            </ul>
          </div>
        </div>
      </nav>
    `;

    el.innerHTML = navHtml + `
      <div class="modal fade" id="profileModal" tabindex="-1">
        <div class="modal-dialog">
          <div class="modal-content">
            <form id="profileForm">
              <div class="modal-header">
                <h5 class="modal-title">Edit Profile</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
              </div>
              <div class="modal-body">
                <div id="profileError" class="alert alert-danger d-none"></div>
                <div class="mb-3">
                  <label class="form-label">Name</label>
                  <input type="text" id="profileName" class="form-control" required>
                </div>
                <div class="mb-3 ${role === 'student' ? '' : 'd-none'}" id="profileSectionGroup">
                  <label class="form-label">Section</label>
                  <input type="text" id="profileSection" class="form-control" placeholder="e.g., BSIT-3A">
                </div>
                <div class="mb-3">
                  <label class="form-label">Email</label>
                  <input type="email" id="profileEmail" class="form-control" required>
                </div>
                <hr>
                <p class="text-muted small mb-2">Leave the password fields blank to keep your current password.</p>
                <div class="mb-3">
                  <label class="form-label">Current Password</label>
                  <input type="password" id="profileCurrentPassword" class="form-control" autocomplete="current-password">
                </div>
                <div class="mb-3">
                  <label class="form-label">New Password</label>
                  <input type="password" id="profileNewPassword" class="form-control" minlength="6" autocomplete="new-password">
                </div>
                <div class="mb-3">
                  <label class="form-label">Confirm New Password</label>
                  <input type="password" id="profileConfirmPassword" class="form-control" minlength="6" autocomplete="new-password">
                </div>
              </div>
              <div class="modal-footer">
                <button type="submit" class="btn btn-primary">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    `;

    document.getElementById('logoutBtn').addEventListener('click', async () => {
      await api.post('/auth/logout');
      window.location.href = '/index.html';
    });

    if (isSuperAdmin) {
      const sidebar = document.getElementById('appSidebar');
      const backdrop = document.getElementById('sidebarBackdrop');
      const toggleBtn = document.getElementById('sidebarToggleBtn');
      const closeSidebar = () => {
        sidebar.classList.remove('show');
        backdrop.classList.remove('show');
      };
      toggleBtn.addEventListener('click', () => {
        sidebar.classList.toggle('show');
        backdrop.classList.toggle('show');
      });
      backdrop.addEventListener('click', closeSidebar);
    }

    Theme.bindToggleButton(document.getElementById('themeToggleBtn'));
    this.bindProfileModal();
  },

  bindProfileModal() {
    const modalEl = document.getElementById('profileModal');

    modalEl.addEventListener('show.bs.modal', () => {
      document.getElementById('profileName').value = this.user.name;
      document.getElementById('profileSection').value = this.user.section || '';
      document.getElementById('profileEmail').value = this.user.email;
      document.getElementById('profileCurrentPassword').value = '';
      document.getElementById('profileNewPassword').value = '';
      document.getElementById('profileConfirmPassword').value = '';
      document.getElementById('profileError').classList.add('d-none');
    });

    document.getElementById('profileForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const errBox = document.getElementById('profileError');
      errBox.classList.add('d-none');

      const name = document.getElementById('profileName').value.trim();
      const section = document.getElementById('profileSection').value.trim();
      const email = document.getElementById('profileEmail').value.trim();
      const currentPassword = document.getElementById('profileCurrentPassword').value;
      const newPassword = document.getElementById('profileNewPassword').value;
      const confirmPassword = document.getElementById('profileConfirmPassword').value;

      if (newPassword && newPassword !== confirmPassword) {
        errBox.textContent = 'New passwords do not match';
        errBox.classList.remove('d-none');
        return;
      }

      const payload = { name, email, section };
      if (newPassword) {
        payload.currentPassword = currentPassword;
        payload.newPassword = newPassword;
      }

      try {
        const { user } = await api.patch('/auth/me', payload);
        this.user = user;
        document.getElementById('navbarUserLabel').innerHTML =
          `${escapeHtml(user.name)} <span class="badge bg-secondary">${this.roleLabel(user.role)}</span>`;
        bootstrap.Modal.getInstance(modalEl).hide();
      } catch (err) {
        errBox.textContent = err.message;
        errBox.classList.remove('d-none');
      }
    });
  },

  async loadNotifications() {
    try {
      const { notifications } = await api.get('/notifications');
      const unread = notifications.filter(n => !n.is_read).length;
      const badge = document.getElementById('notifBadge');
      if (badge) {
        if (unread > 0) { badge.textContent = unread; badge.classList.remove('d-none'); }
        else badge.classList.add('d-none');
      }
      const list = document.getElementById('notifList');
      if (list) {
        if (notifications.length === 0) {
          list.innerHTML = '<div class="text-muted small p-2">No notifications</div>';
        } else {
          list.innerHTML = notifications.slice(0, 10).map(n => `
            <a href="${n.ticket_id ? '/ticket.html?id=' + n.ticket_id : '#'}"
               class="dropdown-item small py-2 ${n.is_read ? '' : 'fw-bold bg-light'}"
               data-id="${n.id}">
              ${escapeHtml(n.message)}
              <div class="text-muted" style="font-size:0.75rem;">${new Date(n.created_at).toLocaleString()}</div>
            </a>
          `).join('');
          list.querySelectorAll('[data-id]').forEach(a => {
            a.addEventListener('click', () => api.patch(`/notifications/${a.dataset.id}/read`));
          });
        }
      }
    } catch (err) { /* ignore transient failures */ }
  },
};
