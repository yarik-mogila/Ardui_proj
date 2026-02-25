import crypto from 'node:crypto';

function envBool(name, fallback) {
  const raw = process.env[name];
  if (raw == null) {
    return fallback;
  }
  return String(raw).toLowerCase() === 'true';
}

function envInt(name, fallback) {
  const raw = process.env[name];
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : fallback;
}

function epochNow() {
  return Math.floor(Date.now() / 1000);
}

const cfg = {
  pollUrl: process.env.SIM_POLL_URL || 'http://localhost:8080/api/device/poll',
  deviceId: process.env.SIM_DEVICE_ID || 'feeder-001',
  deviceSecret: process.env.SIM_DEVICE_SECRET || '',
  signatureEnabled: envBool('SIM_SIGNATURE_ENABLED', false),
  intervalSec: Math.max(1, envInt('SIM_INTERVAL_SEC', 60)),
  firmware: process.env.SIM_FIRMWARE || '1.0.3-sim',
  rssiBase: envInt('SIM_RSSI_BASE', -55),
  verbose: envBool('SIM_VERBOSE', true)
};

const state = {
  bootEpochSec: epochNow(),
  intervalSec: cfg.intervalSec,
  rssi: cfg.rssiBase,
  error: null,
  lastFeedTs: null,
  activeProfile: null,
  profiles: new Map(),
  scheduleByProfile: new Map(),
  pendingAcks: [],
  ackSet: new Set(),
  outboundLogs: [],
  lastScheduleMinuteKey: null,
  lastConfigFingerprint: null
};

function canonicalize(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (value && typeof value === 'object') {
    const out = {};
    for (const key of Object.keys(value).sort()) {
      out[key] = canonicalize(value[key]);
    }
    return out;
  }
  return value;
}

function canonicalJson(value) {
  return JSON.stringify(canonicalize(value));
}

function hmacHex(payload, secret) {
  return crypto.createHmac('sha256', secret).update(payload).digest('hex');
}

function boundedInt(value, min, max) {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return null;
  }
  const i = Math.trunc(n);
  if (i < min || i > max) {
    return null;
  }
  return i;
}

