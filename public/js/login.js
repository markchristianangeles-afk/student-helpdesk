document.addEventListener('DOMContentLoaded', async () => {
  Theme.bindToggleButton(document.getElementById('themeToggleBtn'));

  try {
    await api.get('/auth/me');
    window.location.href = '/dashboard.html';
    return;
  } catch (e) { /* not logged in, stay on this page */ }

  const form = document.getElementById('loginForm');
  const alertBox = document.getElementById('alertBox');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertBox.classList.add('d-none');
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    try {
      await api.post('/auth/login', { email, password });
      window.location.href = '/dashboard.html';
    } catch (err) {
      alertBox.textContent = err.message;
      alertBox.classList.remove('d-none');
    }
  });
});
