// Dashboard — stub (API wiring in later slices)
console.log('[dashboard.js] loaded');

document.addEventListener('DOMContentLoaded', () => {
  const startBtn = document.getElementById('start-export-btn');

  startBtn?.addEventListener('click', () => {
    const sftpPath = document.getElementById('sftp-path')?.value.trim();
    if (!sftpPath) {
      document.getElementById('sftp-path')?.focus();
      alert('SFTP Target Path is required before starting an export.');
      return;
    }
    console.log('[dashboard.js] Export triggered — API wiring coming in Slice 6');
    startBtn.disabled = true;
    startBtn.textContent = '⏳ Export running...';
    setTimeout(() => {
      startBtn.disabled = false;
      startBtn.textContent = '▶ Start Export';
    }, 3000);
  });

  // Sign-out
  const signOutLinks = document.querySelectorAll('a[href*="index.html"]');
  signOutLinks.forEach(link => {
    link.addEventListener('click', async (e) => {
      e.preventDefault();
      try { await fetch('/api/auth/logout', { method: 'POST' }); } catch {}
      window.location.href = '/index.html';
    });
  });
});
