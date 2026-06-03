// Admin panel — stub (API wiring in later slices)
console.log('[admin.js] loaded');

let clmBoData = null; // populated after "Load from CLM"; null = CLM not loaded

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

function addBoTypeRow(name, localizedName) {
  const table = document.getElementById('bo-types-list');
  if (!table) return;
  const row = document.createElement('div');
  row.className = 'bo-type-row';
  row.innerHTML = `
    <input class="form-control form-control-sm bo-type-name" type="text" value="${name || ''}" placeholder="e.g. NAF" />
    <input class="form-control form-control-sm bo-type-localized" type="text" value="${localizedName || ''}" placeholder="e.g. North America Fleet" />
    <button class="btn-secondary btn-sm btn-icon" type="button" aria-label="Remove">×</button>`;
  row.querySelector('.btn-icon').addEventListener('click', () => row.remove());
  table.appendChild(row);
}

function setBoTypeList(boTypes) {
  const table = document.getElementById('bo-types-list');
  if (!table) return;
  table.querySelectorAll('.bo-type-row').forEach(r => r.remove());
  (boTypes || []).forEach(bt => {
    const name = typeof bt === 'string' ? bt : (bt.name || '');
    const loc  = typeof bt === 'string' ? '' : (bt.localizedName || '');
    addBoTypeRow(name, loc);
  });
}

function getBoTypeList() {
  return Array.from(document.querySelectorAll('#bo-types-list .bo-type-row')).map(row => ({
    name: row.querySelector('.bo-type-name')?.value.trim() || '',
    localizedName: row.querySelector('.bo-type-localized')?.value.trim() || '',
  })).filter(bt => bt.name);
}

