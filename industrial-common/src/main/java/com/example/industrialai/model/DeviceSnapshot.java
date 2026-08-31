package com.example.industrialai.model;

import java.util.List;

public record DeviceSnapshot(
        Device device,
        Telemetry telemetry,
        List<Alarm> activeAlarms) {
}
