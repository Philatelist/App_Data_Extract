// Admin panel — stub (API wiring in later slices)
console.log('[admin.js] loaded');

// --- Config helpers ---

const setVal = (id, val) => {
  const el = document.getElementById(id);
  if (el) el.value = val ?? '';
};

const setCheck = (id, val) => {
  const el = document.getElementById(id);
  if (el) el.checked = !!val;
};

const setSelect = (id, val) => {
  const el = document.getElementById(id);
  if (el && val != null) el.value = val;
};

const setTagList = (listId, values) => {
  const list = document.getElementById(listId);
  if (!list) return;
  list.querySelectorAll('.tag').forEach(t => t.remove());
  (values || []).forEach(val => {
    if (!val) return;
    const tag = document.createElement('span');
    tag.className = 'tag';
    tag.innerHTML = `${val} <button class="tag-remove" type="button">×</button>`;
    tag.querySelector('.tag-remove').addEventListener('click', () => tag.remove());
    list.appendChild(tag);
  });
};

const setAdditionalCols = (cols) => {
  const list = document.getElementById('additional-cols-list');
  if (!list) return;
  list.querySelectorAll('.additional-col-row').forEach(r => r.remove());
  (cols || []).forEach(col => {
    const row = document.createElement('div');
    row.className = 'additional-col-row';
    row.innerHTML = `<input class="form-control form-control-sm" value="${col.header || ''}" placeholder="Header name" /><input class="form-control form-control-sm" type="number" min="1" value="${col.position || ''}" placeholder="Position" style="width:80px" /><button class="btn-secondary btn-sm btn-icon" type="button">×</button>`;
    row.querySelector('.btn-icon').addEventListener('click', () => row.remove());
    list.appendChild(row);
  });
};

function populateForm(config) {
  // Server Connection
  setVal('server-url', config.serverUrl);
  setVal('server-username', config.serverUsername);
  setVal('server-password', config.serverPassword);

  // API & Endpoints
  setVal('endpoints-file', config.endpointsFile);
  setVal('batch-size', config.batchSize);
  setVal('retry-attempts', config.retryMaxAttempts);
  setVal('retry-delay', config.retryBaseDelayMs);
  setCheck('offline-mode', config.offlineMode);

  // BO Types
  setTagList('bo-types-list', config.boTypes);
  setSelect('bo-usage-filter', config.boUsageTypeFilter);
  setVal('tracking-filter', config.trackingFilter);

  // Output & Files
  setVal('output-root', config.outputRoot);
  setVal('export-folder', config.exportFolderName);
  setVal('backup-days', config.backupRetentionDays);
  setSelect('csv-mode', config.csvMode);
  setVal('delimiter', config.delimiter);
  setVal('filename-template', config.filenameTemplate);
  setVal('downloads-template', config.downloadsFilenameTemplate);
  setCheck('gen-summary', config.generateSummaryCsv);
  setVal('summary-template', config.summaryFilenameTemplate);
  setCheck('gen-parent', config.generateParentCsv);
  setVal('parent-template', config.parentFilenameTemplate);

  // Column Filtering
  setTagList('skip-columns-list', config.skipColumns);
  setTagList('skip-components-list', config.skipComponents);
  setAdditionalCols(config.additionalColumns);

  // Delimiter Replacement
  setCheck('delim-replace-enabled', config.delimiterReplacementEnabled);
  setVal('delim-substitute', config.delimiterSubstituteChar);

  // Yes/No Translation
  setCheck('yesno-enabled', config.yesNoTranslationEnabled);
  setVal('yesno-true', config.yesNoTrueValue);
  setVal('yesno-false', config.yesNoFalseValue);

  // Date Format
  const df = config.dateFormat || {};
  setTagList('input-date-list', df.inputFormats);
  setVal('output-date', df.outputFormat);
  setTagList('input-datetime-list', df.inputDateTimeFormats);
  setVal('output-datetime', df.outputDateTimeFormat);

  // SFTP
  const sftp = config.sftp || {};
  setVal('sftp-host', sftp.host);
  setVal('sftp-port', sftp.port);
  setVal('sftp-username', sftp.username);
  setVal('sftp-password', sftp.password);

  // Re-trigger conditional visibility after populating checkboxes
  document.getElementById('gen-summary')?.dispatchEvent(new Event('change'));
  document.getElementById('gen-parent')?.dispatchEvent(new Event('change'));
  document.getElementById('delim-replace-enabled')?.dispatchEvent(new Event('change'));
  document.getElementById('yesno-enabled')?.dispatchEvent(new Event('change'));
}

