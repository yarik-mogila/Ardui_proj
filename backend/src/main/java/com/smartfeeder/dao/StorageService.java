package com.smartfeeder.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;

@Component
public final class StorageService {
    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    private final DbClient dbClient;

    public StorageService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public record UserRow(String id, String email, String passwordHash, Instant createdAt) {}

    public record SessionRow(String id, String userId, Instant expiresAt) {}

    public record DeviceRow(String id,
                            String ownerUserId,
                            String name,
                            String secretHash,
                            byte[] encryptedSecret,
                            Instant lastSeenAt,
                            String lastStatusJson,
                            String firmwareVersion,
                            String activeProfileId,
                            Instant createdAt) {}

    public record DeviceListRow(String id,
                                String name,
                                Instant lastSeenAt,
                                Integer rssi,
                                String firmwareVersion,
                                String activeProfileName) {}

    public record ProfileRow(String id,
                             String deviceId,
                             String name,
                             int defaultPortionMs,
                             Instant createdAt) {}

    public record ScheduleRow(String id,
                              String profileId,
                              String profileName,
                              int hh,
                              int mm,
                              int portionMs) {}

    public record ScheduleEventInput(int hh, int mm, int portionMs) {}

    public record CommandRow(String id,
                             String commandType,
                             String payloadJson,
                             Instant createdAt) {}

    public record FeedLogRow(String id,
                             Instant ts,
                             String type,
                             String message,
                             String metaJson) {}

    public record FeedLogInput(Instant ts,
                               String type,
                               String message,
                               String metaJson) {}

