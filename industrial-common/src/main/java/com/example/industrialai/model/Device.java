package com.example.industrialai.model;

import java.time.Instant;

public record Device(
        String id,
        String name,
        String model,
        String location,
        DeviceStatus status,
        Instant updatedAt) {
}

