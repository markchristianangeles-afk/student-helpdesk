(function () {
  let saved = null;
  try { saved = localStorage.getItem('theme'); } catch (e) { /* ignore */ }
  const theme = saved || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.documentElement.setAttribute('data-bs-theme', theme);
})();

const Theme = {
  get() {
    return document.documentElement.getAttribute('data-bs-theme') || 'light';
  },
  set(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    try { localStorage.setItem('theme', theme); } catch (e) { /* ignore */ }
  },
  toggle() {
    const next = this.get() === 'dark' ? 'light' : 'dark';
    this.set(next);
    return next;
  },
  bindToggleButton(btn) {
    if (!btn) return;
    const updateIcon = () => {
      btn.innerHTML = this.get() === 'dark'
        ? '<i class="bi bi-sun"></i>'
        : '<i class="bi bi-moon-stars"></i>';
    };
    updateIcon();
    btn.addEventListener('click', () => { this.toggle(); updateIcon(); });
  },
};
