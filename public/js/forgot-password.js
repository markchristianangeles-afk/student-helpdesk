document.addEventListener('DOMContentLoaded', () => {
  Theme.bindToggleButton(document.getElementById('themeToggleBtn'));

  const form = document.getElementById('forgotForm');
  const alertBox = document.getElementById('alertBox');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email').value.trim();
    const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
      const result = await api.post('/auth/forgot-password', { email });
      form.classList.add('d-none');
      alertBox.classList.remove('d-none', 'alert-danger');
      alertBox.classList.add('alert-success');
      alertBox.textContent = result.message;
    } catch (err) {
      alertBox.classList.remove('alert-success');
      alertBox.classList.add('alert-danger');
      alertBox.textContent = err.message;
      alertBox.classList.remove('d-none');
    } finally {
      btn.disabled = false;
    }
  });
});
