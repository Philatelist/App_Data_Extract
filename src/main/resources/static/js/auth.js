// Shared auth utilities and scroll-reveal initialization
console.log('[auth.js] loaded');

export function getRole() {
  return sessionStorage.getItem('role');
}

export async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } finally {
    sessionStorage.removeItem('role');
    window.location.href = '/index.html';
  }
}

export function initReveal() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) entry.target.classList.add('visible');
    });
  }, { threshold: 0.12 });
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
}

function wireLoginForm() {
  const form = document.getElementById('login-form');
  if (!form) return;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username')?.value.trim() ?? '';
    const password = document.getElementById('password')?.value ?? '';
    const submitBtn = form.querySelector('button[type=submit]');
    const errorDiv = document.getElementById('login-error');

    if (errorDiv) errorDiv.style.display = 'none';
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'Signing in...'; }

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (res.ok) {
        const data = await res.json();
        window.location.href = data.role === 'ADMIN' ? '/admin.html' : '/dashboard.html';
      } else {
        if (errorDiv) {
          errorDiv.textContent = 'Invalid credentials. Please try again.';
          errorDiv.style.display = 'block';
        }
      }
    } catch (err) {
      if (errorDiv) {
        errorDiv.textContent = 'Connection error. Please try again.';
        errorDiv.style.display = 'block';
      }
    } finally {
      if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'Sign In →'; }
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  initReveal();
  const hamburger = document.getElementById('hamburger');
  const sidebar = document.getElementById('sidebar');
  if (hamburger && sidebar) {
    hamburger.addEventListener('click', () => sidebar.classList.toggle('open'));
  }
  wireLoginForm();
});
