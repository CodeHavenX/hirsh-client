function renderSidebar(activePage) {
  return `
    <div class="sidebar">
      <div class="brand">
        <div class="brand-mark">H</div>
        <div>
          <div class="brand-name">HISS</div>
          <div class="brand-sub">Hospital Maria Auxiliadora</div>
        </div>
      </div>
      <div class="nav">
        <a class="nav-item ${activePage === 'patients' ? 'active' : ''}" href="patients.html">
          <span class="ico"></span> Pacientes
        </a>
        <a class="nav-item ${activePage === 'accounts' ? 'active' : ''}" href="accounts.html">
          <span class="ico ico-round"></span> Cuentas
          <span class="tag">admin</span>
        </a>
      </div>
      <div class="nav-divider">Fase 2</div>
      <div class="nav">
        <span class="nav-item disabled"><span class="ico"></span> Agenda</span>
        <span class="nav-item disabled"><span class="ico"></span> Reportes</span>
        <span class="nav-item disabled"><span class="ico"></span> Interconsultas</span>
      </div>
      <div class="sidebar-spacer"></div>
      <a class="user-block" href="profile.html">
        <div class="user-avatar">AP</div>
        <div>
          <div class="user-name">Dr. A. Patel</div>
          <div class="user-role">Medico</div>
        </div>
      </a>
      <a class="signout" href="index.html" onclick="Store.clear()">Cerrar sesion</a>
    </div>`;
}

function renderHeader({ title, crumb, search = false, actions = '' }) {
  const searchHtml = search ? `
    <div class="search-box">
      <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="6" r="5"/><path d="M10 10l3 3"/></svg>
      <input type="text" placeholder="Buscar paciente por nombre o DNI..." id="search-input">
    </div>` : '';

  const titleHtml = title ? `
    <div>
      ${crumb ? `<div class="header-crumb">${crumb}</div>` : ''}
      <div class="header-title">${title}</div>
    </div>` : '';

  return `
    <div class="header">
      <button class="nav-toggle" onclick="toggleNav()" aria-label="Menu">☰</button>
      ${titleHtml}
      ${searchHtml}
      <div class="header-spacer"></div>
      ${actions}
    </div>`;
}

// Full page shell: sidebar + header + body, for sidebar-driven pages.
function renderAppShell(activePage, headerOpts, bodyHtml) {
  return `
    <div class="app">
      ${renderSidebar(activePage)}
      <div class="sidebar-scrim" onclick="toggleNav()"></div>
      <div class="main">
        ${renderHeader(headerOpts)}
        <div class="body">
          ${bodyHtml}
        </div>
      </div>
    </div>`;
}

// Encounter bar: top bar used by the focused, no-sidebar screens (evolucion and historia clinica).
function renderEncounterBar({ title, meta = '', actions = '', closeHref = 'record.html' }) {
  return `
    <div class="encounter-bar">
      <div class="encounter-left">
        <a class="encounter-close" href="${closeHref}">✕</a>
        <div>
          <div style="font-weight:600;font-size:15px">${title}</div>
          <div class="encounter-meta">${meta}</div>
        </div>
      </div>
      <div class="flex-gap">${actions}</div>
    </div>`;
}

// Flex-row data table. columns: [{ label, width, style, render(row, i) }]
function renderDataTable(columns, rows, { rowClass, rowAttrs } = {}) {
  const head = `
    <div class="tr tr-head">
      ${columns.map(c => `<div class="th" style="flex:${c.width || 1}">${c.label || ''}</div>`).join('')}
    </div>`;

  const body = rows.map((row, i) => {
    const cells = columns.map(c => {
      const style = c.style ? `flex:${c.width || 1};${c.style}` : `flex:${c.width || 1}`;
      return `<div class="td" data-label="${c.label || ''}" style="${style}"><span class="td-value">${c.render(row, i)}</span></div>`;
    }).join('');
    return `<div class="tr ${rowClass ? rowClass(row, i) : ''}" ${rowAttrs ? rowAttrs(row, i) : ''}>${cells}</div>`;
  }).join('');

  return head + body;
}

