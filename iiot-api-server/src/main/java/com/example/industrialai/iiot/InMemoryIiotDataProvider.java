package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.AlarmSeverity;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceStatus;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Temporary bootstrap provider. Replace this bean with production adapters. */
@Component
@ConditionalOnProperty(name = "iiot.provider.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryIiotDataProvider implements IiotDataProvider {

    private final Instant timestamp = Instant.now();
    private final Map<String, Device> devices = Map.of(
            "DEV-001", new Device("DEV-001", "Demo motor", "MTR-X200", "Demo line A", DeviceStatus.WARNING, timestamp),
            "DEV-002", new Device("DEV-002", "Demo pump", "PUMP-P80", "Demo cooling station", DeviceStatus.RUNNING, timestamp));
    private final Map<String, Telemetry> telemetry = Map.of(
            "DEV-001", new Telemetry("DEV-001", timestamp, 86.4, 8.7, 1482, 31.6),
            "DEV-002", new Telemetry("DEV-002", timestamp, 43.1, 2.1, 2950, 12.4));
    private final List<Alarm> alarms = List.of(
            new Alarm("ALM-1001", "DEV-001", "MTR-TEMP-HIGH", AlarmSeverity.CRITICAL,
                    "Motor temperature is high", true, timestamp.minus(8, ChronoUnit.MINUTES)));
    private final List<WorkOrder> workOrders = List.of(
            new WorkOrder("WO-DEMO-001", "DEV-001", "OPEN", "Inspect motor vibration", "", timestamp));

    @Override
    public List<Device> findAllDevices() {
        return devices.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    @Override
    public Device findDevice(String deviceId) {
        Device device = devices.get(deviceId);
        if (device == null) {
            throw new DeviceNotFoundException(deviceId);
        }
        return device;
    }

    @Override
    public Telemetry findLatestTelemetry(String deviceId) {
        findDevice(deviceId);
        return telemetry.get(deviceId);
    }

    @Override
    public List<Telemetry> findTelemetryHistory(String deviceId, int limit) {
        Telemetry latest = findLatestTelemetry(deviceId);
        return IntStream.range(0, limit)
                .mapToObj(index -> new Telemetry(
                        deviceId,
                        latest.timestamp().minus(index * 5L, ChronoUnit.MINUTES),
                        round(latest.temperatureCelsius() - index * 0.35),
                        round(Math.max(1.0, latest.vibrationMmPerSecond() - index * 0.12)),
                        round(latest.speedRpm() + ((index % 3) - 1) * 3.0),
                        round(latest.currentAmpere() - index * 0.08)))
                .toList();
    }

    @Override
    public List<Alarm> findAlarms(String deviceId, boolean activeOnly) {
        findDevice(deviceId);
        return alarms.stream()
                .filter(alarm -> alarm.deviceId().equals(deviceId))
                .filter(alarm -> !activeOnly || alarm.active())
                .toList();
    }

    @Override
    public List<WorkOrder> findWorkOrders(String deviceId) {
        findDevice(deviceId);
        return workOrders.stream().filter(order -> order.deviceId().equals(deviceId)).toList();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
