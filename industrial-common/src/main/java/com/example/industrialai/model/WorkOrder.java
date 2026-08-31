package com.example.industrialai.model;

import java.time.Instant;

public record WorkOrder(
        String id,
        String deviceId,
        String status,
        String description,
        String resolution,
        Instant createdAt) {
}

