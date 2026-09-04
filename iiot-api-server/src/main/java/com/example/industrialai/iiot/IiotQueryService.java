package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceSnapshot;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IiotQueryService {

    private final IiotDataProvider provider;

    public IiotQueryService(IiotDataProvider provider) {
        this.provider = provider;
    }

    public List<Device> findAllDevices() {
        return provider.findAllDevices();
    }

    public Device findDevice(String deviceId) {
        return provider.findDevice(normalizeDeviceId(deviceId));
    }

    public DeviceSnapshot getSnapshot(String deviceId) {
        String normalizedId = normalizeDeviceId(deviceId);
        Device device = provider.findDevice(normalizedId);
        Telemetry telemetry = provider.findLatestTelemetry(normalizedId);
        List<Alarm> activeAlarms = provider.findAlarms(normalizedId, true);
        return new DeviceSnapshot(device, telemetry, activeAlarms);
    }

    public Telemetry getLatestTelemetry(String deviceId) {
        return provider.findLatestTelemetry(normalizeDeviceId(deviceId));
    }

    public List<Telemetry> getTelemetryHistory(String deviceId, int limit) {
        return provider.findTelemetryHistory(normalizeDeviceId(deviceId), limit);
    }

    public List<Alarm> getAlarms(String deviceId, boolean activeOnly) {
        return provider.findAlarms(normalizeDeviceId(deviceId), activeOnly);
    }

    public List<WorkOrder> getWorkOrders(String deviceId) {
        return provider.findWorkOrders(normalizeDeviceId(deviceId));
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || !deviceId.matches("DEV-\\d{3}")) {
            throw new InvalidDeviceIdException(deviceId);
        }
        return deviceId.toUpperCase();
    }
}