async function loadConfig() {
  try {
    const res = await fetch('/api/config');
    if (res.status === 401) { window.location.href = '/index.html'; return; }
    if (!res.ok) return;
    const config = await res.json();
    populateForm(config);
  } catch (err) {
    console.error('[admin.js] Failed to load config:', err);
  }
}

function getTagListValues(listId) {
  const list = document.getElementById(listId);
  if (!list) return [];
  return Array.from(list.querySelectorAll('.tag')).map(t => {
    const clone = t.cloneNode(true);
    clone.querySelectorAll('button').forEach(b => b.remove());
    return clone.textContent.trim();
  }).filter(Boolean);
}

function collectConfig() {
  const val = id => document.getElementById(id)?.value?.trim() ?? '';
  const num = id => { const v = document.getElementById(id)?.value; return v === '' || v == null ? null : Number(v); };
  const bool = id => document.getElementById(id)?.checked ?? false;

  const additionalColumns = Array.from(
    document.getElementById('additional-cols-list')?.querySelectorAll('.additional-col-row') || []
  ).map(row => {
    const inputs = row.querySelectorAll('input');
    return { header: inputs[0]?.value?.trim() || '', position: Number(inputs[1]?.value) || 1 };
  }).filter(c => c.header);

  return {
    serverUrl: val('server-url'),
    serverUsername: val('server-username'),
    serverPassword: val('server-password'),
    endpointsFile: val('endpoints-file'),
    batchSize: num('batch-size'),
    retryMaxAttempts: num('retry-attempts'),
    retryBaseDelayMs: num('retry-delay'),
    offlineMode: bool('offline-mode'),
    boTypes: getTagListValues('bo-types-list'),
    boUsageTypeFilter: val('bo-usage-filter'),
    trackingFilter: val('tracking-filter'),
    outputRoot: val('output-root'),
    exportFolderName: val('export-folder'),
    backupRetentionDays: num('backup-days'),
    csvMode: val('csv-mode'),
    delimiter: val('delimiter'),
    filenameTemplate: val('filename-template'),
    downloadsFilenameTemplate: val('downloads-template'),
    generateSummaryCsv: bool('gen-summary'),
    summaryFilenameTemplate: val('summary-template'),
    generateParentCsv: bool('gen-parent'),
    parentFilenameTemplate: val('parent-template'),
    skipColumns: getTagListValues('skip-columns-list'),
    skipComponents: getTagListValues('skip-components-list'),
    additionalColumns,
    delimiterReplacementEnabled: bool('delim-replace-enabled'),
    delimiterSubstituteChar: val('delim-substitute'),
    yesNoTranslationEnabled: bool('yesno-enabled'),
    yesNoTrueValue: val('yesno-true'),
    yesNoFalseValue: val('yesno-false'),
    dateFormat: {
      inputFormats: getTagListValues('input-date-list'),
      outputFormat: val('output-date'),
      inputDateTimeFormats: getTagListValues('input-datetime-list'),
      outputDateTimeFormat: val('output-datetime'),
    },
    sftp: {
      host: val('sftp-host'),
      port: num('sftp-port'),
      username: val('sftp-username'),
      password: val('sftp-password'),
    },
  };
}

function showFieldErrors(errors) {
  document.querySelectorAll('.field-error').forEach(el => el.remove());
  (errors || []).forEach(({ field, message }) => {
    const fieldMap = {
      serverUrl: 'server-url', endpointsFile: 'endpoints-file', outputRoot: 'output-root',
      batchSize: 'batch-size', retryMaxAttempts: 'retry-attempts', retryBaseDelayMs: 'retry-delay',
      delimiter: 'delimiter', csvMode: 'csv-mode', delimiterSubstituteChar: 'delim-substitute',
    };
    const elId = fieldMap[field] || field;
    const el = document.getElementById(elId);
    if (el) {
      const span = document.createElement('span');
      span.className = 'field-error';
      span.textContent = message;
      el.parentNode.insertBefore(span, el.nextSibling);
    }
  });
}

