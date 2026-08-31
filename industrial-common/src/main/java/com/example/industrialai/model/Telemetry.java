package com.example.industrialai.model;

import java.time.Instant;

public record Telemetry(
        String deviceId,
        Instant timestamp,
        double temperatureCelsius,
        double vibrationMmPerSecond,
        double speedRpm,
        double currentAmpere) {
}

