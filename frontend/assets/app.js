(function () {
  async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(path, {
      method: options.method || 'GET',
      credentials: 'include',
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });

    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        data = { raw: text };
      }
    }

    if (!response.ok) {
      const message = data && data.error ? data.error : `HTTP ${response.status}`;
      throw new Error(message);
    }

    return data;
  }

  function qp(name) {
    const url = new URL(window.location.href);
    return url.searchParams.get(name);
  }

  function fmtTs(ts) {
    if (!ts) return 'never';
    return new Date(ts).toLocaleString();
  }

  function onlineBadge(lastSeenAt) {
    if (!lastSeenAt) return '<span class="badge badge-offline rounded-pill">OFFLINE</span>';
    const online = Date.now() - new Date(lastSeenAt).getTime() <= 120000;
    return online
      ? '<span class="badge badge-online rounded-pill">ONLINE</span>'
      : '<span class="badge badge-offline rounded-pill">OFFLINE</span>';
  }

  function isOnline(lastSeenAt) {
    if (!lastSeenAt) return false;
    return Date.now() - new Date(lastSeenAt).getTime() <= 120000;
  }

  /* ── Signal bars helper ── */
  function signalBars(rssi) {
    if (rssi == null) return '<span class="muted">—</span>';
    let level = 0;
    if (rssi > -50) level = 4;
    else if (rssi > -60) level = 3;
    else if (rssi > -70) level = 2;
    else if (rssi > -80) level = 1;
    const bars = [4, 8, 12, 16].map((h, i) =>
      `<div class="bar${i < level ? ' active' : ''}" style="height:${h}px"></div>`
    ).join('');
    return `<span class="signal-bars">${bars}</span> <small class="muted">${rssi}dBm</small>`;
  }

  /* ── Toast notification system ── */
  function ensureToastContainer() {
    let c = document.getElementById('toastContainer');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toastContainer';
      c.className = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  function toast(message, type) {
    type = type || 'info';
    const container = ensureToastContainer();
    const icons = {
      success: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
      error: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
      warning: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
      info: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>'
    };
    const el = document.createElement('div');
    el.className = `toast-item toast-${type}`;
    el.innerHTML = `${icons[type] || icons.info}<span>${message}</span>`;
    container.appendChild(el);
    setTimeout(() => {
      el.classList.add('removing');
      el.addEventListener('animationend', () => el.remove());
    }, 3500);
  }

  /* ── Button loading states ── */
  function showLoading(btn, text) {
    btn.disabled = true;
    btn._origHTML = btn.innerHTML;
    btn.innerHTML = `<span class="spinner"></span>${text || 'Loading...'}`;
  }

  function hideLoading(btn) {
    btn.disabled = false;
    if (btn._origHTML) btn.innerHTML = btn._origHTML;
  }

  /* ── Sidebar renderer ── */
  const ICONS = {
    dashboard: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>',
    device: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v6"/><path d="M8 6h8"/><rect x="4" y="8" width="16" height="12" rx="2"/><path d="M10 16h4"/></svg>',
    shield: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
    logout: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>',
    paw: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="4" r="2"/><circle cx="18" cy="8" r="2"/><circle cx="4" cy="8" r="2"/><path d="M12 18c-4 0-6-2.5-6-5 0-1.8 1.3-3 3-3h6c1.7 0 3 1.2 3 3 0 2.5-2 5-6 5z"/></svg>'
  };

  function renderSidebar(opts) {
    opts = opts || {};
    const activePage = opts.activePage || 'dashboard';
    const devices = opts.devices || [];
    const currentDeviceId = opts.currentDeviceId || null;
    const userEmail = opts.userEmail || '';

    let deviceLinks = '';
    devices.forEach(function(d) {
      const isActive = activePage === 'device' && currentDeviceId === d.deviceId;
      deviceLinks += `
        <a href="/device.html?id=${encodeURIComponent(d.deviceId)}" class="${isActive ? 'active' : ''}">
          ${ICONS.device}
          <span>${d.name}</span>
        </a>`;
    });

    const securityActive = activePage === 'security';

    return `
      <div class="topbar">
        <button class="topbar-toggle" onclick="document.querySelector('.sidebar').classList.toggle('open');document.querySelector('.sidebar-overlay').classList.toggle('open')">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
        <span class="topbar-title">Smart Feeder</span>
      </div>
      <div class="sidebar-overlay" onclick="this.classList.remove('open');document.querySelector('.sidebar').classList.remove('open')"></div>
      <aside class="sidebar">
        <a class="sidebar-brand" href="/index.html">${ICONS.paw} Smart Feeder</a>
        <nav class="sidebar-nav">
          <a href="/index.html" class="${activePage === 'dashboard' ? 'active' : ''}">${ICONS.dashboard} Dashboard</a>
          ${deviceLinks ? '<div class="sidebar-divider"></div>' + deviceLinks : ''}
          ${currentDeviceId ? '<div class="sidebar-divider"></div><a href="/security.html?id=' + encodeURIComponent(currentDeviceId) + '" class="' + (securityActive ? 'active' : '') + '">' + ICONS.shield + ' Security</a>' : ''}
        </nav>
        <div class="sidebar-footer">
          <div class="sidebar-user">${userEmail}</div>
          <button onclick="sfApi.logout().finally(function(){ location.href='/index.html'; })">
            ${ICONS.logout} Logout
          </button>
        </div>
      </aside>`;
  }

  window.sfApi = {
    api,
    qp,
    fmtTs,
    onlineBadge,
    isOnline,
    signalBars,
    toast,
    showLoading,
    hideLoading,
    renderSidebar,
    ICONS,

    register: (email, password) => api('/api/auth/register', { method: 'POST', body: { email, password } }),
    login: (email, password) => api('/api/auth/login', { method: 'POST', body: { email, password } }),
    logout: () => api('/api/auth/logout', { method: 'POST' }),
    me: () => api('/api/auth/me'),

    listDevices: () => api('/api/admin/devices'),
    createDevice: (deviceId, name) => api('/api/admin/devices', { method: 'POST', body: { deviceId, name } }),
    getDevice: (deviceId) => api(`/api/admin/devices/${encodeURIComponent(deviceId)}`),
    rotateSecret: (deviceId) => api(`/api/admin/devices/${encodeURIComponent(deviceId)}/rotate-secret`, { method: 'POST' }),

    createProfile: (deviceId, name, defaultPortionMs) => api(`/api/admin/devices/${encodeURIComponent(deviceId)}/profiles`, {
      method: 'POST',
      body: { name, defaultPortionMs }
    }),

    updateProfile: (profileId, body) => api(`/api/admin/profiles/${encodeURIComponent(profileId)}`, {
      method: 'PATCH',
      body
    }),

    deleteProfile: (profileId) => api(`/api/admin/profiles/${encodeURIComponent(profileId)}`, {
      method: 'DELETE'
    }),

    replaceSchedule: (profileId, events) => api(`/api/admin/profiles/${encodeURIComponent(profileId)}/schedule`, {
      method: 'PUT',
      body: { events }
    }),

    setActiveProfile: (deviceId, profileName) => api(`/api/admin/devices/${encodeURIComponent(deviceId)}/active-profile`, {
      method: 'POST',
      body: { profileName }
    }),

    feedNow: (deviceId, portionMs) => api(`/api/admin/devices/${encodeURIComponent(deviceId)}/feed-now`, {
      method: 'POST',
      body: { portionMs }
    }),

    listLogs: (deviceId, type, q, page, size) => {
      page = page || 0;
      size = size || 50;
      const params = new URLSearchParams();
      if (type) params.set('type', type);
      if (q) params.set('q', q);
      params.set('page', page);
      params.set('size', size);
      return api(`/api/admin/devices/${encodeURIComponent(deviceId)}/logs?${params.toString()}`);
    },

    securityConfig: () => api('/api/admin/security/config')
  };
})();
