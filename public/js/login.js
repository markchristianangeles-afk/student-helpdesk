document.addEventListener('DOMContentLoaded', async () => {
  Theme.bindToggleButton(document.getElementById('themeToggleBtn'));

  try {
    await api.get('/auth/me');
    window.location.href = '/dashboard.html';
    return;
  } catch (e) { /* not logged in, stay on this page */ }

  const form = document.getElementById('loginForm');
  const alertBox = document.getElementById('alertBox');
  const resendBox = document.getElementById('resendBox');

  const verified = new URLSearchParams(window.location.search).get('verified');
  if (verified === 'success') {
    alertBox.classList.remove('d-none', 'alert-danger');
    alertBox.classList.add('alert-success');
    alertBox.textContent = 'Email verified! You can now log in.';
  } else if (verified === 'invalid') {
    alertBox.classList.remove('d-none', 'alert-success');
    alertBox.classList.add('alert-danger');
    alertBox.textContent = 'That verification link is invalid or has expired. Please request a new one below.';
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    alertBox.classList.add('d-none');
    resendBox.classList.add('d-none');
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    try {
      await api.post('/auth/login', { email, password });
      window.location.href = '/dashboard.html';
    } catch (err) {
      alertBox.classList.remove('alert-success');
      alertBox.classList.add('alert-danger');
      alertBox.textContent = err.message;
      alertBox.classList.remove('d-none');
      if (err.code === 'EMAIL_NOT_VERIFIED') {
        resendBox.classList.remove('d-none');
        resendBox.dataset.email = email;
      }
    }
  });

  resendBox.querySelector('button').addEventListener('click', async () => {
    const email = resendBox.dataset.email;
    const btn = resendBox.querySelector('button');
    btn.disabled = true;
    btn.textContent = 'Sending...';
    try {
      const result = await api.post('/auth/resend-verification', { email });
      resendBox.querySelector('span').textContent = result.message;
    } catch (err) {
      resendBox.querySelector('span').textContent = err.message;
    } finally {
      btn.disabled = false;
      btn.textContent = 'Resend verification email';
    }
  });
});
