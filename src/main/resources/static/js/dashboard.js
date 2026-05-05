console.log('[dashboard.js] loaded');

async function loadBos() {
  try {
    const res = await fetch('/api/bos');
    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (!res.ok) return;
    const bos = await res.json();
    const list = document.getElementById('bo-list');
    if (!list || !bos.length) return;
    list.innerHTML = '';
    bos.forEach(bo => {
      const label = document.createElement('label');
      label.className = 'bo-card';
      label.innerHTML = `
        <input type="checkbox" class="bo-checkbox" value="${bo.name}" checked />
        <div class="bo-card-body">
          <span class="bo-name">${bo.name}</span>
          <span class="bo-last-run">${bo.lastRunDate ? 'Last run: ' + new Date(bo.lastRunDate).toLocaleString() : 'Never run'}</span>
        </div>`;
      list.appendChild(label);
    });
  } catch (err) {
    console.error('[dashboard.js] Failed to load BOs:', err);
  }
}

function getSelectedBos() {
  return Array.from(document.querySelectorAll('.bo-checkbox:checked')).map(cb => cb.value);
}

const STEP_IDS = {
  EXPORT_CSV:          'step-csv',
  EXPORT_PDF:          'step-pdf',
  EXPORT_ATTACHMENTS:  'step-attachments',
  PACKAGING:           'step-packaging',
  SFTP_UPLOAD:         'step-sftp',
};

const PILL_LABELS = {
  PENDING:     { text: 'Pending',     cls: 'pending' },
  IN_PROGRESS: { text: 'In Progress', cls: 'in-progress' },
  SUCCESS:     { text: 'Success',     cls: 'success' },
  FAILED:      { text: 'Failed',      cls: 'failed' },
};

function updateStepPill(stepKey, status) {
  const rowId = STEP_IDS[stepKey];
  if (!rowId) return;
  const pill = document.querySelector(`#${rowId} .status-pill`);
  if (!pill) return;
  const def = PILL_LABELS[status] || PILL_LABELS.PENDING;
  pill.textContent = def.text;
  pill.className = `status-pill ${def.cls}`;
}

function resetStatusPanel() {
  Object.keys(STEP_IDS).forEach(key => updateStepPill(key, 'PENDING'));
}

let pollInterval = null;

function stopPolling() {
  if (pollInterval) { clearInterval(pollInterval); pollInterval = null; }
}

async function pollStatus() {
  try {
    const res = await fetch('/api/run/status');
    if (res.status === 401) { window.location.href = '/index.html'; stopPolling(); return; }
    if (!res.ok) return;
    const run = await res.json();
    if (!run) return;

    if (run.steps) {
      Object.entries(run.steps).forEach(([step, status]) => updateStepPill(step, status));
    }

    if (run.completedAt) {
      stopPolling();
      const btn = document.getElementById('start-export-btn');
      if (btn) { btn.disabled = false; btn.textContent = '▶ Start Export'; }
    }
  } catch (err) {
    console.error('[dashboard.js] Poll error:', err);
  }
}

async function startExport() {
  const sftpPath = document.getElementById('sftp-path')?.value.trim();
  if (!sftpPath) {
    document.getElementById('sftp-path')?.focus();
    alert('SFTP Target Path is required before starting an export.');
    return;
  }

  const btn = document.getElementById('start-export-btn');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Running…'; }
  resetStatusPanel();

  try {
    const res = await fetch('/api/run/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ boNames: getSelectedBos(), sftpTargetPath: sftpPath }),
    });

    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (res.status === 409) {
      alert('An export is already running. Please wait for it to complete.');
      if (btn) { btn.disabled = false; btn.textContent = '▶ Start Export'; }
      return;
    }
    if (!res.ok) {
      alert('Failed to start export. Please try again.');
      if (btn) { btn.disabled = false; btn.textContent = '▶ Start Export'; }
      return;
    }

    // Start polling every 2 seconds
    stopPolling();
    pollInterval = setInterval(pollStatus, 2000);
    pollStatus(); // immediate first check

  } catch (err) {
    console.error('[dashboard.js] Start export error:', err);
    if (btn) { btn.disabled = false; btn.textContent = '▶ Start Export'; }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadBos();
  document.getElementById('start-export-btn')?.addEventListener('click', startExport);

  // Restore live poll if a run is already in progress on page load
  fetch('/api/run/status')
    .then(r => r.ok ? r.json() : null)
    .then(run => {
      if (run && !run.completedAt) {
        const btn = document.getElementById('start-export-btn');
        if (btn) { btn.disabled = true; btn.textContent = '⏳ Running…'; }
        if (run.steps) Object.entries(run.steps).forEach(([s, st]) => updateStepPill(s, st));
        pollInterval = setInterval(pollStatus, 2000);
      } else if (run && run.steps) {
        Object.entries(run.steps).forEach(([s, st]) => updateStepPill(s, st));
      }
    })
    .catch(() => {});

  // Sign-out
  document.querySelectorAll('a[href*="index.html"]').forEach(link => {
    link.addEventListener('click', async (e) => {
      e.preventDefault();
      try { await fetch('/api/auth/logout', { method: 'POST' }); } catch {}
      window.location.href = '/index.html';
    });
  });
});
