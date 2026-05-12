console.log('[dashboard.js] loaded');

// internal name → display name, populated by loadBos()
const boDisplayNames = {};

// ─── Step pill helpers ───────────────────────────────────────────────────────

const STEP_IDS = {
  EXPORT_CSV:          'step-csv',
  EXPORT_PDF:          'step-pdf',
  EXPORT_ATTACHMENTS:  'step-attachments',
  PACKAGING:           'step-packaging',
  SFTP_UPLOAD:         'step-sftp',
};

const PILL = {
  PENDING:     { text: 'Pending',     cls: 'pending' },
  IN_PROGRESS: { text: 'In Progress', cls: 'in-progress' },
  SUCCESS:     { text: 'Success',     cls: 'success' },
  FAILED:      { text: 'Failed',      cls: 'failed' },
  SKIPPED:     { text: 'Skipped',     cls: 'skipped' },
};

function updateStepPill(stepKey, status) {
  const rowId = STEP_IDS[stepKey];
  if (!rowId) return;
  const pill = document.querySelector(`#${rowId} .status-pill`);
  if (!pill) return;
  const def = PILL[status] || PILL.PENDING;
  pill.textContent = def.text;
  pill.className = `status-pill ${def.cls}`;
}

function resetStatusPanel() {
  Object.keys(STEP_IDS).forEach(key => updateStepPill(key, 'PENDING'));
}

// ─── Disable / enable config section ────────────────────────────────────────

function setConfigEditable(enabled) {
  const section = document.getElementById('export-config');
  if (section) section.style.display = enabled ? '' : 'none';
}

// ─── Polling ─────────────────────────────────────────────────────────────────

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
      document.getElementById('running-badge').style.display = 'none';
      document.getElementById('stop-btn').style.display = 'none';
      setConfigEditable(true);
      const btn = document.getElementById('start-export-btn');
      if (btn) { btn.disabled = false; btn.textContent = isScheduleOn() ? '📅 Schedule' : '▶ Start Export'; }
      loadHistory();
      loadBos();
    }
  } catch (err) {
    console.error('[dashboard.js] Poll error:', err);
  }
}

