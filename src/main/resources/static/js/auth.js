// Shared auth utilities and scroll-reveal initialization
console.log('[auth.js] loaded');

export function getRole() {
  return sessionStorage.getItem('role');
}

export async function logout() {
  sessionStorage.removeItem('role');
  window.location.href = '/index.html';
}

export function initReveal() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) entry.target.classList.add('visible');
    });
  }, { threshold: 0.12 });
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
}

document.addEventListener('DOMContentLoaded', () => {
  initReveal();
  const hamburger = document.getElementById('hamburger');
  const sidebar = document.getElementById('sidebar');
  if (hamburger && sidebar) {
    hamburger.addEventListener('click', () => sidebar.classList.toggle('open'));
  }
});
