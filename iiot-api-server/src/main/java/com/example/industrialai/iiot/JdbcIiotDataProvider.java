package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.AlarmSeverity;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceStatus;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** PostgreSQL-backed read-only IIoT provider. */
@Component
@ConditionalOnProperty(name = "iiot.provider.type", havingValue = "jdbc")
public class JdbcIiotDataProvider implements IiotDataProvider {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIiotDataProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Device> findAllDevices() {
        return jdbcTemplate.query("""
                SELECT id, name, model, location, status, updated_at
                FROM devices
                ORDER BY id
                """, (rs, rowNum) -> mapDevice(rs));
    }

    @Override
    public Device findDevice(String deviceId) {
        List<Device> devices = jdbcTemplate.query("""
                SELECT id, name, model, location, status, updated_at
                FROM devices
                WHERE id = ?
                """, (rs, rowNum) -> mapDevice(rs), deviceId);
        if (devices.isEmpty()) {
            throw new DeviceNotFoundException(deviceId);
        }
        return devices.get(0);
    }

    @Override
    public Telemetry findLatestTelemetry(String deviceId) {
        findDevice(deviceId);
        List<Telemetry> telemetry = jdbcTemplate.query("""
                SELECT device_id, timestamp, temperature_celsius,
                       vibration_mm_per_second, speed_rpm, current_ampere
                FROM telemetry
                WHERE device_id = ?
                ORDER BY timestamp DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTelemetry(rs), deviceId);
        return telemetry.isEmpty() ? null : telemetry.get(0);
    }

    @Override
    public List<Telemetry> findTelemetryHistory(String deviceId, int limit) {
        findDevice(deviceId);
        int safeLimit = Math.max(1, Math.min(limit, 60));
        return jdbcTemplate.query("""
                SELECT device_id, timestamp, temperature_celsius,
                       vibration_mm_per_second, speed_rpm, current_ampere
                FROM telemetry
                WHERE device_id = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """, (rs, rowNum) -> mapTelemetry(rs), deviceId, safeLimit);
    }

    @Override
    public List<Alarm> findAlarms(String deviceId, boolean activeOnly) {
        findDevice(deviceId);
        String sql = """
                SELECT id, device_id, code, severity, message, active, occurred_at
                FROM alarms
                WHERE device_id = ?
                """ + (activeOnly ? " AND active = TRUE " : "") + """
                ORDER BY occurred_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapAlarm(rs), deviceId);
    }

    @Override
    public List<WorkOrder> findWorkOrders(String deviceId) {
        findDevice(deviceId);
        return jdbcTemplate.query("""
                SELECT id, device_id, status, description, resolution, created_at
                FROM work_orders
                WHERE device_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> mapWorkOrder(rs), deviceId);
    }

    private Device mapDevice(ResultSet rs) throws SQLException {
        return new Device(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("model"),
                rs.getString("location"),
                DeviceStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private Telemetry mapTelemetry(ResultSet rs) throws SQLException {
        return new Telemetry(
                rs.getString("device_id"),
                toInstant(rs.getTimestamp("timestamp")),
                rs.getDouble("temperature_celsius"),
                rs.getDouble("vibration_mm_per_second"),
                rs.getDouble("speed_rpm"),
                rs.getDouble("current_ampere"));
    }

    private Alarm mapAlarm(ResultSet rs) throws SQLException {
        return new Alarm(
                rs.getString("id"),
                rs.getString("device_id"),
                rs.getString("code"),
                AlarmSeverity.valueOf(rs.getString("severity").toUpperCase(Locale.ROOT)),
                rs.getString("message"),
                rs.getBoolean("active"),
                toInstant(rs.getTimestamp("occurred_at")));
    }

    private WorkOrder mapWorkOrder(ResultSet rs) throws SQLException {
        return new WorkOrder(
                rs.getString("id"),
                rs.getString("device_id"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getString("resolution"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