// ─── BO list ─────────────────────────────────────────────────────────────────

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
      boDisplayNames[bo.name] = bo.localizedName || bo.name;
      const label = document.createElement('label');
      label.className = 'bo-card';
      const display = bo.localizedName || bo.name;
      const lastRunText = bo.lastRunDate
        ? new Date(bo.lastRunDate).toLocaleString()
        : '—';
      label.innerHTML = `
        <input type="checkbox" class="bo-checkbox" value="${bo.name}" />
        <div class="bo-card-body">
          <span class="bo-name">${display}</span>
          <span class="bo-last-run">Last run: ${lastRunText}</span>
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

// ─── Schedule toggle & date filter ──────────────────────────────────────────

function isScheduleOn() {
  return document.getElementById('auto-schedule')?.checked ?? false;
}

function wireScheduleToggle() {
  const toggle       = document.getElementById('auto-schedule');
  const schedSet     = document.getElementById('schedule-settings');
  const statusPanel  = document.getElementById('status-panel');
  const btn          = document.getElementById('start-export-btn');
  const hint         = document.getElementById('cta-hint');
  const modGroup     = document.getElementById('modified-period-group');
  const dateToGroup  = document.getElementById('date-to-group');

  const applyScheduleUI = () => {
    const on = toggle.checked;

    // Schedule settings block
    if (schedSet) schedSet.style.display = on ? '' : 'none';

    // Export Status panel
    if (statusPanel) statusPanel.style.display = on ? 'none' : '';

    // Button label + hint
    if (btn) btn.textContent = on ? '📅 Schedule' : '▶ Start Export';
    if (hint) hint.textContent = on
      ? 'Saves the schedule. Exports will run automatically at the configured time.'
      : 'Exports selected BOs. If none selected, all BOs are exported.';

    // Modified-within-period toggle only when auto-schedule ON
    if (modGroup) modGroup.style.display = on ? '' : 'none';

    // Date To hidden when auto-schedule ON
    if (dateToGroup) dateToGroup.style.display = on ? 'none' : '';

    applyDateFieldVisibility();
    applyDayOfWeekVisibility();
  };

  toggle?.addEventListener('change', applyScheduleUI);
  applyScheduleUI();

  // Modified within period: hides Date Field + Date From when checked
  document.getElementById('modified-period')?.addEventListener('change', applyDateFieldVisibility);

  // Day of week only shown for WEEKLY frequency
  document.getElementById('frequency')?.addEventListener('change', applyDayOfWeekVisibility);
}

function applyDateFieldVisibility() {
  const modChecked  = document.getElementById('modified-period')?.checked ?? false;
  const schedOn     = isScheduleOn();
  const hide        = schedOn && modChecked;
  const fieldGrp    = document.getElementById('date-field-group');
  const fromGrp     = document.getElementById('date-from-group');
  if (fieldGrp) fieldGrp.style.display = hide ? 'none' : '';
  if (fromGrp)  fromGrp.style.display  = hide ? 'none' : '';
}

function applyDayOfWeekVisibility() {
  const freq    = document.getElementById('frequency')?.value;
  const dowGrp  = document.getElementById('day-of-week-group');
  if (dowGrp) dowGrp.style.display = freq === 'WEEKLY' ? '' : 'none';
}

// ─── Schedule save + display ─────────────────────────────────────────────────

function collectSchedulePayload() {
  return {
    enabled: document.getElementById('auto-schedule')?.checked ?? false,
    frequency: document.getElementById('frequency')?.value || 'DAILY',
    dayOfWeek: document.getElementById('day-of-week')?.value || 'MONDAY',
    timeOfDay: document.getElementById('hour-of-day')?.value || '02:00',
    timezone: document.getElementById('timezone')?.value || 'UTC',
    selectedBos: getSelectedBos(),
    dateField: document.getElementById('date-field')?.value || '',
    dateFrom: document.getElementById('date-from')?.value || '',
    modifiedWithinPeriod: document.getElementById('modified-period')?.checked ?? false,
  };
}

async function saveSchedule() {
  const btn = document.getElementById('start-export-btn');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Saving…'; }
  try {
    const res = await fetch('/api/schedule', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(collectSchedulePayload()),
    });
    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (res.ok) loadScheduleInfo();
  } catch (err) {
    console.error('[dashboard.js] Save schedule error:', err);
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = '📅 Schedule'; }
  }
}

const FREQ_LABELS = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly' };
const DOW_LABELS  = { MONDAY:'Monday', TUESDAY:'Tuesday', WEDNESDAY:'Wednesday',
                      THURSDAY:'Thursday', FRIDAY:'Friday', SATURDAY:'Saturday', SUNDAY:'Sunday' };

async function loadScheduleInfo() {
  try {
    const res = await fetch('/api/schedule');
    if (!res.ok) return;
    const s = await res.json();
    const panel = document.getElementById('scheduled-job-panel');
    const detail = document.getElementById('scheduled-job-details');
    if (!panel || !detail) return;

    if (!s || !s.enabled) { panel.style.display = 'none'; return; }
    panel.style.display = '';

    const freqLine = FREQ_LABELS[s.frequency] || s.frequency;
    const dowLine  = s.frequency === 'WEEKLY' && s.dayOfWeek
      ? `, every ${DOW_LABELS[s.dayOfWeek] || s.dayOfWeek}` : '';
    const timeLine = s.timeOfDay ? ` at ${s.timeOfDay}` : '';
    const tzLine   = s.timezone ? ` (${s.timezone})` : '';
    const bosLine  = s.selectedBos && s.selectedBos.length
      ? s.selectedBos.map(n => boDisplayNames[n] || n).join(', ') : 'All BOs';

    let filterLine = '—';
    if (s.modifiedWithinPeriod) {
      filterLine = 'Modified within the period';
    } else if (s.dateField) {
      const fieldLabel = s.dateField === 'createDate' ? 'Create Date' : 'Last Modified Date';
      filterLine = fieldLabel + (s.dateFrom ? ` from ${s.dateFrom}` : '');
    }

    const nextRunLine = s.nextRunAt
      ? new Date(s.nextRunAt).toLocaleString()
      : 'Computing…';

    detail.innerHTML = `
      <div class="schedule-info-grid">
        <div class="schedule-info-item">
          <span class="schedule-info-label">Frequency</span>
          <span class="schedule-info-value">${freqLine}${dowLine}${timeLine}${tzLine}</span>
        </div>
        <div class="schedule-info-item">
          <span class="schedule-info-label">Next Run</span>
          <span class="schedule-info-value schedule-next-run">${nextRunLine}</span>
        </div>
        <div class="schedule-info-item">
          <span class="schedule-info-label">Business Objects</span>
          <span class="schedule-info-value">${bosLine}</span>
        </div>
        <div class="schedule-info-item">
          <span class="schedule-info-label">Date Filter</span>
          <span class="schedule-info-value">${filterLine}</span>
        </div>
      </div>
      <div class="schedule-actions">
        <button class="btn-secondary btn-sm" id="edit-schedule-btn" type="button">&#9998; Edit</button>
        <button class="btn-stop btn-sm" id="delete-schedule-btn" type="button">&#128465; Delete</button>
      </div>`;

    document.getElementById('edit-schedule-btn')?.addEventListener('click', async () => {
      try {
        const r = await fetch('/api/schedule');
        if (r.ok) {
          const fresh = await r.json();
          console.log('[dashboard.js] edit schedule - selectedBos:', fresh.selectedBos);
          applyScheduleToForm(fresh);
        }
      } catch (e) { console.error('[dashboard.js] Edit schedule fetch error:', e); }
    });
    document.getElementById('delete-schedule-btn')?.addEventListener('click', deleteScheduleJob);
  } catch (err) {
    console.error('[dashboard.js] loadScheduleInfo error:', err);
  }
}

// ─── Schedule edit / delete ───────────────────────────────────────────────────

function applyScheduleToForm(s) {
  const setVal = (id, val) => { const el = document.getElementById(id); if (el && val != null) el.value = val; };

  const toggle = document.getElementById('auto-schedule');
  if (toggle) { toggle.checked = true; toggle.dispatchEvent(new Event('change')); }

  setVal('frequency', s.frequency);
  document.getElementById('frequency')?.dispatchEvent(new Event('change'));

  setVal('day-of-week', s.dayOfWeek);
  setVal('hour-of-day', s.timeOfDay);
  setVal('timezone', s.timezone);
  setVal('date-field', s.dateField || '');
  setVal('date-from', s.dateFrom || '');

  const modPeriod = document.getElementById('modified-period');
  if (modPeriod) { modPeriod.checked = !!s.modifiedWithinPeriod; modPeriod.dispatchEvent(new Event('change')); }

  const selected = new Set(s.selectedBos || []);
  const allCheckboxes = document.querySelectorAll('.bo-checkbox');
  console.log('[dashboard.js] bo checkboxes in DOM:', Array.from(allCheckboxes).map(cb => cb.value), '| selected set:', [...selected]);
  allCheckboxes.forEach(cb => { cb.checked = selected.has(cb.value); });

  document.getElementById('export-config')?.scrollIntoView({ behavior: 'smooth' });
}

async function deleteScheduleJob() {
  try {
    const res = await fetch('/api/schedule', { method: 'DELETE' });
    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (res.ok) {
      const panel = document.getElementById('scheduled-job-panel');
      if (panel) panel.style.display = 'none';
    }
  } catch (err) {
    console.error('[dashboard.js] Delete schedule error:', err);
  }
}

// ─── Export trigger ───────────────────────────────────────────────────────────

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
      body: JSON.stringify({
        boNames: getSelectedBos(),
        sftpTargetPath: sftpPath,
        dateField: document.getElementById('date-field')?.value || '',
        dateFrom: document.getElementById('date-from')?.value || '',
        modifiedWithinPeriod: document.getElementById('modified-period')?.checked ?? false,
      }),
    });

    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (res.status === 409) {
      alert('An export is already running. Please wait for it to complete.');
      if (btn) { btn.disabled = false; btn.textContent = isScheduleOn() ? '📅 Schedule' : '▶ Start Export'; }
      return;
    }
    if (!res.ok) {
      alert('Failed to start export. Please try again.');
      if (btn) { btn.disabled = false; btn.textContent = isScheduleOn() ? '📅 Schedule' : '▶ Start Export'; }
      return;
    }

    document.getElementById('running-badge').style.display = '';
    document.getElementById('stop-btn').style.display = '';
    document.getElementById('status-panel').style.display = '';
    setConfigEditable(false);
    stopPolling();
    pollInterval = setInterval(pollStatus, 2000);
    pollStatus();

  } catch (err) {
    console.error('[dashboard.js] Start export error:', err);
    if (btn) { btn.disabled = false; btn.textContent = isScheduleOn() ? '📅 Schedule' : '▶ Start Export'; }
  }
}

// ─── Run history / logs ───────────────────────────────────────────────────────

function formatDuration(startedAt, completedAt) {
  if (!startedAt || !completedAt) return 'In progress';
  const ms = new Date(completedAt) - new Date(startedAt);
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  return Math.round(ms / 60000) + 'm ' + Math.round((ms % 60000) / 1000) + 's';
}

function runOverallStatus(steps) {
  const vals = Object.values(steps || {});
  if (vals.includes('FAILED'))      return { text: 'Failed',      cls: 'failed' };
  if (vals.includes('IN_PROGRESS')) return { text: 'In Progress', cls: 'in-progress' };
  if (vals.every(v => v === 'SUCCESS')) return { text: 'All Success', cls: 'success' };
  return { text: 'Pending', cls: 'pending' };
}

function renderHistory(runs) {
  const list  = document.getElementById('logs-list');
  const empty = document.getElementById('logs-empty');
  if (!list) return;

  if (!runs || !runs.length) {
    if (empty) empty.style.display = '';
    return;
  }
  if (empty) empty.style.display = 'none';

  // Remove previous entries (keep the empty message node)
  list.querySelectorAll('.log-entry').forEach(el => el.remove());

  runs.forEach(run => {
    const overall = runOverallStatus(run.steps);
    const startDt = run.startedAt ? new Date(run.startedAt) : null;
    const startStr = startDt ? startDt.toLocaleString() : '—';
    const duration = formatDuration(run.startedAt, run.completedAt);

    const stepBadges = Object.entries(run.steps || {}).map(([key, val]) => {
      const label = key.replace('EXPORT_', '').replace('_', ' ');
      const cls   = (PILL[val] || PILL.PENDING).cls;
      return `<span class="log-step-badge ${cls}">${label}</span>`;
    }).join('');

    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML = `
      <div class="log-entry-header">
        <span class="log-run-id">${startStr}</span>
        <span class="log-duration">${duration}</span>
        <span class="status-pill ${overall.cls} log-overall">${overall.text}</span>
      </div>
      <div class="log-step-badges">${stepBadges}</div>`;
    list.appendChild(entry);
  });
}

async function loadHistory() {
  try {
    const res = await fetch('/api/run/history');
    if (!res.ok) return;
    renderHistory(await res.json());
  } catch (err) {
    console.error('[dashboard.js] Failed to load history:', err);
  }
}

// ─── Page init ────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  wireScheduleToggle();
  loadBos();
  loadHistory();

  document.getElementById('start-export-btn')?.addEventListener('click', () => {
    isScheduleOn() ? saveSchedule() : startExport();
  });
  loadScheduleInfo();

  // Stop button
  document.getElementById('stop-btn')?.addEventListener('click', async () => {
    const stopBtn = document.getElementById('stop-btn');
    if (stopBtn) { stopBtn.disabled = true; stopBtn.textContent = 'Stopping…'; }
    try {
      await fetch('/api/run/stop', { method: 'POST' });
    } catch (err) {
      console.error('[dashboard.js] Stop failed:', err);
    } finally {
      if (stopBtn) { stopBtn.disabled = false; stopBtn.textContent = '⏹ Stop'; }
    }
  });

  // On page load: check if a run is already in progress
  fetch('/api/run/status')
    .then(r => r.ok ? r.json() : null)
    .then(run => {
      if (!run) return;
      if (run.steps) Object.entries(run.steps).forEach(([s, st]) => updateStepPill(s, st));
      if (!run.completedAt) {
        document.getElementById('running-badge').style.display = '';
        document.getElementById('stop-btn').style.display = '';
        document.getElementById('status-panel').style.display = '';
        setConfigEditable(false);
        pollInterval = setInterval(pollStatus, 2000);
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
