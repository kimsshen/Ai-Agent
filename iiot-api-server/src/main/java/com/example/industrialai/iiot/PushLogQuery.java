package com.example.industrialai.iiot;

import java.time.Instant;

/** Normalized, half-open time-window query for push_log. */
public record PushLogQuery(String channel, Instant from, Instant to) {
}
