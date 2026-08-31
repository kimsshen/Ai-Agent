package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.AlarmSeverity;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceSnapshot;
import com.example.industrialai.model.DeviceStatus;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Repository
public class IiotDemoRepository {

    private final Instant baseTime = Instant.parse("2026-08-27T06:00:00Z");

    private final Map<String, Device> devices = Map.of(
            "DEV-001", new Device("DEV-001", "一号主轴电机", "MTR-X200", "一号车间/产线A", DeviceStatus.WARNING, baseTime),
            "DEV-002", new Device("DEV-002", "二号循环泵", "PUMP-P80", "一号车间/冷却站", DeviceStatus.RUNNING, baseTime),
            "DEV-003", new Device("DEV-003", "三号空压机", "COMP-C90", "动力站", DeviceStatus.WARNING, baseTime)
    );

    private final Map<String, Telemetry> latestTelemetry = Map.of(
            "DEV-001", new Telemetry("DEV-001", baseTime, 86.4, 8.7, 1482, 31.6),
            "DEV-002", new Telemetry("DEV-002", baseTime, 43.1, 2.1, 2950, 12.4),
            "DEV-003", new Telemetry("DEV-003", baseTime, 78.2, 7.4, 2975, 42.8)
    );

    private final List<Alarm> alarms = List.of(
            new Alarm("ALM-1001", "DEV-001", "MTR-TEMP-HIGH", AlarmSeverity.CRITICAL,
                    "主轴电机轴承温度超过 85°C", true, baseTime.minus(8, ChronoUnit.MINUTES)),
            new Alarm("ALM-1002", "DEV-001", "MTR-VIB-HIGH", AlarmSeverity.WARNING,
                    "主轴电机振动速度超过 7.1 mm/s", true, baseTime.minus(5, ChronoUnit.MINUTES)),
            new Alarm("ALM-3001", "DEV-003", "COMP-VIB-HIGH", AlarmSeverity.WARNING,
                    "空压机二级转子振动偏高", true, baseTime.minus(18, ChronoUnit.MINUTES)),
            new Alarm("ALM-0901", "DEV-001", "MTR-TEMP-HIGH", AlarmSeverity.WARNING,
                    "历史温度预警，已处理", false, baseTime.minus(30, ChronoUnit.DAYS))
    );

    private final List<WorkOrder> workOrders = List.of(
            new WorkOrder("WO-20260715-01", "DEV-001", "CLOSED", "主轴电机振动升高",
                    "清理冷却风道并重新润滑驱动端轴承，振动恢复至 3.2 mm/s。", baseTime.minus(43, ChronoUnit.DAYS)),
            new WorkOrder("WO-20260801-03", "DEV-002", "CLOSED", "循环泵出口压力波动",
                    "清理入口过滤器并排气。", baseTime.minus(26, ChronoUnit.DAYS)),
            new WorkOrder("WO-20260827-02", "DEV-003", "OPEN", "检查空压机振动偏高",
                    "待执行频谱分析和联轴器对中检查。", baseTime.minus(2, ChronoUnit.HOURS))
    );

    public List<Device> findAllDevices() {
        return devices.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    public Device findDevice(String deviceId) {
        Device device = devices.get(deviceId);
        if (device == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + deviceId);
        }
        return device;
    }

    public Telemetry findLatestTelemetry(String deviceId) {
        findDevice(deviceId);
        return latestTelemetry.get(deviceId);
    }

    public List<Telemetry> findTelemetryHistory(String deviceId, int limit) {
        Telemetry latest = findLatestTelemetry(deviceId);
        int safeLimit = Math.max(1, Math.min(limit, 60));
        return IntStream.range(0, safeLimit)
                .mapToObj(index -> new Telemetry(
                        deviceId,
                        latest.timestamp().minus(index * 5L, ChronoUnit.MINUTES),
                        round(latest.temperatureCelsius() - index * 0.35),
                        round(Math.max(1.0, latest.vibrationMmPerSecond() - index * 0.12)),
                        round(latest.speedRpm() + ((index % 3) - 1) * 3.0),
                        round(latest.currentAmpere() - index * 0.08)))
                .toList();
    }

    public List<Alarm> findAlarms(String deviceId, boolean activeOnly) {
        findDevice(deviceId);
        return alarms.stream()
                .filter(alarm -> alarm.deviceId().equals(deviceId))
                .filter(alarm -> !activeOnly || alarm.active())
                .toList();
    }

    public List<WorkOrder> findWorkOrders(String deviceId) {
        findDevice(deviceId);
        return workOrders.stream().filter(order -> order.deviceId().equals(deviceId)).toList();
    }

    public DeviceSnapshot snapshot(String deviceId) {
        return new DeviceSnapshot(findDevice(deviceId), findLatestTelemetry(deviceId), findAlarms(deviceId, true));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

