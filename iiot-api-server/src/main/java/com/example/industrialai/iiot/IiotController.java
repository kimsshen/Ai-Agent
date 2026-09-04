package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceSnapshot;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/iiot")
@Validated
public class IiotController {

    private final IiotQueryService queryService;

    public IiotController(IiotQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/devices")
    public List<Device> devices() {
        return queryService.findAllDevices();
    }

    @GetMapping("/devices/{deviceId}")
    public Device device(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId) {
        return queryService.findDevice(deviceId);
    }

    @GetMapping("/devices/{deviceId}/snapshot")
    public DeviceSnapshot snapshot(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId) {
        return queryService.getSnapshot(deviceId);
    }

    @GetMapping("/devices/{deviceId}/telemetry/latest")
    public Telemetry latestTelemetry(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId) {
        return queryService.getLatestTelemetry(deviceId);
    }

    @GetMapping("/devices/{deviceId}/telemetry/history")
    public List<Telemetry> telemetryHistory(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int limit) {
        return queryService.getTelemetryHistory(deviceId, limit);
    }

    @GetMapping("/devices/{deviceId}/alarms")
    public List<Alarm> alarms(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return queryService.getAlarms(deviceId, activeOnly);
    }

    @GetMapping("/devices/{deviceId}/work-orders")
    public List<WorkOrder> workOrders(
            @PathVariable @Pattern(regexp = "DEV-\\d{3}") String deviceId) {
        return queryService.getWorkOrders(deviceId);
    }
}
