package com.example.industrialai.model;

import java.time.Instant;

/** One aggregated alarm type/content pair from push_log. */
public record PushLogAlarmItem(
        String exceptionName,
        String message,
        long count,
        Instant firstCreatedDate,
        Instant lastCreatedDate) {
}
