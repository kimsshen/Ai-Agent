package com.example.industrialai.model;

import java.util.List;

public record PushLogChannelStatistics(
        String channel,
        long totalCount,
        List<PushLogAlarmItem> alarms) {
}
