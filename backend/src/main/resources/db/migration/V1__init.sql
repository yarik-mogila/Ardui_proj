CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    secret_hash TEXT NOT NULL,
    encrypted_secret BYTEA NOT NULL,
    last_seen_at TIMESTAMPTZ,
    last_status_json JSONB,
    firmware_version TEXT,
    active_profile_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    default_portion_ms INTEGER NOT NULL CHECK (default_portion_ms > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_profiles_device_name UNIQUE (device_id, name)
);

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_active_profile
    FOREIGN KEY (active_profile_id) REFERENCES profiles(id);

CREATE TABLE IF NOT EXISTS schedule_events (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    hh SMALLINT NOT NULL CHECK (hh BETWEEN 0 AND 23),
    mm SMALLINT NOT NULL CHECK (mm BETWEEN 0 AND 59),
    portion_ms INTEGER NOT NULL CHECK (portion_ms > 0)
);

CREATE TABLE IF NOT EXISTS command_queue (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    command_type TEXT NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    acked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS feed_logs (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    ts TIMESTAMPTZ NOT NULL,
    type TEXT NOT NULL,
    message TEXT NOT NULL,
    meta_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS device_nonce (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    nonce TEXT NOT NULL,
    ts_epoch BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_nonce UNIQUE (device_id, nonce)
);

CREATE INDEX IF NOT EXISTS idx_devices_last_seen_at ON devices(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_command_queue_lookup ON command_queue(device_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_feed_logs_lookup ON feed_logs(device_id, ts DESC);
