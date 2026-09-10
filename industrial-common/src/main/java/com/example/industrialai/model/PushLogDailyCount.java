package com.example.industrialai.model;

import java.time.LocalDate;

/** One calendar-day bucket from push_log. */
public record PushLogDailyCount(LocalDate date, long count) {
}
