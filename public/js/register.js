document.addEventListener('DOMContentLoaded', async () => {
  Theme.bindToggleButton(document.getElementById('themeToggleBtn'));

  try {
    await api.get('/auth/me');
    window.location.href = '/dashboard.html';
    return;
  } catch (e) { /* not logged in, stay on this page */ }

  const form = document.getElementById('registerForm');
  const alertBox = document.getElementById('alertBox');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertBox.classList.add('d-none');
    const name = document.getElementById('name').value.trim();
    const section = document.getElementById('section').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;

    if (password !== confirmPassword) {
      alertBox.textContent = 'Passwords do not match';
      alertBox.classList.remove('d-none');
      return;
    }

    try {
      await api.post('/auth/register', { name, section, email, password });
      window.location.href = '/dashboard.html';
    } catch (err) {
      alertBox.textContent = err.message;
      alertBox.classList.remove('d-none');
    }
  });
});
