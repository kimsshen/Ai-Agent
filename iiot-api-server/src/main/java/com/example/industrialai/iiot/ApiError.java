package com.example.industrialai.iiot;

import java.time.Instant;

public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String traceId,
        Instant timestamp) {
}