// Read-only label/value pair used in patient record and encounter view.
function renderField(label, text) {
  return `<div class="ro-field"><div class="ro-label">${label}</div><div class="ro-text">${text}</div></div>`;
}

// Label/value row used in profile and record cards.
function renderKV(label, value) {
  return `<div class="kv"><span>${label}</span><b>${value}</b></div>`;
}

// Labeled paragraph section used on the printable evolucion/historia clinica sheets.
function renderPrintSection(label, html) {
  return `<div class="print-sec"><h4>${label}</h4><p>${html}</p></div>`;
}

// Editable .field markup for patient demographics, driven by PATIENT_FIELDS
// (shared/data.js) so register.html and patient-edit.html render the exact
// same fields instead of duplicating markup that can drift apart. `keys`
// optionally restricts which fields to render (callers compose the rest of
// the form, including any fields outside the PATIENT_FIELDS model, around
// this); omit it to render all of them.
function renderPatientFieldsForm(values, keys) {
  const fields = keys ? PATIENT_FIELDS.filter(f => keys.includes(f.key)) : PATIENT_FIELDS;
  return fields.map(f => {
    const id = patientFieldId(f.key);
    const val = values[f.key] || '';
    const reqMark = f.required ? ' <span class="req">*</span>' : '';
    let control;
    if (f.type === 'select') {
      const opts = f.options.map(o => {
        const optVal = typeof o === 'string' ? o : o.value;
        const optLabel = typeof o === 'string' ? o : o.label;
        return `<option value="${optVal}" ${val === optVal ? 'selected' : ''}>${optLabel}</option>`;
      }).join('');
      control = `<select id="${id}"><option value="">Seleccionar...</option>${opts}</select>`;
    } else if (f.type === 'textarea') {
      control = `<textarea id="${id}" rows="${f.rows || 2}" placeholder="${f.placeholder || ''}">${val}</textarea>`;
    } else if (f.type === 'date') {
      control = `<input type="date" id="${id}" value="${val ? parseDobToInput(val) : ''}">`;
    } else {
      control = `<input type="text" id="${id}" placeholder="${f.placeholder || ''}"${f.maxlength ? ` maxlength="${f.maxlength}"` : ''} value="${val}">`;
    }
    return `<div class="field"><label>${f.label}${reqMark}</label>${control}</div>`;
  }).join('');
}

// Hospitalizacion estado badge: 'Activa' reads as in-progress, 'Alta' as done.
function renderHospEstadoBadge(estado) {
  return `<span class="badge ${estado === 'Activa' ? 'badge-prog' : 'badge-done'}">${estado}</span>`;
}

// Historia Clinica completion badge, derived from hcCompletionCount/hcStatusLabel (shared/data.js).
function renderHcStatusBadge(historiaClinica) {
  const { done, total } = hcCompletionCount(historiaClinica);
  const label = hcStatusLabel(historiaClinica);
  const cls = label === 'Completa' ? 'badge-done' : label === 'Borrador' ? 'badge-off' : 'badge-prog';
  return `<span class="badge ${cls}">${label} · ${done}/${total} secciones</span>`;
}

// Left rail for the Historia Clinica section-nav editor. HC_SECTIONS (shared/data.js)
// drives the fixed list of sections; stub (not yet implemented) sections render
// as disabled "Proximamente" entries instead of being clickable.
function renderSectionNav(sections, activeKey) {
  return HC_SECTIONS.map(s => {
    const sec = sections[s.key] || { complete: false };
    if (!s.implemented) {
      return `<span class="hc-nav-item hc-nav-stub">
        <span class="hc-nav-ico">…</span> ${s.label}
        <span class="hc-nav-tag">Proximamente</span>
      </span>`;
    }
    return `<a class="hc-nav-item ${activeKey === s.key ? 'active' : ''}" href="#${s.key}" onclick="selectHcSection('${s.key}');return false;">
      <span class="hc-nav-ico ${sec.complete ? 'hc-nav-ico-done' : ''}">${sec.complete ? '✓' : '○'}</span> ${s.label}
    </a>`;
  }).join('');
}
