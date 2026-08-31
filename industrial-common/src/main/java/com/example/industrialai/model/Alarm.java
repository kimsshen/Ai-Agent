package com.example.industrialai.model;

import java.time.Instant;

public record Alarm(
        String id,
        String deviceId,
        String code,
        AlarmSeverity severity,
        String message,
        boolean active,
        Instant occurredAt) {
}

