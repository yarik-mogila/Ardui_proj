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

  window.sfApi = {
    api,
    qp,
    fmtTs,
    onlineBadge,

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

    listLogs: (deviceId, type, q, page = 0, size = 50) => {
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
