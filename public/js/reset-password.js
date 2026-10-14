document.addEventListener('DOMContentLoaded', () => {
  Theme.bindToggleButton(document.getElementById('themeToggleBtn'));

  const token = new URLSearchParams(window.location.search).get('token');
  const form = document.getElementById('resetForm');
  const alertBox = document.getElementById('alertBox');

  if (!token) {
    form.classList.add('d-none');
    alertBox.classList.remove('d-none');
    alertBox.textContent = 'This reset link is missing or invalid. Please request a new one from the "Forgot password?" page.';
    return;
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertBox.classList.add('d-none');
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;

    if (password !== confirmPassword) {
      alertBox.classList.remove('d-none', 'alert-success');
      alertBox.classList.add('alert-danger');
      alertBox.textContent = 'Passwords do not match';
      return;
    }

    const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      const result = await api.post('/auth/reset-password', { token, password });
      form.classList.add('d-none');
      document.getElementById('successUsername').textContent = result.username;
      document.getElementById('successBox').classList.remove('d-none');
    } catch (err) {
      alertBox.classList.remove('alert-success');
      alertBox.classList.add('alert-danger');
      alertBox.textContent = err.message;
      alertBox.classList.remove('d-none');
      btn.disabled = false;
    }
  });
});