function positiveInt(value) {
  return boundedInt(value, 1, Number.MAX_SAFE_INTEGER);
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function jitterRssi() {
  const delta = Math.floor(Math.random() * 5) - 2;
  state.rssi = clamp(state.rssi + delta, -85, -42);
}

function queueLog(type, msg, meta = {}) {
  state.outboundLogs.push({
    ts: epochNow(),
    type,
    msg,
    meta
  });

  // Prevent unbounded growth if backend is down for long time.
  if (state.outboundLogs.length > 500) {
    state.outboundLogs.splice(0, state.outboundLogs.length - 500);
  }
}

function queueAck(id) {
  if (!id || state.ackSet.has(id)) {
    return;
  }
  state.pendingAcks.push(id);
  state.ackSet.add(id);
}

function ensureProfile(name, fallbackPortionMs = 1000) {
  if (!name || typeof name !== 'string') {
    return null;
  }
  const key = name.trim();
  if (!key) {
    return null;
  }
  if (!state.profiles.has(key)) {
    state.profiles.set(key, {
      name: key,
      defaultPortionMs: positiveInt(fallbackPortionMs) || 1000
    });
  }
  return state.profiles.get(key);
}

function resolvePortion(profileName) {
  if (profileName && state.profiles.has(profileName)) {
    return state.profiles.get(profileName).defaultPortionMs;
  }
  if (state.activeProfile && state.profiles.has(state.activeProfile)) {
    return state.profiles.get(state.activeProfile).defaultPortionMs;
  }
  const first = state.profiles.values().next().value;
  return first ? first.defaultPortionMs : 1000;
}

function executeFeed(type, portionMs, meta = {}) {
  const portion = positiveInt(portionMs) || 1000;
  state.lastFeedTs = epochNow();
  const message = type === 'AUTO_FEED'
    ? `Автокормление: ${portion} ms`
    : `Ручная выдача: ${portion} ms`;
  queueLog(type, message, { ...meta, portionMs: portion, profileName: state.activeProfile });
}

function normalizeScheduleEvent(raw) {
  const hh = boundedInt(raw?.hh, 0, 23);
  const mm = boundedInt(raw?.mm, 0, 59);
  const portionMs = positiveInt(raw?.portionMs);
  if (hh == null || mm == null || portionMs == null) {
    return null;
  }
  return { hh, mm, portionMs };
}

function applyConfig(config) {
  if (!config || typeof config !== 'object') {
    return;
  }

  const fingerprint = canonicalJson(config);
  if (fingerprint === state.lastConfigFingerprint) {
    return;
  }

  if (Array.isArray(config.profiles)) {
    const nextProfiles = new Map();
    for (const rawProfile of config.profiles) {
      const name = typeof rawProfile?.name === 'string' ? rawProfile.name.trim() : '';
      const defaultPortionMs = positiveInt(rawProfile?.defaultPortionMs);
      if (!name || defaultPortionMs == null) {
        continue;
      }
      nextProfiles.set(name, { name, defaultPortionMs });
    }
    state.profiles = nextProfiles;
  }

  if (typeof config.activeProfile === 'string' && config.activeProfile.trim()) {
    state.activeProfile = config.activeProfile.trim();
  }
  if (!state.activeProfile && state.profiles.size > 0) {
    state.activeProfile = state.profiles.keys().next().value;
  }
  if (state.activeProfile) {
    ensureProfile(state.activeProfile, 1000);
  }

  if (Array.isArray(config.schedule)) {
    const next = new Map();
    for (const raw of config.schedule) {
      const profileName = typeof raw?.profileName === 'string' ? raw.profileName.trim() : '';
      const event = normalizeScheduleEvent(raw);
      if (!profileName || !event) {
        continue;
      }
      if (!next.has(profileName)) {
        next.set(profileName, []);
      }
      next.get(profileName).push(event);
    }
    state.scheduleByProfile = next;
  }

  state.lastConfigFingerprint = fingerprint;
  queueLog('INFO', 'Конфигурация синхронизирована', {
    activeProfile: state.activeProfile,
    profiles: state.profiles.size
  });
}

function executeCommand(cmd) {
  const id = typeof cmd?.id === 'string' ? cmd.id : null;
  const type = typeof cmd?.commandType === 'string' ? cmd.commandType.trim().toUpperCase() : '';
  const payload = cmd?.payloadJson && typeof cmd.payloadJson === 'object' ? cmd.payloadJson : {};
  const now = epochNow();

  try {
    if (type === 'FEED_NOW') {
      const requestedPortion = positiveInt(payload.portionMs);
      const portion = requestedPortion || resolvePortion(state.activeProfile);
      executeFeed('MANUAL_FEED', portion, { source: 'command', commandId: id });
    } else if (type === 'SET_PROFILE') {
      const profileName = typeof payload.profileName === 'string' ? payload.profileName.trim() : '';
      if (!profileName) {
        throw new Error('profileName missing');
      }
      state.activeProfile = profileName;
      ensureProfile(profileName, 1000);
      queueLog('PROFILE_CHANGED', `Активный профиль: ${profileName}`, { profileName, source: 'command' });
    } else if (type === 'SET_SCHEDULE') {
      const profileNameRaw = typeof payload.profileName === 'string' ? payload.profileName.trim() : '';
      const profileName = profileNameRaw || state.activeProfile;
      const eventsRaw = Array.isArray(payload.events) ? payload.events : [];
      const events = eventsRaw.map(normalizeScheduleEvent).filter(Boolean);
      if (!profileName) {
        throw new Error('profileName missing');
      }
      state.scheduleByProfile.set(profileName, events);
      ensureProfile(profileName, events[0]?.portionMs || 1000);
      queueLog('SCHEDULE_UPDATED', `Расписание обновлено: ${profileName}`, {
        profileName,
        eventsCount: events.length,
        source: 'command'
      });
    } else if (type === 'SET_DEFAULT_PORTION') {
      const profileNameRaw = typeof payload.profileName === 'string' ? payload.profileName.trim() : '';
      const profileName = profileNameRaw || state.activeProfile;
      const defaultPortionMs = positiveInt(payload.defaultPortionMs);
      if (!profileName || defaultPortionMs == null) {
        throw new Error('profileName/defaultPortionMs missing');
      }
      const profile = ensureProfile(profileName, defaultPortionMs);
      profile.defaultPortionMs = defaultPortionMs;
      queueLog('INFO', `Порция по умолчанию: ${defaultPortionMs} ms`, { profileName, source: 'command' });
    } else if (type === 'REBOOT') {
      state.bootEpochSec = now;
      state.error = null;
      state.lastScheduleMinuteKey = null;
      queueLog('BOOT', 'Перезагрузка устройства', { source: 'command', commandId: id });
    } else if (type === 'PING') {
      queueLog('INFO', 'PING -> PONG', { source: 'command', commandId: id, ts: now });
    } else if (type) {
      queueLog('ERROR', `Неизвестная команда: ${type}`, { payload });
    } else {
      queueLog('ERROR', 'Пустой тип команды', { payload });
    }
  } catch (error) {
    queueLog('ERROR', `Ошибка команды ${type || 'UNKNOWN'}: ${error.message}`, { payload });
  } finally {
    if (id) {
      queueAck(id);
    }
  }
}

function runScheduledFeeding(nowSec) {
  if (!state.activeProfile) {
    return;
  }
  const now = new Date(nowSec * 1000);
  const minuteKey = `${now.getFullYear()}-${now.getMonth()}-${now.getDate()}-${now.getHours()}-${now.getMinutes()}`;
  if (minuteKey === state.lastScheduleMinuteKey) {
    return;
  }
  state.lastScheduleMinuteKey = minuteKey;

  const events = state.scheduleByProfile.get(state.activeProfile) || [];
  for (const event of events) {
    if (event.hh === now.getHours() && event.mm === now.getMinutes()) {
      executeFeed('AUTO_FEED', event.portionMs, {
        source: 'schedule',
        profileName: state.activeProfile,
        hh: event.hh,
        mm: event.mm
      });
    }
  }
}

function makePollBody() {
  const nowSec = epochNow();
  jitterRssi();
  runScheduledFeeding(nowSec);

  return {
    deviceId: cfg.deviceId,
    ts: nowSec,
    status: {
      fw: cfg.firmware,
      uptimeSec: Math.max(0, nowSec - state.bootEpochSec),
      rssi: state.rssi,
      error: state.error,
      lastFeedTs: state.lastFeedTs
    },
    log: state.outboundLogs.slice(0, 100),
    ack: state.pendingAcks.slice(0, 100)
  };
}

async function pollOnce() {
  const body = makePollBody();
  const payload = canonicalJson(body);
  const headers = { 'Content-Type': 'application/json' };

  if (cfg.signatureEnabled) {
    if (!cfg.deviceSecret) {
      throw new Error('SIM_DEVICE_SECRET is required when SIM_SIGNATURE_ENABLED=true');
    }
    headers['X-Device-Id'] = cfg.deviceId;
    headers['X-Nonce'] = crypto.randomUUID();
    headers['X-Sign'] = hmacHex(payload, cfg.deviceSecret);
  }

  const res = await fetch(cfg.pollUrl, {
    method: 'POST',
    headers,
    body: payload
  });

  const text = await res.text();
  if (!res.ok) {
    console.error('[sim] poll failed', res.status, text);
    return;
  }

  let json;
  try {
    json = JSON.parse(text);
  } catch {
    console.error('[sim] invalid json', text);
    return;
  }

  const sentAckCount = body.ack.length;
  const sentLogCount = body.log.length;
  if (sentAckCount > 0) {
    const acked = state.pendingAcks.splice(0, sentAckCount);
    for (const id of acked) {
      state.ackSet.delete(id);
    }
  }
  if (sentLogCount > 0) {
    state.outboundLogs.splice(0, sentLogCount);
  }

  if (Number.isFinite(json?.intervalSec) && json.intervalSec > 0) {
    state.intervalSec = clamp(Math.trunc(json.intervalSec), 1, 3600);
  }

  applyConfig(json?.config);

  const commands = Array.isArray(json?.commands) ? json.commands : [];
  for (const command of commands) {
    executeCommand(command);
  }

  if (cfg.verbose) {
    if (commands.length > 0) {
      const list = commands.map((c) => c.commandType || 'UNKNOWN').join(', ');
      console.log('[sim] commands processed:', list);
    } else {
      console.log('[sim] heartbeat ok', new Date().toISOString());
    }
  }
}

let pollTimer = null;

async function loop() {
  try {
    await pollOnce();
  } catch (error) {
    console.error('[sim] error', error);
  } finally {
    pollTimer = setTimeout(loop, state.intervalSec * 1000);
  }
}

queueLog('BOOT', 'Симулятор устройства запущен', { firmware: cfg.firmware });
console.log('[sim] started with config', cfg);
loop();
