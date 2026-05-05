// Admin panel — stub (API wiring in later slices)
console.log('[admin.js] loaded');

document.addEventListener('DOMContentLoaded', () => {
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

  // Save button stub
  document.getElementById('save-btn')?.addEventListener('click', () => {
    const status = document.getElementById('save-status');
    if (status) {
      status.textContent = '✓ Saved (API wiring in next slice)';
      setTimeout(() => { status.textContent = ''; }, 3000);
    }
  });
});