    public Optional<UserRow> findUserByEmail(String email) {
        String sql = "SELECT id, email, password_hash, created_at FROM users WHERE lower(email)=lower(?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, email);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw fail("findUserByEmail", e);
        }
    }

    public Optional<UserRow> findUserById(String userId) {
        String sql = "SELECT id, email, password_hash, created_at FROM users WHERE id=?::uuid";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, userId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw fail("findUserById", e);
        }
    }

    public String createUser(String email, String passwordHash) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO users(id, email, password_hash) VALUES (?::uuid, ?, ?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, id);
            st.setString(2, email);
            st.setString(3, passwordHash);
            st.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw fail("createUser", e);
        }
    }

    public String createSession(String userId, Instant expiresAt) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO auth_sessions(id, user_id, expires_at) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, id);
            st.setString(2, userId);
            st.setTimestamp(3, Timestamp.from(expiresAt));
            st.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw fail("createSession", e);
        }
    }

    public Optional<SessionRow> findValidSession(String sessionId) {
        String sql = "SELECT id, user_id, expires_at FROM auth_sessions WHERE id=?::uuid AND expires_at > NOW()";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, sessionId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SessionRow(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getObject("user_id", UUID.class).toString(),
                    rs.getTimestamp("expires_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw fail("findValidSession", e);
        }
    }

    public void deleteSession(String sessionId) {
        String sql = "DELETE FROM auth_sessions WHERE id=?::uuid";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, sessionId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("deleteSession", e);
        }
    }

    public Optional<String> findUserIdByEmail(String email) {
        String sql = "SELECT id FROM users WHERE lower(email)=lower(?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, email);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getObject("id", UUID.class).toString());
            }
        } catch (SQLException e) {
            throw fail("findUserIdByEmail", e);
        }
    }

    public boolean hasDevice(String deviceId) {
        String sql = "SELECT 1 FROM devices WHERE id=?";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw fail("hasDevice", e);
        }
    }

    public void createDevice(String ownerUserId,
                             String deviceId,
                             String name,
                             String secretHash,
                             byte[] encryptedSecret) {
        String sql = """
            INSERT INTO devices(id, owner_user_id, name, secret_hash, encrypted_secret)
            VALUES (?, ?::uuid, ?, ?, ?)
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            st.setString(2, ownerUserId);
            st.setString(3, name);
            st.setString(4, secretHash);
            st.setBytes(5, encryptedSecret);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("createDevice", e);
        }
    }

    public Optional<DeviceRow> findDeviceById(String deviceId) {
        String sql = """
            SELECT id, owner_user_id, name, secret_hash, encrypted_secret, last_seen_at,
                   last_status_json::text AS status_json, firmware_version, active_profile_id, created_at
            FROM devices
            WHERE id=?
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDevice(rs));
            }
        } catch (SQLException e) {
            throw fail("findDeviceById", e);
        }
    }

    public Optional<DeviceRow> findDeviceByIdAndUser(String deviceId, String userId) {
        String sql = """
            SELECT id, owner_user_id, name, secret_hash, encrypted_secret, last_seen_at,
                   last_status_json::text AS status_json, firmware_version, active_profile_id, created_at
            FROM devices
            WHERE id=? AND owner_user_id=?::uuid
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            st.setString(2, userId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDevice(rs));
            }
        } catch (SQLException e) {
            throw fail("findDeviceByIdAndUser", e);
        }
    }

    public void updateDeviceStatus(String deviceId, Instant seenAt, String statusJson, String firmwareVersion) {
        String sql = """
            UPDATE devices
            SET last_seen_at=?,
                last_status_json=?::jsonb,
                firmware_version=?
            WHERE id=?
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setTimestamp(1, Timestamp.from(seenAt));
            st.setString(2, statusJson);
            st.setString(3, firmwareVersion);
            st.setString(4, deviceId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("updateDeviceStatus", e);
        }
    }

    public void rotateDeviceSecret(String deviceId, String secretHash, byte[] encryptedSecret) {
        String sql = "UPDATE devices SET secret_hash=?, encrypted_secret=? WHERE id=?";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, secretHash);
            st.setBytes(2, encryptedSecret);
            st.setString(3, deviceId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("rotateDeviceSecret", e);
        }
    }

    public List<DeviceListRow> listDevicesByUser(String userId) {
        String sql = """
            SELECT d.id,
                   d.name,
                   d.last_seen_at,
                   (d.last_status_json->>'rssi')::int AS rssi,
                   d.firmware_version,
                   p.name AS active_profile_name
            FROM devices d
            LEFT JOIN profiles p ON p.id = d.active_profile_id
            WHERE d.owner_user_id=?::uuid
            ORDER BY d.created_at DESC
            """;
        List<DeviceListRow> rows = new ArrayList<>();
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, userId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Timestamp seen = rs.getTimestamp("last_seen_at");
                    rows.add(new DeviceListRow(
                        rs.getString("id"),
                        rs.getString("name"),
                        seen == null ? null : seen.toInstant(),
                        (Integer) rs.getObject("rssi"),
                        rs.getString("firmware_version"),
                        rs.getString("active_profile_name")
                    ));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw fail("listDevicesByUser", e);
        }
    }

    public List<ProfileRow> listProfilesByDevice(String deviceId) {
        String sql = "SELECT id, device_id, name, default_portion_ms, created_at FROM profiles WHERE device_id=? ORDER BY created_at ASC";
        List<ProfileRow> rows = new ArrayList<>();
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ProfileRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("device_id"),
                        rs.getString("name"),
                        rs.getInt("default_portion_ms"),
                        rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw fail("listProfilesByDevice", e);
        }
    }

    public Optional<ProfileRow> findProfileByName(String deviceId, String profileName) {
        String sql = "SELECT id, device_id, name, default_portion_ms, created_at FROM profiles WHERE device_id=? AND name=?";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            st.setString(2, profileName);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ProfileRow(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getString("device_id"),
                    rs.getString("name"),
                    rs.getInt("default_portion_ms"),
                    rs.getTimestamp("created_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw fail("findProfileByName", e);
        }
    }

    public Optional<ProfileRow> findProfileByIdAndUser(String profileId, String userId) {
        String sql = """
            SELECT p.id, p.device_id, p.name, p.default_portion_ms, p.created_at
            FROM profiles p
            JOIN devices d ON d.id = p.device_id
            WHERE p.id=?::uuid AND d.owner_user_id=?::uuid
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, profileId);
            st.setString(2, userId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ProfileRow(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getString("device_id"),
                    rs.getString("name"),
                    rs.getInt("default_portion_ms"),
                    rs.getTimestamp("created_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw fail("findProfileByIdAndUser", e);
        }
    }

    public ProfileRow createProfile(String deviceId, String name, int defaultPortionMs) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO profiles(id, device_id, name, default_portion_ms) VALUES (?::uuid, ?, ?, ?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, id);
            st.setString(2, deviceId);
            st.setString(3, name);
            st.setInt(4, defaultPortionMs);
            st.executeUpdate();
            return findProfileByName(deviceId, name).orElseThrow();
        } catch (SQLException e) {
            throw fail("createProfile", e);
        }
    }

    public void updateProfile(String profileId, String userId, String name, Integer defaultPortionMs) {
        String sql = """
            UPDATE profiles p
            SET name = COALESCE(?, p.name),
                default_portion_ms = COALESCE(?, p.default_portion_ms)
            FROM devices d
            WHERE p.id=?::uuid AND d.id = p.device_id AND d.owner_user_id=?::uuid
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, name);
            if (defaultPortionMs == null) {
                st.setNull(2, java.sql.Types.INTEGER);
            } else {
                st.setInt(2, defaultPortionMs);
            }
            st.setString(3, profileId);
            st.setString(4, userId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("updateProfile", e);
        }
    }

    public void deleteProfile(String profileId, String userId) {
        String sql = """
            DELETE FROM profiles p
            USING devices d
            WHERE p.id=?::uuid AND d.id = p.device_id AND d.owner_user_id=?::uuid
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, profileId);
            st.setString(2, userId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("deleteProfile", e);
        }
    }

    public List<ScheduleRow> listScheduleByDevice(String deviceId) {
        String sql = """
            SELECT se.id, se.profile_id, p.name AS profile_name, se.hh, se.mm, se.portion_ms
            FROM schedule_events se
            JOIN profiles p ON p.id = se.profile_id
            WHERE p.device_id = ?
            ORDER BY p.name, se.hh, se.mm
            """;
        return listScheduleBySql(sql, deviceId);
    }

    public List<ScheduleRow> listScheduleByProfile(String profileId) {
        String sql = """
            SELECT se.id, se.profile_id, p.name AS profile_name, se.hh, se.mm, se.portion_ms
            FROM schedule_events se
            JOIN profiles p ON p.id = se.profile_id
            WHERE se.profile_id = ?::uuid
            ORDER BY se.hh, se.mm
            """;
        return listScheduleBySql(sql, profileId);
    }

    private List<ScheduleRow> listScheduleBySql(String sql, String idParam) {
        List<ScheduleRow> rows = new ArrayList<>();
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, idParam);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ScheduleRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getObject("profile_id", UUID.class).toString(),
                        rs.getString("profile_name"),
                        rs.getInt("hh"),
                        rs.getInt("mm"),
                        rs.getInt("portion_ms")
                    ));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw fail("listScheduleBySql", e);
        }
    }

    public void replaceSchedule(String profileId, List<ScheduleEventInput> events) {
        String deleteSql = "DELETE FROM schedule_events WHERE profile_id=?::uuid";
        String insertSql = "INSERT INTO schedule_events(id, profile_id, hh, mm, portion_ms) VALUES (?::uuid, ?::uuid, ?, ?, ?)";
        try (Connection connection = dbClient.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                del.setString(1, profileId);
                del.executeUpdate();
            }

            try (PreparedStatement ins = connection.prepareStatement(insertSql)) {
                for (ScheduleEventInput e : events) {
                    ins.setString(1, UUID.randomUUID().toString());
                    ins.setString(2, profileId);
                    ins.setInt(3, e.hh());
                    ins.setInt(4, e.mm());
                    ins.setInt(5, e.portionMs());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            throw fail("replaceSchedule", e);
        }
    }

    public void setActiveProfile(String deviceId, String profileId) {
        String sql = "UPDATE devices SET active_profile_id=?::uuid WHERE id=?";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, profileId);
            st.setString(2, deviceId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("setActiveProfile", e);
        }
    }

    public Optional<String> getActiveProfileName(String deviceId) {
        String sql = """
            SELECT p.name
            FROM devices d
            LEFT JOIN profiles p ON p.id = d.active_profile_id
            WHERE d.id=?
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, deviceId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw fail("getActiveProfileName", e);
        }
    }

    public String enqueueCommand(String deviceId, String commandType, String payloadJson) {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO command_queue(id, device_id, command_type, payload_json, status)
            VALUES (?::uuid, ?, ?, ?::jsonb, 'PENDING')
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, id);
            st.setString(2, deviceId);
            st.setString(3, commandType);
            st.setString(4, payloadJson);
            st.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw fail("enqueueCommand", e);
        }
    }

    public void ackCommands(String deviceId, List<String> ackIds) {
        if (ackIds == null || ackIds.isEmpty()) {
            return;
        }

        String sql = """
            UPDATE command_queue
            SET status='ACKED', acked_at=NOW()
            WHERE device_id=? AND id=?::uuid
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            for (String id : ackIds) {
                st.setString(1, deviceId);
                st.setString(2, id);
                st.addBatch();
            }
            st.executeBatch();
        } catch (SQLException e) {
            throw fail("ackCommands", e);
        }
    }

    public List<CommandRow> fetchPendingAndMarkSent(String deviceId, int limit) {
        String selectSql = """
            SELECT id, command_type, payload_json::text AS payload_json, created_at
            FROM command_queue
            WHERE device_id=? AND status='PENDING'
            ORDER BY created_at ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;
        String updateSql = "UPDATE command_queue SET status='SENT', sent_at=NOW() WHERE id=?::uuid";

        List<CommandRow> rows = new ArrayList<>();
        try (Connection connection = dbClient.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                select.setString(1, deviceId);
                select.setInt(2, limit);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new CommandRow(
                            rs.getObject("id", UUID.class).toString(),
                            rs.getString("command_type"),
                            rs.getString("payload_json"),
                            rs.getTimestamp("created_at").toInstant()
                        ));
                    }
                }
            }

            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (CommandRow row : rows) {
                    update.setString(1, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }

            connection.commit();
            return rows;
        } catch (SQLException e) {
            throw fail("fetchPendingAndMarkSent", e);
        }
    }

    public void insertFeedLogs(String deviceId, List<FeedLogInput> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO feed_logs(id, device_id, ts, type, message, meta_json)
            VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb)
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            for (FeedLogInput log : logs) {
                st.setString(1, UUID.randomUUID().toString());
                st.setString(2, deviceId);
                st.setTimestamp(3, Timestamp.from(log.ts()));
                st.setString(4, log.type());
                st.setString(5, log.message());
                st.setString(6, log.metaJson());
                st.addBatch();
            }
            st.executeBatch();
        } catch (SQLException e) {
            throw fail("insertFeedLogs", e);
        }
    }

    public void insertFeedLog(String deviceId, Instant ts, String type, String message, String metaJson) {
        String sql = """
            INSERT INTO feed_logs(id, device_id, ts, type, message, meta_json)
            VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb)
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, UUID.randomUUID().toString());
            st.setString(2, deviceId);
            st.setTimestamp(3, Timestamp.from(ts));
            st.setString(4, type);
            st.setString(5, message);
            st.setString(6, metaJson);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("insertFeedLog", e);
        }
    }

    public List<FeedLogRow> listFeedLogs(String deviceId, String typeFilter, String query, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, ts, type, message, meta_json::text AS meta_json
            FROM feed_logs
            WHERE device_id=?
            """);

        List<Object> params = new ArrayList<>();
        params.add(deviceId);

        if (typeFilter != null && !typeFilter.isBlank()) {
            sql.append(" AND type=? ");
            params.add(typeFilter);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND message ILIKE ? ");
            params.add("%" + query + "%");
        }

        sql.append(" ORDER BY ts DESC LIMIT ? OFFSET ? ");
        params.add(limit);
        params.add(offset);

        List<FeedLogRow> rows = new ArrayList<>();
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer v) {
                    st.setInt(i + 1, v);
                } else {
                    st.setString(i + 1, String.valueOf(p));
                }
            }

            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    rows.add(new FeedLogRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getTimestamp("ts").toInstant(),
                        rs.getString("type"),
                        rs.getString("message"),
                        rs.getString("meta_json")
                    ));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw fail("listFeedLogs", e);
        }
    }

    public boolean registerNonce(String deviceId, String nonce, long tsEpoch) {
        String sql = "INSERT INTO device_nonce(id, device_id, nonce, ts_epoch) VALUES (?::uuid, ?, ?, ?)";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, UUID.randomUUID().toString());
            st.setString(2, deviceId);
            st.setString(3, nonce);
            st.setLong(4, tsEpoch);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return false;
            }
            throw fail("registerNonce", e);
        }
    }

    public void purgeOldNonce(long minEpochExclusive) {
        String sql = "DELETE FROM device_nonce WHERE ts_epoch < ?";
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setLong(1, minEpochExclusive);
            st.executeUpdate();
        } catch (SQLException e) {
            throw fail("purgeOldNonce", e);
        }
    }

    private UserRow mapUser(ResultSet rs) throws SQLException {
        return new UserRow(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private DeviceRow mapDevice(ResultSet rs) throws SQLException {
        Timestamp seen = rs.getTimestamp("last_seen_at");
        UUID activeProfile = rs.getObject("active_profile_id", UUID.class);

        return new DeviceRow(
            rs.getString("id"),
            rs.getObject("owner_user_id", UUID.class).toString(),
            rs.getString("name"),
            rs.getString("secret_hash"),
            rs.getBytes("encrypted_secret"),
            seen == null ? null : seen.toInstant(),
            rs.getString("status_json"),
            rs.getString("firmware_version"),
            activeProfile == null ? null : activeProfile.toString(),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private RuntimeException fail(String op, SQLException e) {
        logger.error("DB operation {} failed", op, e);
        return new IllegalStateException("Database error", e);
    }
}
