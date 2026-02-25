(function () {
  async function api(path, options) {
    options = options || {};
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
    return new URL(window.location.href).searchParams.get(name);
  }

  function fmtTs(ts) {
    if (!ts) return 'never';
    return new Date(ts).toLocaleString();
  }

  function relativeTime(ts) {
    if (!ts) return 'never seen';
    const diff = Date.now() - new Date(ts).getTime();
    if (diff < 0) return 'just now';

    const sec = Math.floor(diff / 1000);
    if (sec < 60) return `${sec}s ago`;
    const min = Math.floor(sec / 60);
    if (min < 60) return `${min}m ago`;
    const hour = Math.floor(min / 60);
    if (hour < 24) return `${hour}h ago`;
    const day = Math.floor(hour / 24);
    return `${day}d ago`;
  }

  function isOnline(lastSeenAt) {
    if (!lastSeenAt) return false;
    return Date.now() - new Date(lastSeenAt).getTime() <= 120000;
  }

  function onlineBadge(lastSeenAt) {
    const online = isOnline(lastSeenAt);
    return online
      ? '<span class="status-badge status-online">ONLINE</span>'
      : '<span class="status-badge status-offline">OFFLINE</span>';
  }

  function signalBars(rssi) {
    if (rssi == null) return '<span class="muted">No signal data</span>';

    let level = 0;
    if (rssi > -50) level = 4;
    else if (rssi > -60) level = 3;
    else if (rssi > -70) level = 2;
    else if (rssi > -80) level = 1;

    const bars = [4, 8, 12, 16]
      .map((height, i) => `<span class="bar${i < level ? ' active' : ''}" style="height:${height}px"></span>`)
      .join('');

    return `<span class="signal-bars" title="${rssi} dBm">${bars}</span> <small class="muted">${rssi} dBm</small>`;
  }

  function ensureToastContainer() {
    let node = document.getElementById('toastContainer');
    if (!node) {
      node = document.createElement('div');
      node.id = 'toastContainer';
      node.className = 'toast-container';
      document.body.appendChild(node);
    }
    return node;
  }

  function toast(message, type) {
    type = type || 'info';
    const icons = {
      success: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6 9 17l-5-5"/></svg>',
      error: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/></svg>',
      warning: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 9v4"/><path d="M12 17h.01"/><path d="m3 20 9-16 9 16Z"/></svg>',
      info: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>'
    };

    const el = document.createElement('div');
    el.className = `toast-item toast-${type}`;
    el.innerHTML = `${icons[type] || icons.info}<span>${message}</span>`;
    ensureToastContainer().appendChild(el);

    setTimeout(() => {
      el.classList.add('toast-hide');
      el.addEventListener('animationend', () => el.remove());
    }, 3400);
  }

  function showLoading(btn, text) {
    if (!btn) return;
    btn.disabled = true;
    btn._original = btn.innerHTML;
    btn.innerHTML = `<span class="spinner"></span>${text || 'Loading...'}`;
  }

  function hideLoading(btn) {
    if (!btn) return;
    btn.disabled = false;
    if (btn._original) {
      btn.innerHTML = btn._original;
    }
  }

  function toggleSidebar(forceOpen) {
    const sidebar = document.querySelector('.sidebar');
    const overlay = document.querySelector('.sidebar-overlay');
    if (!sidebar || !overlay) return;

    const willOpen = typeof forceOpen === 'boolean' ? forceOpen : !sidebar.classList.contains('open');
    sidebar.classList.toggle('open', willOpen);
    overlay.classList.toggle('open', willOpen);
    document.body.classList.toggle('sidebar-open', willOpen);
  }

  function closeSidebar() {
    toggleSidebar(false);
  }

  const ICONS = {
    dashboard: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="5" rx="2"/><rect x="13" y="10" width="8" height="11" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/></svg>',
    device: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="6" y="3" width="12" height="18" rx="3"/><path d="M9 7h6"/><path d="M12 17h.01"/></svg>',
    security: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3 4 7v5c0 5.2 3.4 8.8 8 10 4.6-1.2 8-4.8 8-10V7z"/><path d="m9.5 12.5 2 2 4-4"/></svg>',
    logout: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 17v-2a4 4 0 0 1 4-4h7"/><path d="M21 11 17 7"/><path d="m21 11-4 4"/><path d="M14 21H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3h8"/></svg>',
    paw: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="4" r="2"/><circle cx="18" cy="8" r="2"/><circle cx="4" cy="8" r="2"/><path d="M12 19c-4 0-6-2.5-6-5 0-1.8 1.3-3 3-3h6c1.7 0 3 1.2 3 3 0 2.5-2 5-6 5z"/></svg>'
  };

  function renderSidebar(opts) {
    opts = opts || {};
    const activePage = opts.activePage || 'dashboard';
    const devices = opts.devices || [];
    const currentDeviceId = opts.currentDeviceId || null;
    const userEmail = opts.userEmail || 'guest@local';

    const deviceItems = devices
      .map((d) => {
        const active = activePage === 'device' && currentDeviceId === d.deviceId;
        const stateClass = isOnline(d.lastSeenAt) ? 'dot-online' : 'dot-offline';
        return `
          <a href="device.html?id=${encodeURIComponent(d.deviceId)}" class="${active ? 'active' : ''}" onclick="sfApi.closeSidebar()">
            <span class="sidebar-icon">${ICONS.device}</span>
            <span class="sidebar-line">
              <span>${d.name}</span>
              <small>${d.deviceId}</small>
            </span>
            <span class="sidebar-dot ${stateClass}"></span>
          </a>`;
      })
      .join('');

    const securityLink = currentDeviceId
      ? `<a href="security.html?id=${encodeURIComponent(currentDeviceId)}" class="${activePage === 'security' ? 'active' : ''}" onclick="sfApi.closeSidebar()"><span class="sidebar-icon">${ICONS.security}</span><span class="sidebar-line"><span>Security</span><small>Secret and auth mode</small></span></a>`
      : '';

    return `
      <header class="topbar">
        <button class="topbar-toggle" onclick="sfApi.toggleSidebar()" aria-label="Toggle menu">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16"/><path d="M4 12h16"/><path d="M4 17h16"/></svg>
        </button>
        <div class="topbar-title-wrap">
          <strong class="topbar-title">Smart Feeder Cloud</strong>
          <small>Control Layer</small>
        </div>
      </header>
      <div class="sidebar-overlay" onclick="sfApi.closeSidebar()"></div>
      <aside class="sidebar">
        <a class="sidebar-brand" href="index.html">
          <span class="brand-mark">${ICONS.paw}</span>
          <span class="brand-copy">
            <strong>Smart Feeder</strong>
            <small>Fleet Console 2026</small>
          </span>
        </a>
        <div class="sidebar-user">${userEmail}</div>
        <nav class="sidebar-nav">
          <a href="index.html" class="${activePage === 'dashboard' ? 'active' : ''}" onclick="sfApi.closeSidebar()">
            <span class="sidebar-icon">${ICONS.dashboard}</span>
            <span class="sidebar-line"><span>Dashboard</span><small>Overview</small></span>
          </a>
          ${deviceItems ? '<div class="sidebar-section">Devices</div>' + deviceItems : ''}
          ${securityLink}
        </nav>
        <div class="sidebar-footer">
          <button class="sidebar-logout" onclick="sfApi.logout().finally(function(){location.href='index.html';})">
            <span class="sidebar-icon">${ICONS.logout}</span>
            <span class="sidebar-line"><span>Logout</span><small>End session</small></span>
          </button>
        </div>
      </aside>`;
  }

  window.sfApi = {
    api,
    qp,
    fmtTs,
    relativeTime,
    onlineBadge,
    isOnline,
    signalBars,
    toast,
    showLoading,
    hideLoading,
    renderSidebar,
    toggleSidebar,
    closeSidebar,
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
      const params = new URLSearchParams();
      if (type) params.set('type', type);
      if (q) params.set('q', q);
      params.set('page', page || 0);
      params.set('size', size || 50);
      return api(`/api/admin/devices/${encodeURIComponent(deviceId)}/logs?${params.toString()}`);
    },

    securityConfig: () => api('/api/admin/security/config')
  };
})();
