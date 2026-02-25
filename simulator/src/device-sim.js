import crypto from 'node:crypto';

const cfg = {
  pollUrl: process.env.SIM_POLL_URL || 'http://localhost:8080/api/device/poll',
  deviceId: process.env.SIM_DEVICE_ID || 'feeder-001',
  deviceSecret: process.env.SIM_DEVICE_SECRET || '',
  signatureEnabled: String(process.env.SIM_SIGNATURE_ENABLED || 'false').toLowerCase() === 'true',
  intervalSec: Number(process.env.SIM_INTERVAL_SEC || '60')
};

let ackQueue = [];
let uptimeSec = 0;

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

async function poll() {
  uptimeSec += cfg.intervalSec;

  const body = {
    deviceId: cfg.deviceId,
    ts: Math.floor(Date.now() / 1000),
    status: {
      fw: '1.0.3-sim',
      uptimeSec,
      rssi: -55,
      error: null,
      lastFeedTs: null
    },
    log: [],
    ack: ackQueue
  };

  const payload = canonicalJson(body);
  const headers = {
    'Content-Type': 'application/json'
  };

  if (cfg.signatureEnabled) {
    if (!cfg.deviceSecret) {
      throw new Error('SIM_DEVICE_SECRET is required when SIM_SIGNATURE_ENABLED=true');
    }
    const nonce = crypto.randomUUID();
    headers['X-Device-Id'] = cfg.deviceId;
    headers['X-Nonce'] = nonce;
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

  const commands = Array.isArray(json.commands) ? json.commands : [];
  ackQueue = commands.map((c) => c.id).filter(Boolean);

  if (commands.length) {
    console.log('[sim] received commands', commands);
  } else {
    console.log('[sim] heartbeat ok', new Date().toISOString());
  }
}

console.log('[sim] started with config', cfg);
await poll();
setInterval(() => {
  poll().catch((err) => console.error('[sim] error', err));
}, cfg.intervalSec * 1000);
