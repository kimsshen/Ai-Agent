package com.example.industrialai.model;

import java.time.Instant;
import java.util.List;

public record PushLogStatistics(
        String channel,
        Instant from,
        Instant to,
        long totalCount,
        List<PushLogChannelStatistics> channels) {
}