function escapeHtml(str) {
  return String(str ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function renderBoClmTable(rows) {
  const tbody = document.getElementById('bo-clm-tbody');
  if (!tbody) return;
  tbody.innerHTML = '';
  rows.forEach(bo => {
    const tr = document.createElement('tr');
    tr.dataset.internal = bo.internalName;
    tr.innerHTML = `
      <td><input type="checkbox" class="bo-clm-check" ${bo.checked ? 'checked' : ''} /></td>
      <td><span class="bo-clm-internal">${escapeHtml(bo.internalName)}</span></td>
      <td><input type="text" class="form-control form-control-sm bo-clm-localized"
            value="${escapeHtml(bo.localizedName || bo.displayName)}"
            placeholder="${escapeHtml(bo.displayName)}" /></td>
      <td>${escapeHtml(bo.usageType || '')}</td>
      <td><span class="bo-fields-badge" id="fields-badge-${escapeHtml(bo.internalName)}">${bo.fieldCount != null ? bo.fieldCount + ' fields' : '—'}</span></td>
      <td><button class="btn-secondary btn-sm bo-edit-fields" type="button"
            data-bo="${escapeHtml(bo.internalName)}">Edit Fields</button></td>
    `;
    tbody.appendChild(tr);
  });
}

function getBoClmList() {
  return Array.from(document.querySelectorAll('#bo-clm-tbody tr')).map(tr => ({
    name: tr.dataset.internal || '',
    checked: tr.querySelector('.bo-clm-check')?.checked ?? false,
    localizedName: tr.querySelector('.bo-clm-localized')?.value.trim() || '',
  })).filter(bt => bt.name && bt.checked);
}

async function openFieldPicker(boType, anchorRow) {
  // Remove any existing picker
  document.querySelectorAll('.bo-field-picker-row').forEach(r => r.remove());

  // Insert a new picker row after the anchor
  const pickerRow = document.createElement('tr');
  pickerRow.className = 'bo-field-picker-row';
  pickerRow.innerHTML = `<td colspan="6"><div class="field-picker-wrap" id="field-picker-${escapeHtml(boType)}">
    <span>Loading fields…</span>
  </div></td>`;
  anchorRow.insertAdjacentElement('afterend', pickerRow);

  try {
    const [metaRes, colRes] = await Promise.all([
      fetch(`/api/admin/bo-metadata/${encodeURIComponent(boType)}`),
      fetch(`/api/admin/columns/${encodeURIComponent(boType)}`),
    ]);
    if (metaRes.status === 401 || colRes.status === 401) { window.location.href = '/index.html'; return; }
    if (!metaRes.ok) throw new Error('Metadata fetch failed');
    if (!colRes.ok) throw new Error('Columns fetch failed');

    const metadata = await metaRes.json();
    const colData = await colRes.json();
    const selectedPaths = colData.fieldPaths; // null = all selected; [] or [...] = explicit

    renderFieldPicker(pickerRow.querySelector('.field-picker-wrap'), boType, metadata, selectedPaths);
  } catch (err) {
    const wrap = pickerRow.querySelector('.field-picker-wrap');
    if (wrap) wrap.innerHTML = `<span class="error-msg">Failed to load fields: ${escapeHtml(err.message)}</span>`;
  }
}

function renderFieldPicker(container, boType, metadata, selectedPaths) {
  const allPaths = selectedPaths === null; // null means "select all"
  const selectedSet = new Set(selectedPaths || []);
  const collapseDefault = metadata.components.length > 5;

  let html = `
    <div class="fp-header">
      <strong>Fields for ${escapeHtml(metadata.boDisplayName || boType)}</strong>
      <input type="text" class="form-control form-control-sm fp-search" placeholder="Search fields…" style="max-width:220px;" />
      <button class="btn-primary btn-sm fp-apply" type="button" data-bo="${escapeHtml(boType)}">Apply</button>
      <button class="btn-secondary btn-sm fp-close" type="button">✕ Close</button>
    </div>
  `;

  metadata.components.forEach((comp, idx) => {
    const compId = `fp-comp-${escapeHtml(boType)}-${idx}`;
    const collapsed = collapseDefault ? 'collapsed' : '';
    html += `
      <details class="fp-component ${collapsed}" ${collapseDefault ? '' : 'open'}>
        <summary class="fp-comp-summary">
          <span>${escapeHtml(comp.displayName || comp.internalName)}</span>
          <span class="fp-cardinality">${escapeHtml(comp.cardinality || '')}</span>
          <button class="btn-secondary btn-sm fp-select-all" type="button" data-comp="${idx}">Select All</button>
          <button class="btn-secondary btn-sm fp-deselect-all" type="button" data-comp="${idx}">Deselect All</button>
        </summary>
        <div class="fp-fields" id="${compId}">
    `;
    (comp.fields || []).forEach(field => {
      const checked = allPaths || selectedSet.has(field.instancePath);
      html += `
        <label class="fp-field-row" data-path="${escapeHtml(field.instancePath)}" data-display="${escapeHtml((field.displayName || '').toLowerCase())} ${escapeHtml((field.instancePath || '').toLowerCase())}">
          <input type="checkbox" class="fp-field-check" data-path="${escapeHtml(field.instancePath)}" ${checked ? 'checked' : ''} />
          <span class="fp-field-display">${escapeHtml(field.displayName || field.internalName)}</span>
          <span class="fp-field-path">${escapeHtml(field.instancePath || '')}</span>
          <span class="fp-field-type">${escapeHtml(field.dataType || '')}</span>
        </label>
      `;
    });
    html += `</div></details>`;
  });

  container.innerHTML = html;

  // Wire search — also opens/closes <details> so results inside collapsed sections are visible
  container.querySelector('.fp-search')?.addEventListener('input', e => {
    const q = e.target.value.toLowerCase().trim();
    container.querySelectorAll('.fp-component').forEach(details => {
      let hasMatch = false;
      details.querySelectorAll('.fp-field-row').forEach(row => {
        const matches = !q || (row.dataset.display || '').includes(q);
        row.style.display = matches ? '' : 'none';
        if (matches) hasMatch = true;
      });
      if (q) details.open = hasMatch;
    });
  });

  // Wire Select All / Deselect All per component
  container.querySelectorAll('.fp-select-all').forEach(btn => {
    btn.addEventListener('click', e => {
      e.preventDefault();
      const idx = btn.dataset.comp;
      container.querySelectorAll(`#fp-comp-${escapeHtml(boType)}-${idx} .fp-field-check`).forEach(cb => cb.checked = true);
    });
  });
  container.querySelectorAll('.fp-deselect-all').forEach(btn => {
    btn.addEventListener('click', e => {
      e.preventDefault();
      const idx = btn.dataset.comp;
      container.querySelectorAll(`#fp-comp-${escapeHtml(boType)}-${idx} .fp-field-check`).forEach(cb => cb.checked = false);
    });
  });

  // Wire Close
  container.querySelector('.fp-close')?.addEventListener('click', () => {
    container.closest('.bo-field-picker-row')?.remove();
  });

  // Wire Apply
  container.querySelector('.fp-apply')?.addEventListener('click', async () => {
    const checkedPaths = Array.from(container.querySelectorAll('.fp-field-check:checked'))
      .map(cb => cb.dataset.path).filter(Boolean);

    console.log(`[field-picker] Apply for ${boType}: ${checkedPaths.length} paths collected`, checkedPaths);

    if (checkedPaths.length === 0) {
      const confirmAll = confirm(
        'No fields are selected. This will remove the column filter and export ALL fields. Continue?'
      );
      if (!confirmAll) return;
    }

    const applyBtn = container.querySelector('.fp-apply');
    if (applyBtn) { applyBtn.disabled = true; applyBtn.textContent = 'Saving…'; }

    try {
      const res = await fetch(`/api/admin/columns/${encodeURIComponent(boType)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fieldPaths: checkedPaths }),
      });
      if (!res.ok) throw new Error('Save failed');

      // Update badge
      const badge = document.getElementById(`fields-badge-${boType}`);
      if (badge) badge.textContent = `${checkedPaths.length} fields`;

      // Close picker
      container.closest('.bo-field-picker-row')?.remove();
    } catch (err) {
      if (applyBtn) { applyBtn.disabled = false; applyBtn.textContent = 'Apply'; }
      alert('Failed to save: ' + err.message);
    }
  });
}

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
  const pwdEl = document.getElementById('server-password');
  if (pwdEl) {
    pwdEl.value = '';
    pwdEl.placeholder = config.serverPasswordIsSet ? 'Password is set (leave blank to keep)' : 'Enter password';
  }

  // API & Endpoints
  setVal('endpoints-file', config.endpointsFile);
  setVal('batch-size', config.batchSize);
  setVal('retry-attempts', config.retryMaxAttempts);
  setVal('retry-delay', config.retryBaseDelayMs);
  setCheck('offline-mode', config.offlineMode);

  // BO Types
  setBoTypeList(config.boTypes);
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
  setCheck('convert-attachments-pdf', config.convertAttachmentsToPdf !== false);
  setCheck('include-empty-files',     config.includeEmptyExportFiles !== false);

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
  const sftpPwdEl = document.getElementById('sftp-password');
  if (sftpPwdEl) {
    sftpPwdEl.value = '';
    sftpPwdEl.placeholder = (config.sftp && config.sftp.passwordIsSet) ? 'Password is set (leave blank to keep)' : 'Enter password';
  }

  // ZIP / SFTP toggles
  const zipToggle = document.getElementById('enable-zip-packaging');
  if (zipToggle) zipToggle.checked = config.enableZipPackaging !== false;
  const sftpToggle = document.getElementById('enable-sftp-upload');
  if (sftpToggle) sftpToggle.checked = config.enableSftpUpload !== false;
  applySftpUploadToggle();

  // Admin Emails
  const adminEmailsEl = document.getElementById('admin-emails');
  if (adminEmailsEl) {
    adminEmailsEl.value = (config.adminEmails || []).join('\n');
  }

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

  const newServerPassword = val('server-password');
  const newSftpPassword = val('sftp-password');

  return {
    serverUrl: val('server-url'),
    serverUsername: val('server-username'),
    serverPassword: newServerPassword || undefined,
    endpointsFile: val('endpoints-file'),
    batchSize: num('batch-size'),
    retryMaxAttempts: num('retry-attempts'),
    retryBaseDelayMs: num('retry-delay'),
    offlineMode: bool('offline-mode'),
    boTypes: clmBoData ? getBoClmList() : getBoTypeList(),
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
    convertAttachmentsToPdf: bool('convert-attachments-pdf'),
    includeEmptyExportFiles: bool('include-empty-files'),
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
      password: newSftpPassword || undefined,
    },
    enableZipPackaging: bool('enable-zip-packaging'),
    enableSftpUpload: bool('enable-sftp-upload'),
    adminEmails: (document.getElementById('admin-emails')?.value || '')
      .split('\n').map(e => e.trim()).filter(Boolean),
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

async function flushOpenFieldPickers() {
  const pickers = document.querySelectorAll('.bo-field-picker-row .fp-apply[data-bo]');
  for (const applyBtn of pickers) {
    const boType = applyBtn.dataset.bo;
    const container = applyBtn.closest('.field-picker-wrap');
    if (!container) continue;
    const checkedPaths = Array.from(container.querySelectorAll('.fp-field-check:checked'))
      .map(cb => cb.dataset.path).filter(Boolean);
    try {
      await fetch(`/api/admin/columns/${encodeURIComponent(boType)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fieldPaths: checkedPaths }),
      });
      const badge = document.getElementById(`fields-badge-${boType}`);
      if (badge) badge.textContent = checkedPaths.length > 0 ? `${checkedPaths.length} fields` : '—';
    } catch (err) {
      console.warn(`[admin.js] Failed to save field selection for ${boType}:`, err);
    }
  }
}

async function saveConfig() {
  const btn = document.getElementById('save-btn');
  const status = document.getElementById('save-status');
  document.querySelectorAll('.field-error').forEach(el => el.remove());
  if (btn) { btn.disabled = true; btn.textContent = 'Saving…'; }

  await flushOpenFieldPickers();

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

function applySftpUploadToggle() {
  const enabled = document.getElementById('enable-sftp-upload')?.checked ?? true;
  const creds = document.getElementById('sftp-credentials');
  if (!creds) return;
  creds.querySelectorAll('input').forEach(input => {
    input.disabled = !enabled;
    input.style.opacity = enabled ? '' : '0.5';
  });
}

document.getElementById('enable-sftp-upload')?.addEventListener('change', applySftpUploadToggle);

async function loadCachedBoTypes() {
  try {
    const res = await fetch('/api/admin/cached-bo-types');
    if (!res.ok) return;
    const data = await res.json();
    if (!Array.isArray(data) || data.length === 0) return;

    clmBoData = data;
    renderBoClmTable(clmBoData);

    const tableWrap = document.getElementById('bo-clm-table-wrap');
    const manualWrap = document.getElementById('bo-manual-wrap');
    const searchEl = document.getElementById('bo-search');
    if (tableWrap) tableWrap.style.display = '';
    if (manualWrap) manualWrap.style.display = 'none';
    if (searchEl) searchEl.style.display = '';
  } catch (err) {
    console.warn('[admin.js] Could not load cached BO types:', err);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadConfig();
  loadCachedBoTypes();
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
  document.getElementById('bo-type-add')?.addEventListener('click', () => addBoTypeRow('', ''));
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

  // Load from CLM button
  document.getElementById('bo-load-clm')?.addEventListener('click', async () => {
    const spinner = document.getElementById('bo-load-spinner');
    const errorEl = document.getElementById('bo-load-error');
    const searchEl = document.getElementById('bo-search');
    const tableWrap = document.getElementById('bo-clm-table-wrap');
    const manualWrap = document.getElementById('bo-manual-wrap');

    if (spinner) spinner.style.display = 'inline';
    if (errorEl) errorEl.style.display = 'none';

    try {
      const res = await fetch('/api/admin/bo-types');
      if (res.status === 401) { window.location.href = '/index.html'; return; }
      if (!res.ok) throw new Error(await res.text());
      clmBoData = await res.json();
      renderBoClmTable(clmBoData);
      if (tableWrap) tableWrap.style.display = '';
      if (manualWrap) manualWrap.style.display = 'none';
      if (searchEl) searchEl.style.display = '';
    } catch (err) {
      if (errorEl) { errorEl.textContent = 'Failed to load BO types: ' + err.message; errorEl.style.display = 'inline'; }
    } finally {
      if (spinner) spinner.style.display = 'none';
    }
  });

  // CLM table search filter
  document.getElementById('bo-search')?.addEventListener('input', e => {
    const q = e.target.value.toLowerCase();
    document.querySelectorAll('#bo-clm-tbody tr').forEach(tr => {
      const text = tr.textContent.toLowerCase();
      tr.style.display = text.includes(q) ? '' : 'none';
    });
  });

  // Field picker — event delegation (buttons are dynamically rendered in the CLM table)
  document.getElementById('bo-clm-tbody')?.addEventListener('click', async (e) => {
    const btn = e.target.closest('.bo-edit-fields');
    if (!btn) return;
    const boType = btn.dataset.bo;
    if (!boType) return;
    const anchorRow = btn.closest('tr');
    if (!anchorRow) return;

    // If picker already open for this BO, close it
    const existingPicker = anchorRow.nextElementSibling;
    if (existingPicker?.classList.contains('bo-field-picker-row')) {
      existingPicker.remove();
      return;
    }

    await openFieldPicker(boType, anchorRow);
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
