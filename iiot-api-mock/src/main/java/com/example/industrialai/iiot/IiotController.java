package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceSnapshot;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/iiot")
public class IiotController {

    private final IiotDemoRepository repository;

    public IiotController(IiotDemoRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/devices")
    public List<Device> devices() {
        return repository.findAllDevices();
    }

    @GetMapping("/devices/{deviceId}")
    public Device device(@PathVariable String deviceId) {
        return repository.findDevice(deviceId);
    }

    @GetMapping("/devices/{deviceId}/snapshot")
    public DeviceSnapshot snapshot(@PathVariable String deviceId) {
        return repository.snapshot(deviceId);
    }

    @GetMapping("/devices/{deviceId}/telemetry/latest")
    public Telemetry latestTelemetry(@PathVariable String deviceId) {
        return repository.findLatestTelemetry(deviceId);
    }

    @GetMapping("/devices/{deviceId}/telemetry/history")
    public List<Telemetry> telemetryHistory(@PathVariable String deviceId,
                                            @RequestParam(defaultValue = "12") int limit) {
        return repository.findTelemetryHistory(deviceId, limit);
    }

    @GetMapping("/devices/{deviceId}/alarms")
    public List<Alarm> alarms(@PathVariable String deviceId,
                              @RequestParam(defaultValue = "true") boolean activeOnly) {
        return repository.findAlarms(deviceId, activeOnly);
    }

    @GetMapping("/devices/{deviceId}/work-orders")
    public List<WorkOrder> workOrders(@PathVariable String deviceId) {
        return repository.findWorkOrders(deviceId);
    }
}