async function saveConfig() {
  const btn = document.getElementById('save-btn');
  const status = document.getElementById('save-status');
  document.querySelectorAll('.field-error').forEach(el => el.remove());
  if (btn) { btn.disabled = true; btn.textContent = 'Saving…'; }

  try {
    const res = await fetch('/api/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(collectConfig()),
    });

    if (res.status === 401) { window.location.href = '/index.html'; return; }

    if (res.status === 400) {
      const body = await res.json();
      showFieldErrors(body.errors);
      if (status) { status.className = 'save-status save-status--error'; status.textContent = 'Please fix the errors above.'; }
    } else if (res.ok) {
      if (status) {
        status.className = 'save-status save-status--ok';
        status.textContent = '✓ Configuration saved successfully.';
        setTimeout(() => { status.textContent = ''; }, 4000);
      }
    } else {
      if (status) { status.className = 'save-status save-status--error'; status.textContent = 'Save failed. Please try again.'; }
    }
  } catch (err) {
    console.error('[admin.js] Save failed:', err);
    if (status) { status.className = 'save-status save-status--error'; status.textContent = 'Network error. Please try again.'; }
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = 'Save Configuration'; }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadConfig();
  // Conditional field visibility
  const toggleConditional = (checkboxId, groupId, defaultShow) => {
    const cb = document.getElementById(checkboxId);
    const group = document.getElementById(groupId);
    if (!cb || !group) return;
    const update = () => { group.style.display = cb.checked ? '' : 'none'; };
    cb.addEventListener('change', update);
    if (!defaultShow) update();
  };
  toggleConditional('gen-summary', 'summary-template-group', true);
  toggleConditional('gen-parent', 'parent-template-group', false);
  toggleConditional('delim-replace-enabled', 'delim-substitute-group', true);
  toggleConditional('yesno-enabled', 'yesno-values-group', false);

  // Generic tag-add helper
  const wireTagAdd = (inputId, btnId, listId) => {
    const input = document.getElementById(inputId);
    const btn = document.getElementById(btnId);
    const list = document.getElementById(listId);
    if (!input || !btn || !list) return;
    const addTag = () => {
      const val = input.value.trim();
      if (!val) return;
      const tag = document.createElement('span');
      tag.className = 'tag';
      tag.innerHTML = `${val} <button class="tag-remove" type="button">×</button>`;
      tag.querySelector('.tag-remove').addEventListener('click', () => tag.remove());
      list.appendChild(tag);
      input.value = '';
    };
    btn.addEventListener('click', addTag);
    input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } });
  };
  wireTagAdd('bo-type-input', 'bo-type-add', 'bo-types-list');
  wireTagAdd('skip-column-input', 'skip-column-add', 'skip-columns-list');
  wireTagAdd('skip-component-input', 'skip-component-add', 'skip-components-list');
  wireTagAdd('input-date-input', 'input-date-add', 'input-date-list');
  wireTagAdd('input-datetime-input', 'input-datetime-add', 'input-datetime-list');

  // Wire existing remove buttons
  document.querySelectorAll('.tag-remove').forEach(btn => {
    btn.addEventListener('click', () => btn.closest('.tag')?.remove());
  });

  // Additional columns add
  document.getElementById('add-col-btn')?.addEventListener('click', () => {
    const list = document.getElementById('additional-cols-list');
    const row = document.createElement('div');
    row.className = 'additional-col-row';
    row.innerHTML = `<input class="form-control form-control-sm" placeholder="Header name" /><input class="form-control form-control-sm" type="number" min="1" placeholder="Position" style="width:80px" /><button class="btn-secondary btn-sm btn-icon" type="button">×</button>`;
    row.querySelector('.btn-icon').addEventListener('click', () => row.remove());
    list.appendChild(row);
  });

  // Wire remove button on initial additional column row
  document.getElementById('remove-initial-col')?.addEventListener('click', function () {
    this.closest('.additional-col-row')?.remove();
  });

  document.getElementById('save-btn')?.addEventListener('click', saveConfig);

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
